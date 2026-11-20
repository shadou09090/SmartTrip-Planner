package com.project.back_end_proyecto.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("local-no-db")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class LocalNoDbSecurityConfigTest {
    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void localNoDbProfileKeepsCsrfFilterAndDenyAllChain() {
        var filters = securityFilterChain.getFilters("/api/auth/signup");
        assertTrue(filters.stream().anyMatch(CsrfFilter.class::isInstance));
    }

    @Test
    void localNoDbStillDeniesRequests() throws Exception {
        mockMvc.perform(get("/api/viajes")).andExpect(status().isForbidden());
    }
}
