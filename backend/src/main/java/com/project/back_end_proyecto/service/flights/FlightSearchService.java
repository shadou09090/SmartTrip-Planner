package com.project.back_end_proyecto.service.flights;

import com.project.back_end_proyecto.dto.FlightPlaceSuggestion;
import com.project.back_end_proyecto.dto.FlightSearchRequest;
import com.project.back_end_proyecto.dto.FlightSearchResponse;
import com.project.back_end_proyecto.service.TripPermissionService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@Profile("!local-no-db")
public class FlightSearchService {
    private final TripPermissionService permissions;
    private final DuffelSearchService duffelSearch;

    public FlightSearchService(TripPermissionService permissions, DuffelSearchService duffelSearch) {
        this.permissions = permissions;
        this.duffelSearch = duffelSearch;
    }

    public List<FlightPlaceSuggestion> suggestPlaces(Long userId, Long tripId, String query) {
        permissions.requireRead(userId, tripId);
        return duffelSearch.suggestPlaces(query);
    }

    public FlightSearchResponse search(Long userId, Long tripId, FlightSearchRequest request) {
        permissions.requireRead(userId, tripId);
        if (StringUtils.hasText(request.origin()) && request.origin().equalsIgnoreCase(request.destination())) {
            throw new IllegalArgumentException("El origen y el destino deben ser diferentes.");
        }
        return duffelSearch.search(request);
    }

    public FlightSearchResponse.Offer verifyOfferForPersistence(Long userId, Long tripId, String offerId) {
        permissions.requireEdit(userId, tripId);
        FlightSearchResponse.Offer offer = duffelSearch.retrieveOffer(offerId);
        if (offer.slices().size() != 1 || offer.slices().get(0).segments().size() != 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El esquema Oracle actual solo permite guardar una oferta de un tramo directo y de ida; no se almacenaron escalas ni regreso.");
        }
        if (!"COP".equals(offer.currency())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Oracle no tiene una columna de moneda; solo se puede guardar una oferta verificada cuyo precio esté en COP.");
        }
        BigDecimal amount = offer.totalAmount();
        if (amount.scale() > 2 || amount.precision() - amount.scale() > 10) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El precio verificado no cabe en la precisión de la columna Oracle VUELO.PRECIO.");
        }
        FlightSearchResponse.Segment segment = offer.slices().get(0).segments().get(0);
        if (!StringUtils.hasText(segment.origin()) || !StringUtils.hasText(segment.destination())
                || !StringUtils.hasText(segment.departureAt()) || !StringUtils.hasText(segment.arrivalAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Duffel no devolvió los datos completos del segmento; no se guardó la oferta.");
        }
        return offer;
    }
}
