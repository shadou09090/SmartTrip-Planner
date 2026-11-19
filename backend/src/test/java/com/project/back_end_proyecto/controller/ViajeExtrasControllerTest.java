package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.service.ViajeExtrasService;
import com.project.back_end_proyecto.service.flights.FlightSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViajeExtrasControllerTest {

    @Test
    void getLodgingReturnsNoContentForAuthorizedTripWithoutLodging() {
        ViajeExtrasService service = mock(ViajeExtrasService.class);
        when(service.alojamiento(9L, 5L)).thenReturn(Optional.empty());
        ViajeExtrasController controller = new ViajeExtrasController(service, mock(FlightSearchService.class));
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("9").build();

        var response = controller.getLodging(jwt, 5L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void lodgingAndFlightGetRoutesMatchFrontendRequests() throws NoSuchMethodException {
        var lodging = ViajeExtrasController.class.getMethod("getLodging", Jwt.class, Long.class);
        var flights = ViajeExtrasController.class.getMethod("getFlights", Jwt.class, Long.class);

        assertEquals("/api/viajes/{viajeId}/alojamiento", lodging.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/api/viajes/{viajeId}/vuelos", flights.getAnnotation(GetMapping.class).value()[0]);
    }
}
