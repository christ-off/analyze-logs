package com.example.analyzelog.model;

public record NameHumanTrafficStats(String name, long humanRequests, long totalRequests) {
    public double percentage() {
        return totalRequests == 0 ? 0.0 : (100.0 * humanRequests / totalRequests);
    }
}
