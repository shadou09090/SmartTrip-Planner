package com.project.back_end_proyecto.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagePersistenceCorsTest {

    @Test
    void packagePutPreflightAllowsIdempotencyKeyFromLocalFrontend() throws Exception {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource("http://localhost:3000");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/viajes/361/paquete");
        request.addHeader("Origin", "http://localhost:3000");
        request.addHeader("Access-Control-Request-Method", "PUT");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type,idempotency-key");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertEquals("http://localhost:3000", configuration.checkOrigin("http://localhost:3000"));
        assertEquals(List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS),
                configuration.checkHttpMethod(HttpMethod.PUT));
        assertNotNull(configuration.checkHeaders(List.of("authorization", "content-type", "idempotency-key")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(new DefaultCorsProcessor().processRequest(configuration, request, response));
        assertEquals("http://localhost:3000", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("authorization, content-type, idempotency-key",
                response.getHeader("Access-Control-Allow-Headers"));
        assertEquals("GET,POST,PUT,DELETE,OPTIONS", response.getHeader("Access-Control-Allow-Methods"));
    }
}
