package com.example.analyzelog.service;

import com.example.analyzelog.config.AsnBlocklistProperties;
import com.example.analyzelog.model.AsnBlockCandidate;
import com.example.analyzelog.model.AsnResultTypeCount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsnBlocklistServiceTest {

    private final AsnBlocklistService service = new AsnBlocklistService(
            null, new AsnBlocklistProperties(List.of("8075"), List.of("4134")));

    private static AsnResultTypeCount asn(String asn, String category) {
        return new AsnResultTypeCount(asn, "name", category, 1, 2, 3, 4, 0, 0);
    }

    @Test
    void hostingAsnIsBlocked() {
        AsnBlockCandidate c = service.classify(asn("24940", "hosting"));

        assertThat(c.blocked()).isTrue();
        assertThat(c.total()).isEqualTo(10);
    }

    @Test
    void allowlistedHostingAsnIsKeptButNotBlocked() {
        assertThat(service.classify(asn("8075", "hosting")).blocked()).isFalse();
    }

    @Test
    void asnOutsideTheAllowlistIsBlocked() {
        assertThat(service.classify(asn("15169", "hosting")).blocked()).isTrue();
        assertThat(service.classify(asn("396982", "hosting")).blocked()).isTrue();
    }

    @Test
    void forcedIspAsnIsBlocked() {
        assertThat(service.classify(asn("4134", "isp")).blocked()).isTrue();
    }

    @Test
    void otherCategoriesAreIgnored() {
        assertThat(service.classify(asn("3215", "isp"))).isNull();
        assertThat(service.classify(asn("999", null))).isNull();
    }

    @Test
    void kvsImportListsOnlyBlockedAsns() {
        var blocked = service.classify(asn("24940", "hosting"));
        var allowed = service.classify(asn("8075", "hosting"));

        assertThat(service.kvsImport(List.of(blocked, allowed)))
                .containsEntry("data", List.of(java.util.Map.of("key", "24940", "value", "1")));
    }
}
