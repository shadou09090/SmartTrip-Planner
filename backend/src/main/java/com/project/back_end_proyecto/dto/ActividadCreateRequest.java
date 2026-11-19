package com.project.back_end_proyecto.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ActividadCreateRequest(
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 500) String descripcion,
        @Size(max = 250) String ubicacion,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitud,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitud,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal costo,
        LocalDate fechaDisponibleInicio,
        LocalDate fechaDisponibleFin,
        @NotNull @Positive Integer orden,
        @Size(max = 30) List<@NotBlank @Size(max = 100) String> intereses
) {
    @AssertTrue(message = "La fecha final de disponibilidad debe ser igual o posterior a la inicial")
    public boolean isRangoDisponibilidadValido() {
        return fechaDisponibleInicio == null || fechaDisponibleFin == null || !fechaDisponibleFin.isBefore(fechaDisponibleInicio);
    }
}
