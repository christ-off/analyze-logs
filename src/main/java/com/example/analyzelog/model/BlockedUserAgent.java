package com.example.analyzelog.model;

// A user agent whose blocked (Filtered) requests show up on the Blocked Hosted Applications page:
// the raw string (identifies the app and keys the /ua-requests link) and its blocked-request count.
public record BlockedUserAgent(String userAgent, long count) {}
