package com.example.analyzelog.service;

import com.example.analyzelog.config.AsnBlocklistProperties;
import com.example.analyzelog.model.AsnBlockCandidate;
import com.example.analyzelog.model.AsnResultTypeCount;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Builds the list of ASNs to push to the CloudFront KeyValueStore from the ASNs seen in the logs:
// observed "hosting" ASNs (quantcdn category) minus the allowlist, plus the forced ones.
// The allowlist and the forced ASNs come from the asn-blocklist properties.
@Service
public class AsnBlocklistService {

    private static final String HOSTING = "hosting";

    private final DashboardService dashboardService;
    private final Set<String> allowed;
    private final Set<String> forced;

    public AsnBlocklistService(DashboardService dashboardService, AsnBlocklistProperties properties) {
        this.dashboardService = dashboardService;
        this.allowed = Set.copyOf(properties.allowed());
        this.forced = Set.copyOf(properties.forced());
    }

    public List<AsnBlockCandidate> candidates(Instant from, Instant to) {
        return dashboardService.topAsnsByResultType(from, to, Integer.MAX_VALUE).stream()
                .map(this::classify)
                .filter(c -> c != null)
                .sorted(Comparator.comparingLong(AsnBlockCandidate::total).reversed())
                .toList();
    }

    // Body of the CloudFront KeyValueStore import file (S3 JSON source): one key per blocked ASN.
    public Map<String, Object> kvsImport(List<AsnBlockCandidate> candidates) {
        List<Map<String, String>> data = candidates.stream()
                .filter(AsnBlockCandidate::blocked)
                .map(c -> Map.of("key", c.asn(), "value", "1"))
                .toList();
        return Map.of("data", data);
    }

    // null: not a candidate (not hosting, not forced).
    AsnBlockCandidate classify(AsnResultTypeCount a) {
        boolean hosting = HOSTING.equals(a.category());
        boolean isForced = forced.contains(a.asn());
        if (!hosting && !isForced) {
            return null;
        }
        if (allowed.contains(a.asn())) {
            return candidate(a, false, "allowlisted");
        }
        return candidate(a, true, isForced ? "forced (scraper tagged " + a.category() + ")" : "hosting");
    }

    private static AsnBlockCandidate candidate(AsnResultTypeCount a, boolean blocked, String reason) {
        return new AsnBlockCandidate(a.asn(), a.name(), a.category(), a.total(), a.humanRequests(), blocked, reason);
    }
}
