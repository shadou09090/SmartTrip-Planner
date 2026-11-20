package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.service.publicplaces.OpenStreetMapParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenStreetMapPlaceParserTest {
    private final OpenStreetMapParser parser = new OpenStreetMapParser();

    @Test
    void extractsOnlyFieldsPresentInSimulatedOverpassResponse() {
        Map<String, Object> element = Map.of(
                "type", "node", "id", 123,
                "lat", 4.60, "lon", -74.07,
                "tags", Map.of("name", "Museo de Prueba", "tourism", "museum",
                        "description", "Exhibición local", "addr:street", "Calle 1", "addr:city", "Bogotá")
        );

        var places = parser.parsearOverpass(Map.of("elements", List.of(element)));

        assertEquals(1, places.size());
        assertEquals("Museo de Prueba", places.get(0).nombre());
        assertEquals("museum", places.get(0).categoria());
        assertEquals("Calle 1, Bogotá", places.get(0).ubicacion());
        assertEquals(new BigDecimal("4.6"), places.get(0).latitud());
        assertTrue(places.get(0).urlFuente().endsWith("/node/123"));
        assertFalse(places.get(0).etiquetas().containsKey("price"));
    }

    @Test
    void ignoresElementsWithoutNameOrCoordinatesAndParsesGeocoding() {
        var places = parser.parsearOverpass(Map.of("elements", List.of(
                Map.of("type", "node", "id", 1, "lat", 4.0, "lon", -74.0, "tags", Map.of("amenity", "cafe"))
        )));
        var point = parser.parsearGeocodificacion(List.of(Map.of("lat", "4.65", "lon", "-74.1")));

        assertTrue(places.isEmpty());
        assertEquals(new BigDecimal("4.65"), point.latitude());
        assertEquals(new BigDecimal("-74.1"), point.longitude());
        assertNull(parser.parsearGeocodificacion(List.of()));
    }
}
