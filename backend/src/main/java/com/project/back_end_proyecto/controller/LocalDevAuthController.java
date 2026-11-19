package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.LocalDevTokenRequest;
import com.project.back_end_proyecto.dto.LocalDevTokenResponse;
import com.project.back_end_proyecto.config.LocalDevAuthConfiguredCondition;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@Profile("local-no-db & !oracle & !prod & !production")
@Conditional(LocalDevAuthConfiguredCondition.class)
public class LocalDevAuthController {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_FAILED_ATTEMPTS_PER_MINUTE = 5;

    private final JwtEncoder jwtEncoder;
    private final byte[] configuredBootstrapToken;
    private long failureWindowStartedAt = System.currentTimeMillis();
    private int failedAttempts;

    public LocalDevAuthController(JwtEncoder jwtEncoder,
                                  @Value("${smarttrip.local-auth.bootstrap-token}") String configuredBootstrapToken) {
        if (!StringUtils.hasText(configuredBootstrapToken) || configuredBootstrapToken.length() < 32) {
            throw new IllegalStateException("SMARTTRIP_LOCAL_AUTH_TOKEN must contain at least 32 characters.");
        }
        this.jwtEncoder = jwtEncoder;
        this.configuredBootstrapToken = configuredBootstrapToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/api/local/dev-auth/token")
    public LocalDevTokenResponse issueToken(@Valid @RequestBody LocalDevTokenRequest request,
                                            HttpServletRequest servletRequest) {
        if (!isLoopback(servletRequest.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este emisor solo está disponible desde el equipo local.");
        }
        synchronized (this) {
            resetFailureWindowIfElapsed();
            if (failedAttempts >= MAX_FAILED_ATTEMPTS_PER_MINUTE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Se alcanzó el límite temporal de intentos de autenticación local.");
            }
            byte[] suppliedToken = request.bootstrapToken().getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(configuredBootstrapToken, suppliedToken)) {
                failedAttempts++;
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial local inválida.");
            }
            failedAttempts = 0;
        }

        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://smarttrip.local/issuer/dev")
                .subject("local-duffel-developer")
                .audience(List.of("smarttrip-local-duffel"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(TOKEN_LIFETIME))
                .claim("scope", "duffel:search")
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new LocalDevTokenResponse(accessToken, "Bearer", TOKEN_LIFETIME.toSeconds());
    }

    private synchronized void resetFailureWindowIfElapsed() {
        long now = System.currentTimeMillis();
        if (now - failureWindowStartedAt >= 60_000) {
            failureWindowStartedAt = now;
            failedAttempts = 0;
        }
    }

    private static boolean isLoopback(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }
}
