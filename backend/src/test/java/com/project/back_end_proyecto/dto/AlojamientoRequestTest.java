package com.project.back_end_proyecto.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AlojamientoRequestTest {
    @Test
    void roundsProviderCoordinatePrecisionToTheOracleColumnScale() {
        var request = new AlojamientoRequest("Hotel", null,
                new BigDecimal("10.12345678"), new BigDecimal("-74.123456789"));

        assertEquals(new BigDecimal("10.1234568"), request.latitud());
        assertEquals(new BigDecimal("-74.1234568"), request.longitud());
        assertEquals(7, request.latitud().scale());
        assertEquals(7, request.longitud().scale());
    }

    @Test
    void keepsNullableCoordinatesAndAcceptsTheOraclePrecisionBoundaries() {
        var request = new AlojamientoRequest("Hotel", null, null, new BigDecimal("-180"));
        assertNull(request.latitud());
        assertEquals(new BigDecimal("-180.0000000"), request.longitud());
    }

    @Test
    void rejectsCoordinatesOutsideGeographicRangesBeforeRounding() {
        assertThrows(IllegalArgumentException.class, () -> new AlojamientoRequest("Hotel", null,
                new BigDecimal("90.00000001"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AlojamientoRequest("Hotel", null,
                BigDecimal.ZERO, new BigDecimal("180.000001")));
    }
}
