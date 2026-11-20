package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;

public record DestinoUbicacionResponse(String nombre, BigDecimal latitud, BigDecimal longitud,
                                       String fuenteCoordenadas, boolean coordenadasReutilizadas) {
    public DestinoUbicacionResponse(String nombre, BigDecimal latitud, BigDecimal longitud) {
        this(nombre, latitud, longitud, "proveedor público", false);
    }
}
