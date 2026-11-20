package com.project.back_end_proyecto.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;

public record HotelPriceSearchResponse(String provider, String destination, LocalDate checkIn,
                                       LocalDate checkOut, int guests, int rooms, String currency,
                                       Instant retrievedAt, Instant expiresAt, JsonNode results) { }
