package com.project.back_end_proyecto.dto.packagepersistence;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.project.back_end_proyecto.dto.packagepersistence.PaqueteGuardarRequest.*;
import static org.junit.jupiter.api.Assertions.*;

class PaqueteGuardarRequestTest {
    @Test
    void acceptsFiveDaysWithMixedPersistableActivityKindsAndStableOrder() {
        var days = List.of(
                day(1, activity("internal:10", TipoActividad.INTERNA, 10L, null, null, 1),
                        activity("manual:one", TipoActividad.MANUAL, null, null, null, 2)),
                day(2, activity("external:geo:abc", TipoActividad.EXTERNA, null, "Geoapify", "geo:abc", 1)),
                day(3, activity("internal:11", TipoActividad.INTERNA, 11L, null, null, 1)),
                day(4, activity("manual:two", TipoActividad.MANUAL, null, null, null, 1)),
                day(5, activity("external:osm:way1", TipoActividad.EXTERNA, null, "OpenStreetMap", "way/1", 1)));
        var request = new PaqueteGuardarRequest(3L, packageData(), days, null, null, List.of(), List.of());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }

    @Test
    void acceptsProviderCoordinatesWithMoreThanSevenFractionDigitsWithoutRounding() {
        var preciseActivity = new Actividad("external:geo:precise", TipoActividad.EXTERNA, null,
                "Geoapify", "place:precise", "Precise place", null, "Barcelona",
                new BigDecimal("41.387420012345678901"), new BigDecimal("2.169918765432109876"),
                1, null, null, unknownCost());
        var preciseLodging = new AlojamientoSeleccion(null, "Geoapify", "hotel:precise", "Hotel", "Barcelona",
                new BigDecimal("41.380000012345678901"), new BigDecimal("2.170000098765432109"),
                unknownCost(), 5, 1, 1);
        var request = new PaqueteGuardarRequest(0L, packageData(),
                List.of(new Dia(null, LocalDate.of(2026, 11, 20), List.of(preciseActivity))),
                null, preciseLodging, List.of(), List.of());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
        assertEquals(new BigDecimal("41.387420012345678901"), request.dias().get(0).actividades().get(0).latitud());
        assertEquals(new BigDecimal("2.170000098765432109"), request.alojamiento().longitud());
    }

