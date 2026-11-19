package com.project.back_end_proyecto.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record HistorialPrecioResponse(Long id, BigDecimal precio, LocalDateTime fechaRegistro) { }
