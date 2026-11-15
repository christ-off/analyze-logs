package com.example.analyzelog.model;

// blocked=false rows are hosting ASNs kept out of the list (allowlist); reason says why either way.
public record AsnBlockCandidate(String asn, String name, String category, long total, long humanRequests,
                                boolean blocked, String reason) {}
