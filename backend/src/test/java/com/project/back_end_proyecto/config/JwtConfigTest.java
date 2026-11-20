package com.project.back_end_proyecto.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtConfigTest {

    @Test
    void oracleProfileJwtEncoderAndDecoderRoundTripWithoutDatabase() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        String encodedTestKey = Base64.getEncoder().encodeToString(keyBytes);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("oracle");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test-jwt-properties", Map.of("smarttrip.jwt.secret", encodedTestKey)));
            context.register(JwtConfig.class);
            context.refresh();

            Instant now = Instant.now();
            var claims = JwtClaimsSet.builder().issuer("smarttrip-planner").subject("test-subject")
                    .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
            JwtEncoder encoder = context.getBean(JwtEncoder.class);
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            String inMemoryToken = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();

            assertEquals("test-subject", decoder.decode(inMemoryToken).getSubject());

            var localClaims = JwtClaimsSet.builder().issuer("https://smarttrip.local/issuer/dev").subject("local-duffel-developer")
                    .audience(java.util.List.of("smarttrip-local-duffel"))
                    .issuedAt(now).expiresAt(now.plusSeconds(60)).claim("scope", "duffel:search").build();
            String localToken = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), localClaims)).getTokenValue();
            org.junit.jupiter.api.Assertions.assertThrows(JwtValidationException.class, () -> decoder.decode(localToken));
        }
    }
}
