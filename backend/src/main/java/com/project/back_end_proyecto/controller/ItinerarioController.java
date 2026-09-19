package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.service.ItinerarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/itinerario")
@CrossOrigin(origins = "http://localhost:5173")
public class ItinerarioController {

    @Autowired
    private ItinerarioService itinerarioService;

    @PostMapping("/generar")
    public ResponseEntity<Viaje> generarDias(@RequestBody Map<String, String> fechas) {
        try {
            LocalDate inicio = LocalDate.parse(fechas.get("fechaInicio"));
            LocalDate fin = LocalDate.parse(fechas.get("fechaFin"));

            Viaje viajeGuardado = itinerarioService.crearItinerarioVacio(inicio, fin);

            return ResponseEntity.ok(viajeGuardado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}