package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.RecomendacionExterna;
import com.project.back_end_proyecto.dto.RecomendacionDiagnostico;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.service.publicplaces.ErrorFuentePublica;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import com.project.back_end_proyecto.service.publicplaces.LugarPublico;
import com.project.back_end_proyecto.service.publicplaces.RelevanceScorer;
import com.project.back_end_proyecto.service.publicplaces.WikimediaCommonsPhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("!local-no-db")
public class ExternalActivityRecommendationService {
    private static final Logger log = LoggerFactory.getLogger(ExternalActivityRecommendationService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int RECOMMENDATION_LIMIT = 100;
    private static final int PHOTO_LOOKUP_LIMIT = 20;
    private final FuenteLugaresPublicos source;
    private final WikimediaCommonsPhotoService photos;
    private final boolean diagnosticsEnabled;
    private final Map<SelectionKey, CachedSelection> selections = new ConcurrentHashMap<>();

    @Autowired
    public ExternalActivityRecommendationService(FuenteLugaresPublicos source, WikimediaCommonsPhotoService photos,
            @Value("${smarttrip.recommendations.diagnostics:false}") boolean diagnosticsEnabled) {
        this.source = source;
        this.photos = photos;
        this.diagnosticsEnabled = diagnosticsEnabled;
    }

    public ExternalActivityRecommendationService(FuenteLugaresPublicos source, WikimediaCommonsPhotoService photos) {
        this(source, photos, false);
    }

    public Result recommend(Long tripId, String destination, LocalDate startDate, LocalDate endDate,
            List<String> interests) {
        List<String> requestedInterests = interests == null ? List.of() : interests;
        try {
            FuenteLugaresPublicos.ResultadoLugares discovery = source.buscarConDiagnostico(destination);
            // Mockito and third-party legacy implementations may return null for this
            // default method.
            if (discovery == null)
                discovery = new FuenteLugaresPublicos.ResultadoLugares(source.buscar(destination), List.of());
            List<LugarPublico> places = discovery.lugares();
            List<String> sourceWarnings = new ArrayList<>(discovery.avisos());
            RelevanceScorer.RankingResult ranking = RelevanceScorer.rankTourismDetailed(places, requestedInterests);
            List<RelevanceScorer.RankedPlace> ranked = ranking.ranked();
            List<LugarPublico> photoCandidates = ranked.stream().limit(PHOTO_LOOKUP_LIMIT)
                    .map(RelevanceScorer.RankedPlace::place).toList();
            Map<String, WikimediaCommonsPhotoService.VerifiedPhoto> verifiedPhotos = photos
                    .verifyPlaceImages(photoCandidates);
            if (verifiedPhotos == null)
                verifiedPhotos = Map.of();
            List<RecomendacionExterna> recommendations = new ArrayList<>();
            for (var item : ranked.stream().limit(RECOMMENDATION_LIMIT).toList()) {
                LugarPublico place = item.place();
                WikimediaCommonsPhotoService.VerifiedPhoto photo = verifiedPhotos.get(place.id());
                String provider = tag(place, "provider");
                String role = tag(place, "tourism_role");
                String reason;
                if ("gastronomy".equals(role)) {
                    reason = "Opción gastronómica complementaria; se muestra después de las atracciones turísticas.";
                } else if (!item.matchedInterests().isEmpty()) {
                    reason = "Atracción turística según sus metadatos públicos; coincide con: "
                            + String.join(", ", item.matchedInterests());
                } else {
                    reason = "Alternativa turística general, clasificada por metadatos públicos"
                            + (provider.isBlank() ? "." : " en " + provider + ".");
                }
                String scope = tag(place, "region_scope");
                if ("EXCURSION_OPTIONAL".equals(scope)) {
                    reason += " Excursión opcional de larga distancia; valida transporte, duración y fecha antes de programarla.";
                } else if ("REGIONAL_ATTRACTION".equals(scope) || "REGIONAL_CANDIDATE".equals(scope)) {
                    reason += " Atracción regional; confirma transporte y duración antes de programarla.";
                }
                java.math.BigDecimal distance = decimalTag(place, "distance_km");
                int tourismSignal = RelevanceScorer.tourismScore(place);
                String importance = RelevanceScorer.tourismImportance(tourismSignal);
                String normalizedCategory = RelevanceScorer.normalize(place.categoria());
                boolean coordinatesValid = validCoordinate(place.latitud(), -90, 90)
                        && validCoordinate(place.longitud(), -180, 180);
                boolean localScope = "LOCAL_ATTRACTION".equals(scope) || "CITY".equals(scope);
                String exclusionReason = null;
                if (!coordinatesValid)
                    exclusionReason = "No hay coordenadas geográficas válidas para planificar este lugar.";
                else if (importance.equals("EXPLORABLE"))
                    exclusionReason = "La categoría y los metadatos disponibles no aportan suficiente evidencia turística para incluirlo automáticamente.";
                else if (!localScope)
                    exclusionReason = scope.isBlank()
                            ? "El alcance geográfico no está confirmado; verifica la ubicación antes de planificarlo."
                            : "El proveedor lo clasifica como regional o excursión; verifica el traslado antes de incluirlo.";
                boolean itineraryEligible = coordinatesValid && !importance.equals("EXPLORABLE") && localScope;
                recommendations.add(new RecomendacionExterna(place.id(), place.nombre(), place.descripcion(),
                        place.categoria(), place.ubicacion(), place.latitud(), place.longitud(), null, null,
                        place.urlFuente(), photo == null ? null : photo.url(), item.matchedInterests(), reason,
                        "La fuente no informa disponibilidad para " + startDate + " a " + endDate + ".",
                        photo == null ? null : photo.attribution(), photo == null ? null : photo.license(),
                        photo == null ? null : photo.licenseUrl(), photo == null ? null : photo.sourceUrl(),
                        provider, scope, distance, tourismSignal, normalizedCategory, importance,
                        RelevanceScorer.tourismEvidence(place), itineraryEligible, exclusionReason));
                if (diagnosticsEnabled && recommendations.size() <= 12) {
                    log.info(
                            "tourism_candidate destination={} id={} name={} category={} coordinates={},{} city={} district={} boundary={} scope={} scopeReason={} relevance={} importance={} eligible={} exclusion={}",
                            safeLog(destination), safeLog(place.id()), safeLog(place.nombre()),
                            safeLog(normalizedCategory),
                            place.latitud(), place.longitud(), safeLog(tag(place, "city")),
                            safeLog(tag(place, "district")), safeLog(tag(place, "boundary_context")), safeLog(scope),
                            safeLog(tag(place, "scope_reason")), tourismSignal, importance, itineraryEligible,
                            safeLog(exclusionReason));
                }
                selections.put(new SelectionKey(tripId, place.id()), new CachedSelection(place, destination,
                        item.matchedInterests(), requestedInterests, Instant.now().plus(CACHE_TTL)));
            }
            selections.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
            if (selections.size() > 1000)
                selections.clear();
            List<String> warnings = recommendations.isEmpty() && !places.isEmpty()
                    ? append(sourceWarnings,
                            "Los proveedores respondieron, pero no devolvieron atractivos turísticos aptos; se omitieron alojamientos y negocios no turísticos.")
                    : List.copyOf(sourceWarnings);
            RecomendacionDiagnostico diagnostics = diagnosticsEnabled
                    ? diagnostics(destination, discovery, ranking, recommendations, warnings)
                    : null;
            if (diagnosticsEnabled) {
                Map<String, Integer> classifications = classificationCounts(recommendations);
                Map<String, Integer> exclusionReasons = exclusionReasons(recommendations);
                log.info(
                        "tourism_candidates destination={} received={} withCoordinates={} principal={} complementary={} explorable={} eligible={} regionalOrOptional={} excluded={} reasons={}",
                        safeLog(destination), recommendations.size(), classifications.get("conCoordenadasValidas"),
                        classifications.get("principales"), classifications.get("complementarios"),
                        classifications.get("explorables"), classifications.get("aptosParaItinerario"),
                        classifications.get("regionalesUOpcionales"), classifications.get("noElegibles"),
                        exclusionReasons);
            }
            return new Result(List.copyOf(recommendations), warnings, diagnostics);
        } catch (ErrorFuentePublica unavailable) {
            List<String> errors = List.of(unavailable.getMessage());
            List<String> diagnosticErrors = unavailable.avisos().isEmpty() ? errors : unavailable.avisos();
            return new Result(List.of(), errors, diagnosticsEnabled
                    ? new RecomendacionDiagnostico(destination, null, null, unavailable.fuentesConsultadas(),
                            zeroCounts(unavailable.fuentesConsultadas()), Map.of(), 0, diagnosticErrors)
                    : null);
        } catch (RuntimeException unavailable) {
            List<String> errors = List.of("No fue posible consultar los lugares públicos por el momento.");
            return new Result(List.of(), errors, diagnosticsEnabled
                    ? new RecomendacionDiagnostico(destination, null, null, List.of(), Map.of(), Map.of(), 0, errors)
                    : null);
        }
    }

    public Selection selection(Long tripId, String externalId, String destination, List<String> currentInterests) {
        CachedSelection cached = selections.get(new SelectionKey(tripId, externalId));
        boolean destinationMatches = cached != null && RelevanceScorer.normalize(cached.destination())
                .equals(RelevanceScorer.normalize(destination));
        boolean interestsMatch = cached != null && normalizeInterests(cached.requestedInterests())
                .equals(normalizeInterests(currentInterests));
        if (cached == null || cached.expiresAt().isBefore(Instant.now()) || !destinationMatches || !interestsMatch) {
            selections.remove(new SelectionKey(tripId, externalId));
            throw new ResourceNotFoundException();
        }
        return new Selection(cached.place(), cached.matchedInterests());
    }

    public record Result(List<RecomendacionExterna> recommendations, List<String> warnings,
            RecomendacionDiagnostico diagnostics) {
        public Result(List<RecomendacionExterna> recommendations, List<String> warnings) {
            this(recommendations, warnings, null);
        }
    }

    public record Selection(LugarPublico place, List<String> matchedInterests) {
    }

    private record SelectionKey(Long tripId, String externalId) {
    }

    private record CachedSelection(LugarPublico place, String destination, List<String> matchedInterests,
            List<String> requestedInterests, Instant expiresAt) {
    }

    private static String tag(LugarPublico place, String key) {
        return place.etiquetas() == null ? "" : place.etiquetas().getOrDefault(key, "");
    }

    private static String safeLog(String value) {
        return value == null ? ""
                : value.replaceAll("[\\r\\n\\t]", " ").substring(0,
                        Math.min(value.replaceAll("[\\r\\n\\t]", " ").length(), 160));
    }

    private static java.math.BigDecimal decimalTag(LugarPublico place, String key) {
        try {
            return new java.math.BigDecimal(tag(place, key));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean validCoordinate(java.math.BigDecimal value, double minimum, double maximum) {
        if (value == null)
            return false;
        double number = value.doubleValue();
        return Double.isFinite(number) && number >= minimum && number <= maximum;
    }

    private static List<String> append(List<String> existing, String warning) {
        List<String> result = new ArrayList<>(existing);
        result.add(warning);
        return List.copyOf(result);
    }

    private static Map<String, Integer> zeroCounts(List<String> sources) {
        if (sources == null || sources.isEmpty())
            return Map.of();
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        sources.forEach(source -> counts.put(source, 0));
        return Map.copyOf(counts);
    }

    private RecomendacionDiagnostico diagnostics(String destination, FuenteLugaresPublicos.ResultadoLugares discovery,
            RelevanceScorer.RankingResult ranking,
            List<RecomendacionExterna> recommendations,
            List<String> warnings) {
        java.math.BigDecimal latitude = null;
        java.math.BigDecimal longitude = null;
        try {
            var center = source.geocodificarDestino(destination).orElse(null);
            if (center != null) {
                latitude = center.latitud();
                longitude = center.longitud();
            }
        } catch (RuntimeException ignored) {
            // Diagnostics must not turn an otherwise usable partial result into a failed
            // search.
        }
        Map<String, Integer> classifications = classificationCounts(recommendations);
        Map<String, Integer> exclusionReasons = exclusionReasons(recommendations);
        return new RecomendacionDiagnostico(destination, latitude, longitude, discovery.fuentesConsultadas(),
                discovery.resultadosConservadosPorFuente(),
                Map.of("noTuristicosOAlojamiento", ranking.excludedByType(),
                        "duplicadosEliminados", ranking.duplicatesRemoved()),
                recommendations.size(), warnings, discovery.areaGeografica() == null
                        ? source.areaGeograficaConsultada(destination)
                        : discovery.areaGeografica(),
                classifications, exclusionReasons);
    }

    private static Map<String, Integer> classificationCounts(List<RecomendacionExterna> recommendations) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        counts.put("recibidosPorReact", recommendations.size());
        counts.put("conCoordenadasValidas", 0);
        counts.put("principales", 0);
        counts.put("complementarios", 0);
        counts.put("explorables", 0);
        counts.put("aptosParaItinerario", 0);
        counts.put("regionalesUOpcionales", 0);
        counts.put("noElegibles", 0);
        for (RecomendacionExterna place : recommendations) {
            boolean coordinates = validCoordinate(place.latitud(), -90, 90)
                    && validCoordinate(place.longitud(), -180, 180);
            if (coordinates)
                counts.merge("conCoordenadasValidas", 1, Integer::sum);
            switch (place.clasificacionTuristica() == null ? "EXPLORABLE" : place.clasificacionTuristica()) {
                case "PRINCIPAL" -> counts.merge("principales", 1, Integer::sum);
                case "COMPLEMENTARIO" -> counts.merge("complementarios", 1, Integer::sum);
                default -> counts.merge("explorables", 1, Integer::sum);
            }
            if ("REGIONAL_ATTRACTION".equals(place.alcance()) || "REGIONAL_CANDIDATE".equals(place.alcance())
                    || "EXCURSION_OPTIONAL".equals(place.alcance())) {
                counts.merge("regionalesUOpcionales", 1, Integer::sum);
            }
            if (place.elegibleItinerario())
                counts.merge("aptosParaItinerario", 1, Integer::sum);
            else
                counts.merge("noElegibles", 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private static Map<String, Integer> exclusionReasons(List<RecomendacionExterna> recommendations) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (RecomendacionExterna place : recommendations) {
            if (!place.elegibleItinerario() && place.razonNoElegible() != null) {
                counts.merge(place.razonNoElegible(), 1, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    private static java.util.Set<String> normalizeInterests(List<String> interests) {
        if (interests == null)
            return java.util.Set.of();
        return interests.stream().map(RelevanceScorer::normalize).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
