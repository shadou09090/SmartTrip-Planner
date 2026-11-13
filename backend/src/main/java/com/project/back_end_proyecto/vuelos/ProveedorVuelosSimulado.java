package com.project.back_end_proyecto.vuelos;

import com.project.back_end_proyecto.dto.BusquedaVuelos;
import com.project.back_end_proyecto.dto.VueloDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

// datos de prueba mientras no se conecte una api real
// la misma busqueda siempre devuelve los mismos vuelos
@Component
public class ProveedorVuelosSimulado implements ProveedorVuelos {

    private static final String[] AEROLINEAS = {"Avianca", "LATAM", "Wingo", "Copa Airlines"};
    private static final String[] CODIGOS = {"AV", "LA", "P5", "CM"};
    private static final int OPCIONES_POR_TRAMO = 3;

    @Override
    public List<VueloDto> buscar(BusquedaVuelos busqueda) {
        List<VueloDto> vuelos = new ArrayList<>(generar("ida", busqueda.origen(), busqueda.destino(),
                busqueda.fechaIda(), busqueda.pasajeros()));

        if (busqueda.fechaVuelta() != null) {
            vuelos.addAll(generar("vuelta", busqueda.destino(), busqueda.origen(),
                    busqueda.fechaVuelta(), busqueda.pasajeros()));
        }
        return vuelos;
    }

    private List<VueloDto> generar(String tramo, String origen, String destino, LocalDate fecha, int pasajeros) {
        Random azar = new Random(Objects.hash(origen, destino, fecha.toEpochDay()));
        int primeraAerolinea = azar.nextInt(AEROLINEAS.length);

        List<VueloDto> vuelos = new ArrayList<>();
        for (int i = 0; i < OPCIONES_POR_TRAMO; i++) {
            int aerolinea = (primeraAerolinea + i) % AEROLINEAS.length;

            LocalDateTime salida = LocalDateTime.of(fecha,
                    LocalTime.of(6 + i * 5 + azar.nextInt(2), azar.nextInt(4) * 15));
            LocalDateTime llegada = salida.plusMinutes(55 + azar.nextInt(90));

            long precioPorPersona = (180_000 + azar.nextInt(420_000)) / 1_000 * 1_000;
            BigDecimal precio = BigDecimal.valueOf(precioPorPersona).multiply(BigDecimal.valueOf(pasajeros));

            vuelos.add(new VueloDto(
                    tramo,
                    AEROLINEAS[aerolinea],
                    CODIGOS[aerolinea] + (100 + azar.nextInt(900)),
                    origen,
                    destino,
                    salida,
                    llegada,
                    precio,
                    "COP",
                    "simulado"));
        }
        return vuelos;
    }
}
