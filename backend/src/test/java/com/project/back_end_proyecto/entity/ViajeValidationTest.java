package com.project.back_end_proyecto.entity;

import com.project.back_end_proyecto.service.ItinerarioService;
import com.project.back_end_proyecto.repository.DiaItinerarioRepository;
import com.project.back_end_proyecto.repository.ItinerarioRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ViajeValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEndDateBeforeStartDate() {
        Viaje viaje = viaje(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 9), BigDecimal.ZERO);

        assertTrue(validator.validate(viaje).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("rangoFechasValido")));
    }

    @Test
    void acceptsNullOrNonNegativeBudgetAndRejectsNegativeBudget() {
        assertTrue(validator.validate(viaje(
                LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 10), null
        )).isEmpty());
        assertTrue(validator.validate(viaje(
                LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 10), new BigDecimal("0.00")
        )).isEmpty());
        assertTrue(validator.validate(viaje(
                LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 10), new BigDecimal("-0.01")
        )).stream().anyMatch(violation -> violation.getPropertyPath().toString().equals("presupuesto")));
        assertTrue(validator.validate(viaje(
                LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 10), new BigDecimal("1.001")
        )).stream().anyMatch(violation -> violation.getPropertyPath().toString().equals("presupuesto")));
        assertTrue(validator.validate(viaje(
                LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 10), new BigDecimal("10000000000.00")
        )).stream().anyMatch(violation -> violation.getPropertyPath().toString().equals("presupuesto")));
    }

    @Test
    void existingItineraryRequestDoesNotPersistAnIncompleteTrip() {
        ItinerarioService service = new ItinerarioService(
                org.mockito.Mockito.mock(ItinerarioRepository.class),
                org.mockito.Mockito.mock(DiaItinerarioRepository.class),
                org.mockito.Mockito.mock(ViajeRepository.class),
                org.mockito.Mockito.mock(com.project.back_end_proyecto.repository.ActividadRepository.class),
                org.mockito.Mockito.mock(com.project.back_end_proyecto.repository.ActividadInteresRepository.class),
                org.mockito.Mockito.mock(com.project.back_end_proyecto.repository.ItinerarioActividadRepository.class),
                org.mockito.Mockito.mock(com.project.back_end_proyecto.repository.InteresRepository.class),
                org.mockito.Mockito.mock(com.project.back_end_proyecto.repository.ViajeInteresRepository.class),
                org.mockito.Mockito.mock(com.project.back_end_proyecto.service.TripPermissionService.class),
                org.mockito.Mockito.mock(com.project.back_end_proyecto.service.ActivityRecommendationCatalogService.class),
                org.mockito.Mockito.mock(com.project.back_end_proyecto.service.ExternalActivityRecommendationService.class)
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.crearItinerarioVacio(
                        LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 10)
                )
        );

        assertTrue(error.getMessage().contains("asociarse a un viaje existente"));
    }

    private Viaje viaje(LocalDate inicio, LocalDate fin, BigDecimal presupuesto) {
        User usuario = new User("Test User", "test@example.com", "bcrypt-hash");
        return new Viaje(usuario, "Bogotá", "Cali", inicio, fin, presupuesto);
    }
}
