package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.InteresResponse;
import com.project.back_end_proyecto.repository.InteresRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("!local-no-db")
public class InteresController {
    private final InteresRepository interesRepository;
    public InteresController(InteresRepository interesRepository) { this.interesRepository = interesRepository; }

    @GetMapping("/api/intereses")
    public List<InteresResponse> list() {
        return interesRepository.findAllByOrderByNombreAsc().stream()
                .map(interes -> new InteresResponse(interes.getId(), interes.getNombre())).toList();
    }
}
