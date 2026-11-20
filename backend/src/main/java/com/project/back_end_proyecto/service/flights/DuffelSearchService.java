package com.project.back_end_proyecto.service.flights;

import com.project.back_end_proyecto.dto.FlightPlaceSuggestion;
import com.project.back_end_proyecto.dto.FlightSearchRequest;
import com.project.back_end_proyecto.dto.FlightSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** Shared provider-only operations that do not read or persist trip data. */
@Service
public class DuffelSearchService {
    private final FlightSearchProvider provider;

    public DuffelSearchService(FlightSearchProvider provider) {
        this.provider = provider;
    }

    public List<FlightPlaceSuggestion> suggestPlaces(String query) {
        return provider.suggestPlaces(query);
    }

    public FlightSearchResponse search(FlightSearchRequest request) {
        if (StringUtils.hasText(request.origin()) && request.origin().equalsIgnoreCase(request.destination())) {
            throw new IllegalArgumentException("El origen y el destino deben ser diferentes.");
        }
        return provider.search(request);
    }

    public FlightSearchResponse.Offer retrieveOffer(String offerId) {
        return provider.retrieveOffer(offerId);
    }
}
