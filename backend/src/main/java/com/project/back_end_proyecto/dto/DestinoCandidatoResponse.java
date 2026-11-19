package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;

public record DestinoCandidatoResponse(String id, String nombreCompleto, String ciudad, String region,
                                       String pais, String tipo, BigDecimal latitud, BigDecimal longitud,
                                       String proveedor) { }
