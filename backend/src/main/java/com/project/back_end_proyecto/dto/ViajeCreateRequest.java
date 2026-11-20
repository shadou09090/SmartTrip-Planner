package com.project.back_end_proyecto.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ViajeCreateRequest(
        @NotBlank @Size(max = 150) String origen,
        @NotBlank @Size(max = 150) String destino,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal presupuesto,
        @Size(max = 30) List<@NotBlank @Size(max = 100) String> intereses
) {
    public ViajeCreateRequest(String origen, String destino, LocalDate fechaInicio, LocalDate fechaFin, BigDecimal presupuesto) {
        this(origen, destino, fechaInicio, fechaFin, presupuesto, null);
    }

    @AssertTrue(message = "La fecha de fin debe ser igual o posterior a la fecha de inicio")
    public boolean isRangoFechasValido() {
        return fechaInicio == null || fechaFin == null || !fechaFin.isBefore(fechaInicio);
    }
}
