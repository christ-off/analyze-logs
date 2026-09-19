package com.example.analyzelog.model;

import java.time.Instant;

// One raw served (Hit/Miss) request whose user agent or referer matched a known social/messaging
// network signature (link-preview crawler UA, or a click-through Referer header). The network itself
// is the key of the map these are returned under, so it is not repeated on every row.
public record SocialNetworkRequest(Instant timestamp, String userAgent, String uaName, String uriStem,
                                   String country) {
}
