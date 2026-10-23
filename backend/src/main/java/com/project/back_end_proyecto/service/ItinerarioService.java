package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.DiaItinerario;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.repository.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ItinerarioService {

    @Autowired
    private ViajeRepository viajeRepository;

    public Viaje crearItinerarioVacio(LocalDate fechaInicio, LocalDate fechaFin) {
        Viaje nuevoViaje = new Viaje(fechaInicio, fechaFin);
        List<DiaItinerario> diasEstructurados = new ArrayList<>();

        long cantidadDias = ChronoUnit.DAYS.between(fechaInicio, fechaFin);

        for (int i = 0; i <= cantidadDias; i++) {
            LocalDate fechaDelDia = fechaInicio.plusDays(i);
            diasEstructurados.add(new DiaItinerario(fechaDelDia));
        }

        nuevoViaje.setDias(diasEstructurados);
        return viajeRepository.save(nuevoViaje);
    }

    public DiaItinerario agregarActividadADia(Long viajeId, Long diaId, String actividad) {
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(() -> new RuntimeException("Viaje no encontrado"));
        for (DiaItinerario dia : viaje.getDias()) {
            if (dia.getId().equals(diaId)) {
                dia.agregarActividad(actividad);
                viajeRepository.save(viaje);
                return dia;
            }
        }
        throw new RuntimeException("Día no encontrado en el viaje");
    }
}