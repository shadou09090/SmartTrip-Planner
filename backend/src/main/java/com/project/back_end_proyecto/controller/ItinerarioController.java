package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.TrasladoRequest;
import com.project.back_end_proyecto.dto.TrasladoResponse;
import com.project.back_end_proyecto.entity.DiaItinerario;
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

    @PostMapping("/{viajeId}/dia/{diaId}/actividad")
    public ResponseEntity<DiaItinerario> agregarActividad(
            @PathVariable Long viajeId,
            @PathVariable Long diaId,
            @RequestBody Map<String, String> body) {
        try {
            String actividad = body.get("actividad");
            DiaItinerario diaActualizado = itinerarioService.agregarActividadADia(viajeId, diaId, actividad);
            return ResponseEntity.ok(diaActualizado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * HU-010: Endpoint para calcular los tiempos estimados de traslado
     * entre dos actividades consecutivas (a pie, transporte público y automóvil).
     */
    @PostMapping("/calcular-traslado")
    public ResponseEntity<TrasladoResponse> calcularTraslado(@RequestBody TrasladoRequest request) {
        try {
            TrasladoResponse respuesta = itinerarioService.calcularTraslado(request);
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
