package com.example.analyzelog.model;

public record HumanTrafficStats(long humanRequests, long totalRequests) {
    public double percentage() {
        return totalRequests == 0 ? 0.0 : (100.0 * humanRequests / totalRequests);
    }
}