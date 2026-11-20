package com.project.back_end_proyecto.dto;

import java.time.LocalDate;

public record BusquedaVuelos(
        String origen,
        String destino,
        LocalDate fechaIda,
        LocalDate fechaVuelta,
        int pasajeros) {
}
