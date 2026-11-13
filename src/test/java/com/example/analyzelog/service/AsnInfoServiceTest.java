package com.example.analyzelog.service;

import com.example.analyzelog.model.AsnInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsnInfoServiceTest {

    private static List<AsnInfo> parse(String json) {
        return AsnInfoService.parseJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesNameCountryAndCategory() {
        List<AsnInfo> result = parse("""
                [{"asn": 24940, "metadata": {"handle": "HETZNER-AS", "description": "Hetzner Online GmbH",
                   "countryCode": "DE", "category": "hosting"}, "stats": null},
                 {"asn": 16276, "metadata": {"handle": "OVH", "description": "OVH SAS",
                   "countryCode": "FR", "category": "isp"}}]""");

        assertEquals(2, result.size());
        assertEquals(new AsnInfo("24940", "HETZNER-AS", "Hetzner Online GmbH", "DE", "hosting"), result.get(0));
        assertEquals("isp", result.get(1).category());
    }

    @Test
    void missingOrNullFieldsBecomeNull() {
        List<AsnInfo> result = parse("""
                [{"asn": 99, "metadata": {"handle": "", "description": null, "category": null}},
                 {"asn": 100}]""");

        assertEquals(2, result.size());
        assertNull(result.get(0).handle());
        assertNull(result.get(0).name());
        assertNull(result.get(0).countryCode());
        assertNull(result.get(0).category());
        assertNull(result.get(1).name());
    }

    @Test
    void ignoresEntriesWithoutNumericAsn() {
        assertTrue(parse("[{\"asn\": \"x\", \"metadata\": {}}, {\"metadata\": {}}]").isEmpty());
    }

    @Test
    void rejectsNonArrayPayload() {
        assertThrows(IllegalStateException.class, () -> parse("{\"asn\": 1}"));
    }
}
