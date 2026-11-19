package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.FlightPlaceSuggestion;
import com.project.back_end_proyecto.service.flights.FlightSearchProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=local-no-db",
        "smarttrip.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "smarttrip.local-auth.bootstrap-token=local-test-bootstrap-token-0123456789abcdef"
})
@AutoConfigureMockMvc
class LocalDevAuthControllerTest {
    private static final String BOOTSTRAP = "local-test-bootstrap-token-0123456789abcdef";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean
    private FlightSearchProvider provider;

    @Test
    void bootstrapCredentialIssuesShortLivedScopedJwtAcceptedByDuffelEndpoint() throws Exception {
        when(provider.suggestPlaces("Bogota"))
                .thenReturn(List.of(new FlightPlaceSuggestion("Bogota", "BOG", "city", "CO")));

        MvcResult tokenResult = mockMvc.perform(post("/api/local/dev-auth/token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"bootstrapToken\":\"" + BOOTSTRAP + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.accessToken");
        var jwt = jwtDecoder.decode(accessToken);
        assertEquals("local-duffel-developer", jwt.getSubject());
        assertEquals("https://smarttrip.local/issuer/dev", jwt.getIssuer().toString());
        assertTrue(jwt.getAudience().contains("smarttrip-local-duffel"));
        assertEquals("duffel:search", jwt.getClaimAsString("scope"));
        assertNull(jwt.getClaim("roles"));
        assertNull(jwt.getClaim("authorities"));
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
        assertTrue(jwt.getExpiresAt().isBefore(Instant.now().plusSeconds(601)));

        mockMvc.perform(get("/api/local/duffel/places").param("query", "Bogota")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void invalidBootstrapCredentialCannotIssueToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/local/dev-auth/token").contentType(APPLICATION_JSON)
                        .content("{\"bootstrapToken\":\"wrong-bootstrap-token-value-0123456789\"}"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(BOOTSTRAP));
    }

    @Test
    void tokenIssuerRejectsNonLoopbackClients() throws Exception {
        mockMvc.perform(post("/api/local/dev-auth/token").with(request -> {
                    request.setRemoteAddr("192.0.2.10");
                    return request;
                }).contentType(APPLICATION_JSON)
                        .content("{\"bootstrapToken\":\"" + BOOTSTRAP + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredJwtIsRejectedByLocalDuffelEndpoints() throws Exception {
        Instant now = Instant.now();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                JwtClaimsSet.builder().issuer("https://smarttrip.local/issuer/dev").subject("local-duffel-developer")
                        .audience(List.of("smarttrip-local-duffel"))
                        .issuedAt(now.minusSeconds(900)).expiresAt(now.minusSeconds(1))
                        .claim("scope", "duffel:search").build())).getTokenValue();

        mockMvc.perform(get("/api/local/duffel/places").param("query", "Bogota")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedJwtWithoutDuffelScopeIsDenied() throws Exception {
        mockMvc.perform(get("/api/local/duffel/places").param("query", "Bogota")
                        .with(jwt().authorities()))
                .andExpect(status().isForbidden());
    }
}
