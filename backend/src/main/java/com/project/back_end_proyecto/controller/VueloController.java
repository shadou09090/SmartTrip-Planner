package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.VueloDto;
import com.project.back_end_proyecto.service.VueloService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/vuelos")
public class VueloController {

    private final VueloService vueloService;

    public VueloController(VueloService vueloService) {
        this.vueloService = vueloService;
    }

    // ejemplo: GET /api/vuelos?origen=BOG&destino=CTG&fechaIda=2026-12-01&fechaVuelta=2026-12-05&pasajeros=2
    @GetMapping
    public ResponseEntity<List<VueloDto>> buscar(
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) String destino,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaIda,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaVuelta,
            @RequestParam(required = false) Integer pasajeros) {
        return ResponseEntity.ok(vueloService.buscar(origen, destino, fechaIda, fechaVuelta, pasajeros));
    }
}
