package com.project.back_end_proyecto.vuelos;

import com.project.back_end_proyecto.dto.BusquedaVuelos;
import com.project.back_end_proyecto.dto.VueloDto;

import java.util.List;

// patron strategy: cada api de vuelos implementa esta interfaz
public interface ProveedorVuelos {

    List<VueloDto> buscar(BusquedaVuelos busqueda);
}
