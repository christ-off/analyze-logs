package com.example.analyzelog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// humanRequests counts served (Hit/Miss) requests of "Probable human" pairs; blocked requests
// (function/error) are left out of the percentage since they say nothing about who is behind them.
public record AsnResultTypeCount(String name, long hit, long miss, long function, long error, long humanRequests) {
    @JsonProperty
    public double humanPercentage() {
        long served = hit + miss;
        return served == 0 ? 0.0 : 100.0 * humanRequests / served;
    }
}
