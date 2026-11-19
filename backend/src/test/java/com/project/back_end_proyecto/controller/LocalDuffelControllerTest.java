package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.FlightPlaceSuggestion;
import com.project.back_end_proyecto.dto.FlightSearchResponse;
import com.project.back_end_proyecto.service.flights.FlightSearchProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=local-no-db",
        "smarttrip.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
class LocalDuffelControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlightSearchProvider provider;

    @Test
    void localNoDbExposesProviderSuggestionsOnlyToAuthenticatedJwtRequests() throws Exception {
        when(provider.suggestPlaces("Bogota"))
                .thenReturn(List.of(new FlightPlaceSuggestion("Bogota", "BOG", "city", "CO")));

        mockMvc.perform(get("/api/local/duffel/places").param("query", "Bogota"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/local/duffel/places").param("query", "Bogota")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_duffel:search"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].iataCode").value("BOG"));
    }

    @Test
    void localNoDbStillDeniesOracleTripEndpoints() throws Exception {
        mockMvc.perform(get("/api/viajes/102/vuelos/lugares")
                        .param("query", "Bogota")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_duffel:search"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void localNoDbSearchEndpointRequiresJwtAndUsesProviderWithoutTripPersistence() throws Exception {
        when(provider.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FlightSearchResponse("Duffel", true, List.of(), "Mock provider response"));
        String request = "{\"origin\":\"BOG\",\"destination\":\"MDE\",\"departureDate\":\"2030-02-01\","
                + "\"returnDate\":null,\"passengers\":1,\"adults\":1,\"children\":0,\"infants\":0,\"cabinClass\":\"economy\"}";

        mockMvc.perform(post("/api/local/duffel/search").contentType("application/json").content(request))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/local/duffel/search")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_duffel:search")))
                        .contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
