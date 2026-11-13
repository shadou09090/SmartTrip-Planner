package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.CrearViajeRequest;
import com.project.back_end_proyecto.dto.ViajeResponse;
import com.project.back_end_proyecto.service.ViajeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/viajes")
public class ViajeController {

    private final ViajeService viajeService;

    public ViajeController(ViajeService viajeService) {
        this.viajeService = viajeService;
    }

    @PostMapping
    public ResponseEntity<ViajeResponse> crear(@Valid @RequestBody CrearViajeRequest request) {
        ViajeResponse creado = viajeService.crear(request);
        return ResponseEntity.created(URI.create("/api/viajes/" + creado.id())).body(creado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ViajeResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(viajeService.obtener(id));
    }
}
