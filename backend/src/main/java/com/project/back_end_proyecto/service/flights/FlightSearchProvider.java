package com.project.back_end_proyecto.service.flights;

import com.project.back_end_proyecto.dto.FlightPlaceSuggestion;
import com.project.back_end_proyecto.dto.FlightSearchRequest;
import com.project.back_end_proyecto.dto.FlightSearchResponse;

import java.util.List;

public interface FlightSearchProvider {
    List<FlightPlaceSuggestion> suggestPlaces(String query);
    FlightSearchResponse search(FlightSearchRequest request);
    FlightSearchResponse.Offer retrieveOffer(String offerId);
}
