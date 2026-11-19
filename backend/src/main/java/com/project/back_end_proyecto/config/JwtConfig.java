package com.project.back_end_proyecto.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@Profile("oracle")
public class JwtConfig {

    @Bean
    SecretKey jwtSecretKey(@Value("${smarttrip.jwt.secret}") String base64Secret) {
        if (!StringUtils.hasText(base64Secret)) {
            throw new IllegalStateException("SMARTTRIP_JWT_SECRET must be configured for the oracle profile.");
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
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("smarttrip-planner"));
        return decoder;
    }
}
