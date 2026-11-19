package com.project.back_end_proyecto.service;

import tools.jackson.databind.ObjectMapper;
import com.project.back_end_proyecto.entity.Viaje;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OBOHotelPriceServiceTest {
    @Test
    void requiresReadPermissionAndRequestsDatedReadOnlyHotelPrices() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        Viaje trip = mock(Viaje.class);
        when(permissions.requireRead(4L, 8L)).thenReturn(trip);
        when(trip.getDestino()).thenReturn("Paris, France");
        when(trip.getFechaInicio()).thenReturn(LocalDate.of(2026, 12, 1));
        when(trip.getFechaFin()).thenReturn(LocalDate.of(2026, 12, 5));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://onebookingonline.com/mcp"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("MCP-Protocol-Version", "2025-06-18"))
                .andExpect(content().json("""
                    {"jsonrpc":"2.0","method":"tools/call","params":{"name":"search_hotels","arguments":{
                    "destination":"Paris, France","check_in":"2026-12-01","check_out":"2026-12-05","adults":2,"rooms":1,"currency":"EUR","sort":"recommended","limit":10}}}
                    """, false))
                .andRespond(withSuccess("""
                    {"jsonrpc":"2.0","id":1,"result":{"structuredContent":{"hotels":[{"hotel_name":"Hotel Example","total_price":{"amount":500,"currency":"EUR"}}]}}}
                    """, MediaType.APPLICATION_JSON));
        OBOHotelPriceService service = new OBOHotelPriceService(permissions, builder, new ObjectMapper());

        var response = service.search(4L, 8L, 2, 1, "eur");

        assertEquals("OBO Travel", response.provider());
        assertEquals("Paris, France", response.destination());
        assertEquals(LocalDate.of(2026, 12, 1), response.checkIn());
        assertEquals(LocalDate.of(2026, 12, 5), response.checkOut());
        assertEquals("EUR", response.currency());
        assertEquals("Hotel Example", response.results().path("hotels").get(0).path("hotel_name").asText());
        assertEquals(1800, response.expiresAt().getEpochSecond() - response.retrievedAt().getEpochSecond());
        verify(permissions).requireRead(4L, 8L);
        server.verify();
    }

    @Test
    void doesNotCallProviderForInvalidDatesOrOccupancy() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        Viaje trip = mock(Viaje.class);
        when(permissions.requireRead(4L, 8L)).thenReturn(trip);
        when(trip.getFechaInicio()).thenReturn(LocalDate.of(2026, 12, 5));
        when(trip.getFechaFin()).thenReturn(LocalDate.of(2026, 12, 1));
        var service = new OBOHotelPriceService(permissions, RestClient.builder(), new ObjectMapper());
        assertThrows(ResponseStatusException.class, () -> service.search(4L, 8L, 2, 1, "COP"));
        when(trip.getFechaFin()).thenReturn(LocalDate.of(2026, 12, 7));
        assertThrows(ResponseStatusException.class, () -> service.search(4L, 8L, 9, 1, "COP"));
    }
}
