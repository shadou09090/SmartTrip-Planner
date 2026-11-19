package com.project.back_end_proyecto.controller;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {
    @Test
    void reportsUpOnlyWhenOracleQuerySucceeds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1 FROM DUAL", Integer.class)).thenReturn(1);

        var response = new HealthController(jdbc).readiness();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void returnsSafeUnavailableStatusWhenOracleIsDown() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1 FROM DUAL", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        var response = new HealthController(jdbc).readiness();

        assertEquals(503, response.getStatusCode().value());
        assertEquals("DOWN", response.getBody().get("status"));
    }
}
