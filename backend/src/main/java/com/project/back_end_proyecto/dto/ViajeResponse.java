package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ViajeResponse(
        Long id,
        String origen,
        String destino,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        BigDecimal presupuesto,
        List<String> intereses,
        String rolAcceso
) {
    public ViajeResponse(Long id, String origen, String destino, LocalDate fechaInicio, LocalDate fechaFin, BigDecimal presupuesto) {
        this(id, origen, destino, fechaInicio, fechaFin, presupuesto, List.of(), "OWNER");
    }

    public ViajeResponse(Long id, String destino, LocalDate fechaInicio, LocalDate fechaFin, BigDecimal presupuesto, List<String> intereses) {
        this(id, null, destino, fechaInicio, fechaFin, presupuesto, intereses, "OWNER");
    }
}
