package com.example.analyzelog.model;

public record SiteConfigFetcher(String name, long hit, long miss, long function, long error, long otherRequests) {
    public long total() { return hit + miss + function + error; }
}
