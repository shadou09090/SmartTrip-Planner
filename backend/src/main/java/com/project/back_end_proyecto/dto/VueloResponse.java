package com.project.back_end_proyecto.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record VueloResponse(Long id, String origen, String destino, LocalDateTime fechaSalida,
                            LocalDateTime fechaLlegada, String aerolinea, String numeroVuelo, BigDecimal precio) { }
