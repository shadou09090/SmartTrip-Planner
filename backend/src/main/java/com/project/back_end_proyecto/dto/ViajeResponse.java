package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ViajeResponse(
        Long id,
        String destino,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        BigDecimal presupuesto,
        List<String> intereses) {
}
