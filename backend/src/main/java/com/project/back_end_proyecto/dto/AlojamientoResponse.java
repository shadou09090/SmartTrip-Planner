package com.project.back_end_proyecto.dto;
import java.math.BigDecimal;
public record AlojamientoResponse(Long id, String nombre, String direccion, BigDecimal latitud, BigDecimal longitud) { }
