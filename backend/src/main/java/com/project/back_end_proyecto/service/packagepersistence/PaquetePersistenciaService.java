package com.project.back_end_proyecto.service.packagepersistence;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import com.project.back_end_proyecto.dto.packagepersistence.PaqueteGuardarRequest;
import com.project.back_end_proyecto.dto.packagepersistence.PaqueteGuardarRequest.Actividad;
import com.project.back_end_proyecto.dto.packagepersistence.PaqueteGuardarRequest.TipoActividad;
import com.project.back_end_proyecto.dto.packagepersistence.PaqueteGuardarResponse;
import com.project.back_end_proyecto.repository.PaqueteSnapshotRepository;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

@Service
@Profile("oracle")
@ConditionalOnProperty(name = "smarttrip.package-persistence.enabled", havingValue = "true")
public class PaquetePersistenciaService {
    private static final String REQUEST_KEY_PATTERN = "[A-Za-z0-9._:-]{8,80}";
    private final PaqueteSnapshotRepository repository;
    private final PaqueteTripAccess access;
    private final ObjectMapper mapper;
    private final Validator validator;

    public PaquetePersistenciaService(PaqueteSnapshotRepository repository, PaqueteTripAccess access,
                                     ObjectMapper mapper, Validator validator) {
        this.repository = repository;
        this.access = access;
        this.validator = validator;
        this.mapper = mapper.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();
    }

    @Transactional
    public PaqueteGuardarResponse guardar(long userId, long tripId, String idempotencyKey,
                                         PaqueteGuardarRequest request) {
        if (idempotencyKey == null || !idempotencyKey.matches(REQUEST_KEY_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere Idempotency-Key válido.");
        }
        var trip = access.requireEdit(userId, tripId);
        if (!repository.lockTrip(tripId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Viaje no encontrado.");
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el contenido del paquete.");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    violations.iterator().next().getMessage());
        }
        validateReferencesAndDates(tripId, trip.fechaInicio(), trip.fechaFin(), request);
        String requestJson = json(request);
        String requestHash = sha256(requestJson);

        var previousRequest = repository.findRequest(tripId, idempotencyKey);
        if (previousRequest.isPresent()) {
            if (!MessageDigest.isEqual(previousRequest.get().requestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key ya fue usado con otro contenido.");
            }
            return readResponse(previousRequest.get().responseJson());
        }

        var current = repository.find(tripId);
        long currentRevision = current.map(PaqueteSnapshotRepository.StoredPackage::revision).orElse(0L);
        if (request.revisionEsperada() != currentRevision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La revisión del paquete cambió. Recarga el paquete antes de guardar.");
        }

        long nextRevision = currentRevision + 1;
        LocalDateTime now = LocalDateTime.now();
        var response = new PaqueteGuardarResponse(tripId, nextRevision, idempotencyKey, now, true, request);
        String responseJson = json(response);
        if (current.isPresent()) repository.updatePackage(tripId, nextRevision, requestJson, sha256(requestJson), now);
        else repository.insertPackage(tripId, nextRevision, requestJson, sha256(requestJson), now);
        // Same database transaction as the package row: a failed idempotency insert rolls back the save.
        repository.insertRequest(tripId, idempotencyKey, requestHash, nextRevision, responseJson, now);
        return response;
    }

    @Transactional(readOnly = true)
    public PaqueteGuardarResponse recuperar(long userId, long tripId) {
        access.requireRead(userId, tripId);
        return repository.find(tripId).map(stored -> new PaqueteGuardarResponse(
                tripId, stored.revision(), null, stored.updatedAt(), true, readRequest(stored.json())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Este viaje todavía no tiene un paquete guardado."));
    }

    private void validateReferencesAndDates(long tripId, java.time.LocalDate start,
                                            java.time.LocalDate end, PaqueteGuardarRequest request) {
        if (request == null || request.dias() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El paquete no contiene días.");
        }
        long expectedDayCount = ChronoUnit.DAYS.between(start, end) + 1;
        if (expectedDayCount != request.dias().size()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El paquete debe incluir cada fecha del viaje exactamente una vez.");
        }
        Set<java.time.LocalDate> dates = new HashSet<>();
        Set<Long> dayIds = new HashSet<>();
        Set<String> activityIdentities = new HashSet<>();
        for (var day : request.dias()) {
            if (!dates.add(day.fecha())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Una fecha del paquete está repetida.");
            }
            if (day.fecha().isBefore(start) || day.fecha().isAfter(end)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "La fecha de un día está fuera del rango del viaje.");
            }
            if (day.diaId() != null) {
                if (!dayIds.add(day.diaId()) || !repository.dayMatchesTripAndDate(day.diaId(), tripId, day.fecha())) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Un día del paquete no coincide con su viaje o fecha.");
                }
            }
            for (Actividad activity : day.actividades()) {
                if (activity.tipo() == TipoActividad.INTERNA
                        && !repository.activityExists(activity.actividadId())) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Una actividad interna ya no existe.");
                }
                String activityIdentity = switch (activity.tipo()) {
                    case INTERNA -> "internal\u0000" + activity.actividadId();
                    case EXTERNA -> activity.fuente().toLowerCase() + "\u0000" + activity.idExterno();
                    case MANUAL -> "manual\u0000" + activity.claveCliente();
                };
                if (!activityIdentities.add(activityIdentity)) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Una misma actividad aparece más de una vez en el paquete.");
                }
            }
        }
        if (request.vueloSeleccionado() != null && request.vueloSeleccionado().vueloId() != null
                && !repository.flightBelongsToTrip(request.vueloSeleccionado().vueloId(), tripId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El vuelo seleccionado no pertenece a este viaje.");
        }
        if (request.alojamiento() != null && request.alojamiento().alojamientoId() != null
                && !repository.lodgingBelongsToTrip(request.alojamiento().alojamientoId(), tripId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El alojamiento seleccionado no pertenece a este viaje.");
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El paquete no se pudo serializar.");
        }
    }

    private PaqueteGuardarRequest readRequest(String value) {
        try { return mapper.readValue(value, PaqueteGuardarRequest.class); }
        catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "El paquete persistido no se pudo leer.");
        }
    }

    private PaqueteGuardarResponse readResponse(String value) {
        try { return mapper.readValue(value, PaqueteGuardarResponse.class); }
        catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "La respuesta idempotente no se pudo recuperar.");
        }
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 no está disponible.", impossible); }
    }
}
