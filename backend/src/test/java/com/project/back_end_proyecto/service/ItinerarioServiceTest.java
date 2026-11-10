package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.TrasladoRequest;
import com.project.back_end_proyecto.dto.TrasladoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItinerarioServiceTest {

    private ItinerarioService itinerarioService;

    @BeforeEach
    void setUp() {
        itinerarioService = new ItinerarioService();
    }

    @Test
    @DisplayName("Criterio 1: Debe calcular tiempos a pie, transporte público y en auto entre dos ubicaciones válidas")
    void testCalcularTrasladoTresModos() {
        // Museo del Oro a Plaza de Bolívar en Bogotá
        TrasladoRequest request = new TrasladoRequest(
                "Museo del Oro", 4.6019, -74.0720,
                "Plaza de Bolívar", 4.5981, -74.0758
        );

        TrasladoResponse response = itinerarioService.calcularTraslado(request);

        assertNotNull(response);
        assertEquals("Museo del Oro", response.getOrigen());
        assertEquals("Plaza de Bolívar", response.getDestino());
        assertTrue(response.getDistanciaKm() > 0);
        assertTrue(response.getTiempoPieMinutos() > 0);
        assertTrue(response.getTiempoTransporteMinutos() > 0);
        assertTrue(response.getTiempoAutoMinutos() > 0);
        assertNotNull(response.getTiempoPieFormato());
        assertNotNull(response.getTiempoTransporteFormato());
        assertNotNull(response.getTiempoAutoFormato());
        assertFalse(response.isDesdeCache());
    }

    @Test
    @DisplayName("Criterio 2: Debe activar advertencia visual si el tiempo estimado supera los 120 minutos")
    void testAdvertenciaSupera120Minutos() {
        // Bogotá a Zipaquirá (~50 km) - a pie toma más de 600 minutos (> 120 min)
        TrasladoRequest request = new TrasladoRequest(
                "Plaza de Bolívar Bogotá", 4.5981, -74.0758,
                "Catedral de Sal Zipaquirá", 5.0192, -74.0044
        );

        TrasladoResponse response = itinerarioService.calcularTraslado(request);

        assertNotNull(response);
        assertTrue(response.getTiempoPieMinutos() > 120);
        assertTrue(response.isPieSupera120());
        assertTrue(response.isAdvertenciaVisualSupera120(), "Debe activar la advertencia visual general si algún modo supera 120 minutos");
    }

    @Test
    @DisplayName("Criterio 3: Debe retornar desde caché sin demora perceptible cuando se consultan los mismos dos puntos")
    void testRecuperacionDesdeCache() {
        TrasladoRequest request = new TrasladoRequest(
                "Punto A", 4.6057, -74.0555,
                "Punto B", 4.6019, -74.0720
        );

        // Primera llamada
        TrasladoResponse r1 = itinerarioService.calcularTraslado(request);
        assertFalse(r1.isDesdeCache());

        // Segunda llamada con las mismas coordenadas
        TrasladoResponse r2 = itinerarioService.calcularTraslado(request);
        assertTrue(r2.isDesdeCache(), "La segunda consulta debe indicar que proviene de la caché");
        assertEquals(r1.getDistanciaKm(), r2.getDistanciaKm());
        assertEquals(r1.getTiempoPieMinutos(), r2.getTiempoPieMinutos());
    }
}
