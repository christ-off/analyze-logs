package com.example.analyzelog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// humanRequests/nonWebpRequests use the same basis as the country detail page's human proportion
// (.webp requests excluded), so both pages show the same percentage.
public record CountryStats(String code, String name, long hit, long miss, long function, long error,
                           long humanRequests, long nonWebpRequests, long mastodon, long searchBots, long feeds) {
    @JsonProperty
    public long total() {
        return hit + miss + function + error;
    }

    @JsonProperty
    public double humanPercentage() {
        return nonWebpRequests == 0 ? 0.0 : 100.0 * humanRequests / nonWebpRequests;
    }
}
