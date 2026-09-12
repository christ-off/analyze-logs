package com.example.analyzelog.model;

import java.time.Instant;

// One raw request whose user agent or referer matched a known social/messaging network signature
// (link-preview crawler UA, or a click-through Referer header).
public record SocialNetworkRequest(String network, Instant timestamp, String userAgent, String uaName, String uriStem,
                                    String country, long hit, long miss, long function, long error) {
}
