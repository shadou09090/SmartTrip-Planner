package com.project.back_end_proyecto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.repository.UserRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("oracle")
@EnabledIfSystemProperty(named = "smarttrip.oracle.integration", matches = "true")
class OraclePersistenceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ViajeRepository viajeRepository;

    @Test
    void registersLogsInPersistsTripsAndEnforcesOwnership() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        String ownerEmail = "oracle-test-" + unique + "@example.invalid";
        String otherEmail = "oracle-test-other-" + unique + "@example.invalid";
        String password = UUID.randomUUID() + "Aa9!";
        Long tripId = null;
        Long ownerId = null;
        Long otherId = null;

        try {
            register(ownerEmail, password);
            register(otherEmail, password);
            User owner = userRepository.findByEmailIgnoreCase(ownerEmail).orElseThrow();
            User other = userRepository.findByEmailIgnoreCase(otherEmail).orElseThrow();
            ownerId = owner.getId();
            otherId = other.getId();
            assertNotEquals(password, owner.getPassword());
            assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                    .matches(password, owner.getPassword()));

            String ownerToken = login(ownerEmail, password);
            String otherToken = login(otherEmail, password);

            String tripJson = mockMvc.perform(post("/api/viajes")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "origen", "Bogotá",
                                    "destino", "Cali",
                                    "fechaInicio", LocalDate.of(2026, 9, 10).toString(),
                                    "fechaFin", LocalDate.of(2026, 9, 15).toString(),
                                    "presupuesto", new BigDecimal("850000.00")
                            ))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.origen").value("Bogotá"))
                    .andReturn().getResponse().getContentAsString();
            JsonNode createdTrip = objectMapper.readTree(tripJson);
            tripId = createdTrip.get("id").asLong();

            assertTrue(viajeRepository.findByIdAndUsuario_Id(tripId, ownerId).isPresent());
            assertTrue(viajeRepository.findByIdAndUsuario_Id(tripId, otherId).isEmpty());

            String tripsJson = mockMvc.perform(get("/api/viajes").header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            boolean ownerTripIsListed = false;
            for (JsonNode listedTrip : objectMapper.readTree(tripsJson)) {
                ownerTripIsListed |= listedTrip.path("id").asLong() == tripId;
            }
            assertTrue(ownerTripIsListed);
            mockMvc.perform(get("/api/viajes/{id}", tripId).header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.destino").value("Cali"));
            mockMvc.perform(get("/api/viajes/{id}", tripId).header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/viajes"))
                    .andExpect(status().isUnauthorized());
        } finally {
            if (otherId == null) {
                otherId = userRepository.findByEmailIgnoreCase(otherEmail).map(User::getId).orElse(null);
            }
            if (ownerId == null) {
                ownerId = userRepository.findByEmailIgnoreCase(ownerEmail).map(User::getId).orElse(null);
            }
            if (tripId != null) {
                viajeRepository.deleteById(tripId);
            }
            if (otherId != null) viajeRepository.findAllByUsuario_IdOrderByFechaInicioDesc(otherId)
                    .forEach(trip -> viajeRepository.deleteById(trip.getId()));
            if (ownerId != null) viajeRepository.findAllByUsuario_IdOrderByFechaInicioDesc(ownerId)
                    .forEach(trip -> viajeRepository.deleteById(trip.getId()));
            if (otherId != null) userRepository.deleteById(otherId);
            if (ownerId != null) userRepository.deleteById(ownerId);
        }
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Oracle Integration Test",
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        String content = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("accessToken").asText();
    }
}
