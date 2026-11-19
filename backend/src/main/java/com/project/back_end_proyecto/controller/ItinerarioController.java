package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.ActividadCreateRequest;
import com.project.back_end_proyecto.dto.ActividadRecomendacionResponse;
import com.project.back_end_proyecto.dto.ActividadResponse;
import com.project.back_end_proyecto.dto.AgregarActividadExistenteRequest;
import com.project.back_end_proyecto.dto.ItinerarioResponse;
import com.project.back_end_proyecto.dto.MoverActividadRequest;
import com.project.back_end_proyecto.dto.PresupuestoActividadesResponse;
import com.project.back_end_proyecto.dto.ReordenarActividadRequest;
import com.project.back_end_proyecto.dto.TrasladoRequest;
import com.project.back_end_proyecto.dto.TrasladoResponse;
import com.project.back_end_proyecto.entity.DiaItinerario;
import com.project.back_end_proyecto.service.ItinerarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Profile("!local-no-db")
public class ItinerarioController {
    private final ItinerarioService itinerarioService;

    public ItinerarioController(ItinerarioService itinerarioService) {
        this.itinerarioService = itinerarioService;
    }

    @PostMapping(path = {"/itinerario/calcular-traslado", "/calcular-traslado"})
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

    @PostMapping("/viajes/{viajeId}/itinerario")
    public ItinerarioResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId) {
        return itinerarioService.crearParaViaje(authenticatedUserId(jwt), viajeId);
    }

    @GetMapping("/viajes/{viajeId}/itinerario")
    public ResponseEntity<ItinerarioResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId) {
        return itinerarioService.consultarParaViaje(authenticatedUserId(jwt), viajeId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/itinerario/generar")
    public ItinerarioResponse generateLegacy(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody GenerateRequest request) {
        return itinerarioService.generarParaViaje(authenticatedUserId(jwt), request.viajeId());
    }

    @GetMapping("/actividades")
    public List<ActividadResponse> activities() {
        return itinerarioService.listarActividades();
    }

    @GetMapping("/viajes/{viajeId}/actividades/disponibles")
    public List<ActividadResponse> availableActivities(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable Long viajeId) {
        return itinerarioService.listarDisponibles(authenticatedUserId(jwt), viajeId);
    }

    @GetMapping("/viajes/{viajeId}/actividades/recomendadas")
    public ActividadRecomendacionResponse recommendedActivities(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable Long viajeId) {
        return itinerarioService.recomendar(authenticatedUserId(jwt), viajeId);
    }

    @GetMapping("/viajes/{viajeId}/presupuesto-actividades")
    public PresupuestoActividadesResponse activityBudget(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable Long viajeId) {
        return itinerarioService.presupuestoActividades(authenticatedUserId(jwt), viajeId);
    }

    @PostMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades")
    public ActividadResponse addActivity(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable Long viajeId,
                                         @PathVariable Long diaId,
                                         @Valid @RequestBody ActividadCreateRequest request) {
        return itinerarioService.agregarActividad(authenticatedUserId(jwt), viajeId, diaId, request);
    }

    @PostMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades/{actividadId}")
    public ActividadResponse assignExistingActivity(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable Long viajeId,
                                                    @PathVariable Long diaId,
                                                    @PathVariable Long actividadId,
                                                    @Valid @RequestBody AgregarActividadExistenteRequest request) {
        return itinerarioService.agregarActividadExistente(authenticatedUserId(jwt), viajeId, diaId,
                actividadId, request.orden());
    }

    @PostMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/recomendaciones-externas/{externalId}")
    public ActividadResponse addExternalRecommendation(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable Long viajeId,
                                                       @PathVariable Long diaId,
                                                       @PathVariable String externalId,
                                                       @Valid @RequestBody AgregarActividadExistenteRequest request) {
        return itinerarioService.agregarRecomendacionExterna(authenticatedUserId(jwt), viajeId, diaId,
                externalId, request.orden());
    }

    @DeleteMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades/{actividadId}")
    public ResponseEntity<Void> removeActivity(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable Long viajeId,
                                               @PathVariable Long diaId,
                                               @PathVariable Long actividadId) {
        itinerarioService.eliminarAsignacion(authenticatedUserId(jwt), viajeId, diaId, actividadId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades/{actividadId}")
    public ResponseEntity<Void> reorderActivity(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable Long viajeId,
                                                @PathVariable Long diaId,
                                                @PathVariable Long actividadId,
                                                @Valid @RequestBody ReordenarActividadRequest request) {
        itinerarioService.reordenarActividad(authenticatedUserId(jwt), viajeId, diaId, actividadId, request.orden());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/viajes/{viajeId}/itinerario/dias/{diaId}/actividades/{actividadId}/dia")
    public ResponseEntity<Void> moveActivity(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable Long viajeId,
                                             @PathVariable Long diaId,
                                             @PathVariable Long actividadId,
                                             @Valid @RequestBody MoverActividadRequest request) {
        itinerarioService.moverActividad(authenticatedUserId(jwt), viajeId, diaId, actividadId,
                request.diaDestinoId());
        return ResponseEntity.noContent().build();
    }

    private Long authenticatedUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            return 1L;
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    public record GenerateRequest(@NotNull Long viajeId) { }
}
