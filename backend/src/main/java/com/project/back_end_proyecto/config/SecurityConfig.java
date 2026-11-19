package com.project.back_end_proyecto.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${smarttrip.cors.allowed-origins:${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}}") String configuredOrigins
    ) {
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    @Profile("oracle")
    SecurityFilterChain oracleFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/healthz", "/error", "/api/public-places/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {}))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
                );
        return http.build();
    }

    @Bean
    @Profile("!oracle & !local-no-db")
    SecurityFilterChain localFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> jwtDecoderProvider) throws Exception {
        JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/itinerario/**", "/api/viajes/**",
                                "/api/vuelos/**", "/healthz", "/error", "/api/public-places/**").permitAll()
                        .anyRequest().authenticated()
                );
        if (jwtDecoder != null) {
            http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.decoder(jwtDecoder)))
                    .exceptionHandling(errors -> errors
                            .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                            .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
                    );
        }
        return http.build();
    }

    @Bean
    @Profile("oracle")
    UserDetailsService oracleNoPasswordAuthenticationService() {
        return username -> { throw new UsernameNotFoundException("Use the account login endpoint."); };
    }

    @Bean
    @Profile("local-no-db & !oracle & !prod & !production")
    SecurityFilterChain localNoDatabaseFilterChain(HttpSecurity http,
                                                    ObjectProvider<JwtDecoder> jwtDecoderProvider) throws Exception {
        JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
        http.cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (jwtDecoder == null) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
        } else {
            http.csrf(csrf -> csrf.ignoringRequestMatchers(
                            "/api/local/duffel/**", "/api/local/dev-auth/token"))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/local/dev-auth/token").permitAll()
                            .requestMatchers("/api/local/duffel/**").hasAuthority("SCOPE_duffel:search")
                            .anyRequest().denyAll())
                    .oauth2ResourceServer(resourceServer -> resourceServer
                            .jwt(jwt -> jwt.decoder(jwtDecoder)))
                    .exceptionHandling(errors -> errors
                            .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                            .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
        }
        return http.build();
    }

    @Bean
    @Profile("local-no-db & !oracle & !prod & !production")
    UserDetailsService localNoDatabaseUserDetailsService() {
        return username -> { throw new UsernameNotFoundException("Authentication is disabled in local-no-db."); };
    }
}
