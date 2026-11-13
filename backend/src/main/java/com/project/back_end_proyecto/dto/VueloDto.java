package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VueloDto(
        String tramo,
        String aerolinea,
        String numeroVuelo,
        String origen,
        String destino,
        LocalDateTime salida,
        LocalDateTime llegada,
        BigDecimal precio,
        String moneda,
        String fuente) {
}
