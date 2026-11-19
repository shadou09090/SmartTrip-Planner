package com.project.back_end_proyecto.service.publicplaces;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenStreetMapParser {
    public List<LugarPublico> parsearOverpass(Map<String, Object> payload) {
        Object raw = payload.get("elements");
        if (!(raw instanceof List<?> elements)) return List.of();
        List<LugarPublico> places = new ArrayList<>();
        for (Object value : elements) {
            if (!(value instanceof Map<?, ?> element)) continue;
            Map<String, String> tags = stringMap(element.get("tags"));
            String name = tags.get("name");
            String type = text(element.get("type"));
            String id = text(element.get("id"));
            if (name == null || name.isBlank() || type == null || id == null
                    || !List.of("node", "way", "relation").contains(type) || !id.matches("\\d+")) continue;
            Map<?, ?> center = element.get("center") instanceof Map<?, ?> map ? map : element;
            BigDecimal lat = decimal(center.get("lat"));
            BigDecimal lon = decimal(center.get("lon"));
            if (!validCoordinate(lat, new BigDecimal("-90"), new BigDecimal("90"))
                    || !validCoordinate(lon, new BigDecimal("-180"), new BigDecimal("180"))) continue;
            String location = address(tags);
            String category = category(tags);
            tags.putIfAbsent("provider", "OpenStreetMap");
            tags.putIfAbsent("tourism_role", isLodging(tags) ? "lodging"
                    : isGastronomy(tags) ? "gastronomy" : "attraction");
            tags.putIfAbsent("region_scope", "LOCAL_ATTRACTION");
            String sourceUrl = "https://www.openstreetmap.org/" + type + "/" + id;
            places.add(new LugarPublico("osm:" + type + ":" + id, name.trim(), clean(tags.get("description")),
                    category, location, lat, lon, sourceUrl, Map.copyOf(tags)));
        }
        return places;
    }

    public GeoPoint parsearGeocodificacion(List<?> results) {
        for (Object value : results) {
            if (!(value instanceof Map<?, ?> result)) continue;
            BigDecimal lat = decimal(result.get("lat"));
            BigDecimal lon = decimal(result.get("lon"));
            if (validCoordinate(lat, new BigDecimal("-90"), new BigDecimal("90"))
                    && validCoordinate(lon, new BigDecimal("-180"), new BigDecimal("180"))) return new GeoPoint(lat, lon);
        }
        return null;
    }

    private static String address(Map<String, String> tags) {
        List<String> parts = new ArrayList<>();
        String street = tags.get("addr:street");
        String number = tags.get("addr:housenumber");
        if (street != null) parts.add(number == null ? street : street + " " + number);
        for (String key : List.of("addr:city", "addr:state", "addr:country")) {
            if (tags.containsKey(key)) parts.add(tags.get(key));
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String category(Map<String, String> tags) {
        for (String key : List.of("tourism", "historic", "leisure", "amenity")) {
            if (tags.containsKey(key)) return tags.get(key).replace('_', ' ');
        }
        return "Lugar de interés";
    }

    private static boolean isLodging(Map<String, String> tags) {
        String tourism = tags.getOrDefault("tourism", "");
        return List.of("hotel", "motel", "hostel", "guest_house", "apartment", "camp_site").contains(tourism);
    }

    private static boolean isGastronomy(Map<String, String> tags) {
        String amenity = tags.getOrDefault("amenity", "");
        return "restaurant".equals(amenity) || "cafe".equals(amenity);
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((key, item) -> {
            if (key instanceof String k && item != null) result.put(k, item.toString());
        });
        return result;
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(value.toString()); } catch (NumberFormatException ignored) { return null; }
    }
    private static boolean validCoordinate(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    public record GeoPoint(BigDecimal latitude, BigDecimal longitude) { }
}
