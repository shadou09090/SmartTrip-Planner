package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.BusquedaVuelos;
import com.project.back_end_proyecto.dto.VueloDto;
import com.project.back_end_proyecto.exception.DatosInvalidosException;
import com.project.back_end_proyecto.vuelos.ProveedorVuelos;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class VueloService {

    private static final int MAX_PASAJEROS = 9;

    private final ProveedorVuelos proveedorVuelos;

    public VueloService(ProveedorVuelos proveedorVuelos) {
        this.proveedorVuelos = proveedorVuelos;
    }

    public List<VueloDto> buscar(String origen, String destino, LocalDate fechaIda,
                                 LocalDate fechaVuelta, Integer pasajeros) {
        String codigoOrigen = validarCodigo(origen, "origen");
        String codigoDestino = validarCodigo(destino, "destino");

        if (codigoOrigen.equals(codigoDestino)) {
            throw new DatosInvalidosException("El origen y el destino no pueden ser iguales");
        }
        if (fechaIda == null) {
            throw new DatosInvalidosException("La fecha de ida es obligatoria");
        }
        if (fechaIda.isBefore(LocalDate.now())) {
            throw new DatosInvalidosException("La fecha de ida no puede ser pasada");
        }
        if (fechaVuelta != null && fechaVuelta.isBefore(fechaIda)) {
            throw new DatosInvalidosException("La fecha de vuelta no puede ser anterior a la de ida");
        }

        int cantidad = (pasajeros == null) ? 1 : pasajeros;
        if (cantidad < 1 || cantidad > MAX_PASAJEROS) {
            throw new DatosInvalidosException("Los pasajeros deben estar entre 1 y " + MAX_PASAJEROS);
        }

        BusquedaVuelos busqueda = new BusquedaVuelos(codigoOrigen, codigoDestino, fechaIda, fechaVuelta, cantidad);

        // ida primero y luego vuelta, del mas barato al mas caro
        return proveedorVuelos.buscar(busqueda).stream()
                .sorted(Comparator.comparing(VueloDto::tramo).thenComparing(VueloDto::precio))
                .toList();
    }

    private String validarCodigo(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new DatosInvalidosException("El " + campo + " es obligatorio");
        }
        String codigo = valor.trim().toUpperCase();
        if (!codigo.matches("[A-Z]{3}")) {
            throw new DatosInvalidosException("El " + campo + " debe ser un código IATA de 3 letras (ej: BOG)");
        }
        return codigo;
    }
}
