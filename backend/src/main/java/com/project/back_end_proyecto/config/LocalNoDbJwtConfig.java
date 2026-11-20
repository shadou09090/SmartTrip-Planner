package com.project.back_end_proyecto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
@Profile("local-no-db & !oracle & !prod & !production")
@Conditional(LocalJwtSecretConfiguredCondition.class)
class LocalNoDbJwtConfig {

    @Bean
    SecretKey localNoDbJwtSecretKey(@Value("${smarttrip.jwt.secret}") String base64Secret) {
        if (!StringUtils.hasText(base64Secret)) {
            throw new IllegalStateException("SMARTTRIP_JWT_SECRET must be configured to enable local Duffel endpoints.");
        }
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("SMARTTRIP_JWT_SECRET must be Base64 encoded.");
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("SMARTTRIP_JWT_SECRET must decode to at least 32 bytes.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder localNoDbJwtDecoder(SecretKey localNoDbJwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(localNoDbJwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator =
                new JwtClaimValidator<java.util.Collection<String>>("aud",
                        audience -> audience != null && audience.contains("smarttrip-local-duffel"));
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> scopeValidator =
                new JwtClaimValidator<String>("scope", "duffel:search"::equals);
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> lifetimeValidator = jwt -> {
            var issuedAt = jwt.getIssuedAt();
            var expiresAt = jwt.getExpiresAt();
            boolean validLifetime = issuedAt != null && expiresAt != null
                    && expiresAt.isAfter(issuedAt)
                    && java.time.Duration.between(issuedAt, expiresAt)
                    .compareTo(java.time.Duration.ofMinutes(10)) <= 0;
            return validLifetime ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://smarttrip.local/issuer/dev"),
                audienceValidator, scopeValidator, lifetimeValidator));
        return decoder;
    }

    @Bean
    JwtEncoder localNoDbJwtEncoder(SecretKey localNoDbJwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(localNoDbJwtSecretKey));
    }
}
