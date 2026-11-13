package com.project.back_end_proyecto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CrearViajeRequest {

    @NotBlank(message = "El destino es obligatorio")
    private String destino;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate fechaFin;

    @NotNull(message = "El presupuesto es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El presupuesto debe ser mayor a 0")
    private BigDecimal presupuesto;

    @NotEmpty(message = "Selecciona al menos un interés")
    private List<@NotBlank(message = "Los intereses no pueden estar vacíos") String> intereses;
}
