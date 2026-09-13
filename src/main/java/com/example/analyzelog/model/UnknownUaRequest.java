package com.example.analyzelog.model;

import java.time.Instant;

// One raw "Probable human" request (see DashboardService.HUMAN_PAGE_FILTER) whose user agent
// UserAgentClassifier could not identify as a known browser — surfaced on the Human page.
public record UnknownUaRequest(Instant timestamp, String userAgent, String uriStem,
                                long hit, long miss, long function, long error) {
}
