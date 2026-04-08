package com.example.analyzelog.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class EdgeLocationResolver {

    private static final Pattern IATA = Pattern.compile("^([A-Z]{3})\\d+(?:-P\\d+)?$");

    public record Location(String city, String country, String countryCode, String pricingRegion) {
        public String display() {
            return city + ", " + country;
        }
    }

    private final Map<String, Location> locations;

    public EdgeLocationResolver() {
        this.locations = load();
    }

    /** Extracts the IATA code from a raw edge location value (e.g. "CDG55-P2" → "CDG"). Returns null if unrecognised. */
    public String extractIata(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var m = IATA.matcher(raw);
        return m.matches() ? m.group(1) : null;
    }

    /** Resolves a stored IATA code to its Location. Returns null if not found. */
    public Location resolve(String iata) {
        if (iata == null) return null;
        return locations.get(iata);
    }

    /** Resolves an IATA code to a display string (e.g. "CDG" → "Paris, France"). Falls back to the raw value. */
    public String resolveDisplay(String iata) {
        var loc = resolve(iata);
        return loc != null ? loc.display() : iata;
    }

    private static Map<String, Location> load() {
        try {
            var mapper = new ObjectMapper();
            var root = mapper.readTree(new ClassPathResource("aws-edge-locations.json").getInputStream());
            var map = new HashMap<String, Location>();
            root.properties().forEach(e -> {
                JsonNode n = e.getValue();
                map.put(e.getKey(), new Location(
                        n.path("city").asString(),
                        n.path("country").asString(),
                        n.path("countryCode").asString(),
                        n.path("pricingRegion").asString(null)
                ));
            });
            return Map.copyOf(map);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load aws-edge-locations.json", ex);
        }
    }
}
