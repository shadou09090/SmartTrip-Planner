package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.ItinerarioResponse;
import com.project.back_end_proyecto.dto.ActividadCreateRequest;
import com.project.back_end_proyecto.dto.ActividadResponse;
import com.project.back_end_proyecto.dto.ActividadRecomendacionResponse;
import com.project.back_end_proyecto.dto.AgregarActividadExistenteRequest;
import com.project.back_end_proyecto.dto.PresupuestoActividadesResponse;
import com.project.back_end_proyecto.dto.ReordenarActividadRequest;
import com.project.back_end_proyecto.dto.MoverActividadRequest;
import com.project.back_end_proyecto.service.ItinerarioService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@RestController
@RequestMapping("/api")
@Profile("oracle")
public class ItinerarioController {
    private final ItinerarioService itinerarioService;

    public ItinerarioController(ItinerarioService itinerarioService) {
        this.itinerarioService = itinerarioService;
    }

    @PostMapping("/viajes/{viajeId}/itinerario")
    public ItinerarioResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId) {
        return itinerarioService.crearParaViaje(Long.valueOf(jwt.getSubject()), viajeId);
    }

    @GetMapping("/viajes/{viajeId}/itinerario")
    public ResponseEntity<ItinerarioResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId) {
        return itinerarioService.consultarParaViaje(Long.valueOf(jwt.getSubject()), viajeId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/itinerario/generar")
    public ItinerarioResponse generateLegacy(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody GenerateRequest request) {
        return itinerarioService.generarParaViaje(Long.valueOf(jwt.getSubject()), request.viajeId());
    }

    @GetMapping("/actividades")
    public List<ActividadResponse> activities() {
        return itinerarioService.listarActividades();
    }

    @GetMapping("/viajes/{viajeId}/actividades/disponibles")
    public List<ActividadResponse> availableActivities(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable Long viajeId) {
        return itinerarioService.listarDisponibles(Long.valueOf(jwt.getSubject()), viajeId);
    }

    @GetMapping("/viajes/{viajeId}/actividades/recomendadas")
    public ActividadRecomendacionResponse recommendedActivities(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable Long viajeId) {
        return itinerarioService.recomendar(Long.valueOf(jwt.getSubject()), viajeId);
    }

    @GetMapping("/viajes/{viajeId}/presupuesto-actividades")
    public PresupuestoActividadesResponse activityBudget(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable Long viajeId) {
        return itinerarioService.presupuestoActividades(Long.valueOf(jwt.getSubject()), viajeId);
    }

    @PostMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades")
    public ActividadResponse addActivity(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable Long viajeId,
                                         @PathVariable Long diaId,
                                         @Valid @RequestBody ActividadCreateRequest request) {
        return itinerarioService.agregarActividad(Long.valueOf(jwt.getSubject()), viajeId, diaId, request);
    }

    @PostMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades/{actividadId}")
    public ActividadResponse assignExistingActivity(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable Long viajeId,
                                                    @PathVariable Long diaId,
                                                    @PathVariable Long actividadId,
                                                    @Valid @RequestBody AgregarActividadExistenteRequest request) {
        return itinerarioService.agregarActividadExistente(Long.valueOf(jwt.getSubject()), viajeId, diaId,
                actividadId, request.orden());
    }

    @PostMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/recomendaciones-externas/{externalId}")
    public ActividadResponse addExternalRecommendation(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable Long viajeId,
                                                       @PathVariable Long diaId,
                                                       @PathVariable String externalId,
                                                       @Valid @RequestBody AgregarActividadExistenteRequest request) {
        return itinerarioService.agregarRecomendacionExterna(Long.valueOf(jwt.getSubject()), viajeId, diaId,
                externalId, request.orden());
    }

    @DeleteMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades/{actividadId}")
    public ResponseEntity<Void> removeActivity(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable Long viajeId,
                                               @PathVariable Long diaId,
                                               @PathVariable Long actividadId) {
        itinerarioService.eliminarAsignacion(Long.valueOf(jwt.getSubject()), viajeId, diaId, actividadId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades/{actividadId}")
    public ResponseEntity<Void> reorderActivity(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable Long viajeId,
                                                @PathVariable Long diaId,
                                                @PathVariable Long actividadId,
                                                @Valid @RequestBody ReordenarActividadRequest request) {
        itinerarioService.reordenarActividad(Long.valueOf(jwt.getSubject()), viajeId, diaId, actividadId, request.orden());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades/{actividadId}/dia")
    public ResponseEntity<Void> moveActivity(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable Long viajeId,
                                             @PathVariable Long diaId,
                                             @PathVariable Long actividadId,
                                             @Valid @RequestBody MoverActividadRequest request) {
        itinerarioService.moverActividad(Long.valueOf(jwt.getSubject()), viajeId, diaId, actividadId,
                request.diaDestinoId());
        return ResponseEntity.noContent().build();
    }

    public record GenerateRequest(@NotNull Long viajeId) { }
}
