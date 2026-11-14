package com.example.analyzelog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// name/category come from the downloaded asn_info and are null until it has been loaded.
// humanRequests counts served (Hit/Miss) requests of "Probable human" pairs; blocked requests
// (function/error) are left out of the percentage since they say nothing about who is behind them.
// feeds counts served requests for /feed.xml and /rss.xml.
public record AsnResultTypeCount(String asn, String name, String category,
                                 long hit, long miss, long function, long error,
                                 long humanRequests, long feeds) {
    @JsonProperty
    public long total() {
        return hit + miss + function + error;
    }

    @JsonProperty
    public double humanPercentage() {
        long served = hit + miss;
        return served == 0 ? 0.0 : 100.0 * humanRequests / served;
    }
}
