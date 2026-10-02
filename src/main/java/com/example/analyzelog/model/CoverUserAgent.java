package com.example.analyzelog.model;

public record CoverUserAgent(String name, long human, long blog, long other, long hit, long miss, long function, long error) {
    public long total() { return hit + miss + function + error; }
}
