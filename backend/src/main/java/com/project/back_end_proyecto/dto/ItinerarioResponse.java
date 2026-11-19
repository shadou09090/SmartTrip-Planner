package com.project.back_end_proyecto.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ItinerarioResponse(Long id, Long viajeId, LocalDateTime fechaCreacion, List<DiaResponse> dias, String mensaje) {
    public ItinerarioResponse(Long id, Long viajeId, LocalDateTime fechaCreacion, List<DiaResponse> dias) {
        this(id, viajeId, fechaCreacion, dias, null);
    }
    public record DiaResponse(Long id, LocalDate fecha, List<ActividadResponse> actividades) { }
}