    @Test
    void stillRejectsCoordinatesOutsideGeographicBounds() {
        var invalid = new Actividad("external:geo:invalid", TipoActividad.EXTERNA, null,
                "Geoapify", "place:invalid", "Invalid place", null, "Barcelona",
                new BigDecimal("90.0000000000001"), new BigDecimal("2.1"), 1, null, null, unknownCost());
        var request = new PaqueteGuardarRequest(0L, packageData(),
                List.of(new Dia(null, LocalDate.of(2026, 11, 20), List.of(invalid))),
                null, null, List.of(), List.of());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().contains("latitud")));
        }
    }

    @Test
    void rejectsDuplicateOrderInOneDayButAllowsSameOrderOnDifferentDays() {
        var request = new PaqueteGuardarRequest(0L, packageData(),
                List.of(day(1, activity("manual:a", TipoActividad.MANUAL, null, null, null, 1),
                                activity("manual:b", TipoActividad.MANUAL, null, null, null, 1)),
                        day(2, activity("manual:c", TipoActividad.MANUAL, null, null, null, 1))),
                null, null, List.of(), List.of());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(violation -> violation.getMessage().contains("orden")));
        }
    }

    @Test
    void identifiesEmptySelectedFlightSegmentsAsTheNotEmptyValidationFailure() {
        var flight = new VueloSeleccion(null, "DuffelSandbox", "offer-sandbox", "EUR", new BigDecimal("3216"),
                TipoCosto.SANDBOX, "BOG", "BCN", (String) null, (String) null, List.of());
        var request = new PaqueteGuardarRequest(0L, packageData(),
                List.of(day(1, activity("manual:valid", TipoActividad.MANUAL, null, null, null, 1))),
                flight, null, List.of(), List.of());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violation = factory.getValidator().validate(request).stream()
                    .filter(item -> item.getPropertyPath().toString().equals("vueloSeleccionado.tramos"))
                    .findFirst().orElseThrow();
            assertEquals(jakarta.validation.constraints.NotEmpty.class,
                    violation.getConstraintDescriptor().getAnnotation().annotationType());
        }
    }

    @Test
    void refusesUnknownCostsWithAnAmountAndExternalActivitiesWithoutIdentity() {
        Costo invalidCost = new Costo(new BigDecimal("50"), null, "person", BigDecimal.ONE,
                TipoCosto.DESCONOCIDO, null, null, null);
        Actividad invalidExternal = new Actividad("external:unknown", TipoActividad.EXTERNA, null,
                null, null, "Museum", null, null, null, null, 1, null, null, unknownCost());
        var request = new PaqueteGuardarRequest(0L, packageData(), List.of(new Dia(1L, LocalDate.of(2026, 11, 20),
                List.of(new Actividad("manual:valid", TipoActividad.MANUAL, null, null, null,
                        "Manual", null, null, null, null, 1, null, null, invalidCost), invalidExternal))),
                null, null, List.of(), List.of());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);
            assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("desconocido")));
            assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("actividad externa")));
        }
    }

    @Test
    void rejectsInvalidPackageCurrencyAndTravelerCount() {
        var packageData = new ViajePaquete(0, "COPX", "not-a-zone", Map.of());
        var request = new PaqueteGuardarRequest(0L, packageData,
                List.of(day(1, activity("manual:a", TipoActividad.MANUAL, null, null, null, 1))),
                null, null, List.of(), List.of());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().contains("numeroViajeros")));
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().contains("monedaPresupuesto")));
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().contains("zonaHoraria")));
        }
    }

    @Test
    void keepsASelectedFlightOfferAsAnOrderedMultiLegSelection() {
        var legs = List.of(
                new TramoVuelo("BOG", "MAD", LocalDateTime.parse("2026-11-19T20:00:00"),
                        LocalDateTime.parse("2026-11-20T12:00:00"), "Example Air", "EA101"),
                new TramoVuelo("MAD", "CDG", LocalDateTime.parse("2026-11-20T14:00:00"),
                        LocalDateTime.parse("2026-11-20T16:00:00"), "Example Air", "EA202"));
        var offer = new VueloSeleccion(null, "provider", "offer-123", "EUR", new BigDecimal("450.00"),
                TipoCosto.COTIZACION_REAL, "BOG", "CDG", legs.get(0).salida(), legs.get(1).llegada(), legs);
        var request = new PaqueteGuardarRequest(0L, packageData(),
                List.of(day(1, activity("manual:a", TipoActividad.MANUAL, null, null, null, 1))),
                offer, null, List.of(), List.of());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
        assertEquals(List.of("EA101", "EA202"), request.vueloSeleccionado().tramos().stream()
                .map(TramoVuelo::numeroVuelo).toList());
    }

    private static ViajePaquete packageData() {
        return new ViajePaquete(1, "COP", "Europe/Paris", Map.of("ritmo", "moderado"));
    }

    private static Dia day(int offset, Actividad... activities) {
        return new Dia((long) offset, LocalDate.of(2026, 11, 19).plusDays(offset), List.of(activities));
    }

    private static Actividad activity(String key, TipoActividad type, Long activityId,
                                       String source, String externalId, int order) {
        return new Actividad(key, type, activityId, source, externalId, "Attraction", null, "City",
                BigDecimal.ONE, BigDecimal.ONE, order, null, null, unknownCost());
    }

    private static Costo unknownCost() {
        return new Costo(null, null, null, null, TipoCosto.DESCONOCIDO, null, null, null);
    }
}
