package com.example.analyzelog.model;

import java.time.Instant;
import java.util.List;

// One IP that presented more than one distinct known-bot identity (ua_name in a bot
// ua_group) within the queried range — a "face dancer" impersonating several crawlers.
public record IdentityShift(String ip, Instant firstSeen, Instant lastSeen,
                             List<NameCount> userAgents, List<IdentityShiftUrl> urls) {

    // One URL requested by that IP — which of its user agents fetched it, alongside the usual result-type split.
    public record IdentityShiftUrl(String name, long hit, long miss, long function, long error, List<String> userAgents) {}
}
