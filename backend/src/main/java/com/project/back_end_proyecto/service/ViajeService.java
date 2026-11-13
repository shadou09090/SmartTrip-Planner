package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.CrearViajeRequest;
import com.project.back_end_proyecto.dto.ViajeResponse;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.exception.DatosInvalidosException;
import com.project.back_end_proyecto.exception.RecursoNoEncontradoException;
import com.project.back_end_proyecto.repository.ViajeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ViajeService {

    private final ViajeRepository viajeRepository;

    public ViajeService(ViajeRepository viajeRepository) {
        this.viajeRepository = viajeRepository;
    }

    @Transactional
    public ViajeResponse crear(CrearViajeRequest request) {
        if (request.getFechaFin().isBefore(request.getFechaInicio())) {
            throw new DatosInvalidosException("La fecha de fin no puede ser anterior a la de inicio");
        }

        Viaje viaje = new Viaje(request.getFechaInicio(), request.getFechaFin());
        viaje.setDestino(request.getDestino().trim());
        viaje.setPresupuesto(request.getPresupuesto());

        List<String> intereses = new ArrayList<>();
        for (String interes : request.getIntereses()) {
            intereses.add(interes.trim());
        }
        viaje.setIntereses(intereses);

        return aRespuesta(viajeRepository.save(viaje));
    }

    @Transactional(readOnly = true)
    public ViajeResponse obtener(Long id) {
        Viaje viaje = viajeRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el viaje " + id));
        return aRespuesta(viaje);
    }

    private ViajeResponse aRespuesta(Viaje viaje) {
        return new ViajeResponse(
                viaje.getId(),
                viaje.getDestino(),
                viaje.getFechaInicio(),
                viaje.getFechaFin(),
                viaje.getPresupuesto(),
                new ArrayList<>(viaje.getIntereses()));
    }
}
