package com.project.back_end_proyecto.service.flights;

import com.project.back_end_proyecto.dto.FlightSearchRequest;
import com.project.back_end_proyecto.dto.FlightSearchResponse;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.service.TripPermissionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class FlightSearchServiceTest {
    @Test
    void rejectsSearchWhenUserCannotReadTrip() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        DuffelSearchService duffelSearch = mock(DuffelSearchService.class);
        when(permissions.requireRead(7L, 12L)).thenThrow(new ResourceNotFoundException());
        FlightSearchService service = new FlightSearchService(permissions, duffelSearch);

        assertThrows(ResourceNotFoundException.class, () -> service.search(7L, 12L,
                new FlightSearchRequest("BOG", "MDE", LocalDate.now().plusDays(30), null, 1, "economy")));
        verifyNoInteractions(duffelSearch);
    }

    @Test
    void requiresEditPermissionAndReturnsCurrentPersistableCopOffer() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        DuffelSearchService duffelSearch = mock(DuffelSearchService.class);
        var segment = new FlightSearchResponse.Segment("Bogotá (BOG)", "Medellín (MDE)",
                "Air", "Air", "123", "2030-02-01T08:00:00Z", "2030-02-01T09:00:00Z");
        var slice = new FlightSearchResponse.Slice("Bogotá (BOG)", "Medellín (MDE)", "Air", "123",
                segment.departureAt(), segment.arrivalAt(), "1 h", 0, null, List.of(segment));
        var offer = new FlightSearchResponse.Offer("off_verified", new BigDecimal("1250.00"), "COP",
                OffsetDateTime.now().plusMinutes(10), List.of(slice));
        when(duffelSearch.retrieveOffer("off_verified")).thenReturn(offer);
        FlightSearchService service = new FlightSearchService(permissions, duffelSearch);

        assertEquals(offer, service.verifyOfferForPersistence(7L, 12L, "off_verified"));

        verify(permissions).requireEdit(7L, 12L);
        verify(duffelSearch).retrieveOffer("off_verified");
    }

    @Test
    void refusesForeignCurrencyAndMultiSegmentOffersThatOracleCannotRepresent() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        DuffelSearchService duffelSearch = mock(DuffelSearchService.class);
        var first = new FlightSearchResponse.Segment("Bogotá (BOG)", "Panamá (PTY)", "Air", "Air", "1",
                "2030-02-01T08:00:00Z", "2030-02-01T09:00:00Z");
        var second = new FlightSearchResponse.Segment("Panamá (PTY)", "Medellín (MDE)", "Air", "Air", "2",
                "2030-02-01T10:00:00Z", "2030-02-01T11:00:00Z");
        var direct = new FlightSearchResponse.Slice("Bogotá (BOG)", "Medellín (MDE)", "Air", "1",
                first.departureAt(), first.arrivalAt(), "1 h", 0, null, List.of(first));
        var connection = new FlightSearchResponse.Slice("Bogotá (BOG)", "Medellín (MDE)", "Air", "1",
                first.departureAt(), second.arrivalAt(), "3 h", 1, null, List.of(first, second));
        var usd = new FlightSearchResponse.Offer("off_usd", new BigDecimal("100.00"), "USD",
                OffsetDateTime.now().plusMinutes(10), List.of(direct));
        var multiSegment = new FlightSearchResponse.Offer("off_connection", new BigDecimal("100.00"), "COP",
                OffsetDateTime.now().plusMinutes(10), List.of(connection));
        when(duffelSearch.retrieveOffer("off_usd")).thenReturn(usd);
        when(duffelSearch.retrieveOffer("off_connection")).thenReturn(multiSegment);
        FlightSearchService service = new FlightSearchService(permissions, duffelSearch);

        ResponseStatusException currencyException = assertThrows(ResponseStatusException.class,
                () -> service.verifyOfferForPersistence(7L, 12L, "off_usd"));
        ResponseStatusException segmentsException = assertThrows(ResponseStatusException.class,
                () -> service.verifyOfferForPersistence(7L, 12L, "off_connection"));

        assertEquals(422, currencyException.getStatusCode().value());
        assertEquals(422, segmentsException.getStatusCode().value());
    }

    @Test
    void doesNotAskDuffelToVerifyWhenUserCannotEditTrip() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        DuffelSearchService duffelSearch = mock(DuffelSearchService.class);
        when(permissions.requireEdit(7L, 12L)).thenThrow(new ResourceNotFoundException());
        FlightSearchService service = new FlightSearchService(permissions, duffelSearch);

        assertThrows(ResourceNotFoundException.class,
                () -> service.verifyOfferForPersistence(7L, 12L, "off_verified"));
        verifyNoInteractions(duffelSearch);
    }
}
