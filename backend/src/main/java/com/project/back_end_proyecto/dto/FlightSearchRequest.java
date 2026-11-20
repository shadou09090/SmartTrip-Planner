package com.project.back_end_proyecto.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.FutureOrPresent;

import java.time.LocalDate;

public record FlightSearchRequest(
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}") String origin,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}") String destination,
        @NotNull @FutureOrPresent LocalDate departureDate,
        @FutureOrPresent LocalDate returnDate,
        @NotNull @Min(1) @Max(9) Integer passengers,
        @Min(1) @Max(9) Integer adults,
        @Min(0) @Max(8) Integer children,
        @Min(0) @Max(8) Integer infants,
        String cabinClass
) {
    public FlightSearchRequest(String origin, String destination, LocalDate departureDate,
                               LocalDate returnDate, Integer passengers, String cabinClass) {
        this(origin, destination, departureDate, returnDate, passengers, null, null, null, cabinClass);
    }

    public int effectiveAdults() { return adults == null ? passengers : adults; }
    public int effectiveChildren() { return children == null ? 0 : children; }
    public int effectiveInfants() { return infants == null ? 0 : infants; }

    @AssertTrue(message = "La suma de adultos, niños e infantes debe coincidir con el total de pasajeros")
    public boolean isPassengerCompositionValid() {
        if (passengers == null) return true;
        boolean compositionProvided = adults != null || children != null || infants != null;
        if (!compositionProvided) return passengers >= 1 && passengers <= 9;
        int adultCount = effectiveAdults();
        int childCount = effectiveChildren();
        int infantCount = effectiveInfants();
        return adultCount >= 1 && childCount >= 0 && infantCount >= 0
                && adultCount + childCount + infantCount == passengers
                && infantCount <= adultCount && passengers <= 9;
    }

    @AssertTrue(message = "El regreso debe ser igual o posterior a la salida")
    public boolean isReturnDateValid() {
        return returnDate == null || departureDate == null || !returnDate.isBefore(departureDate);
    }

    @AssertTrue(message = "El origen y el destino deben ser diferentes")
    public boolean isRouteValid() {
        return origin == null || destination == null || !origin.equalsIgnoreCase(destination);
    }
}
