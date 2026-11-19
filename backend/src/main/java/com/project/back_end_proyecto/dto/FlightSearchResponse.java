package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record FlightSearchResponse(String provider, boolean testData, List<Offer> offers, String notice) {
    public record Offer(String id, BigDecimal totalAmount, String currency, OffsetDateTime expiresAt,
                        List<Slice> slices) { }
    public record Slice(String origin, String destination, String airline, String flightNumber,
                        String departureAt, String arrivalAt, String duration, int stops,
                        String baggageAllowance, List<Segment> segments) { }
    public record Segment(String origin, String destination, String operatingCarrier,
                          String marketingCarrier, String flightNumber,
                          String departureAt, String arrivalAt) { }
}
