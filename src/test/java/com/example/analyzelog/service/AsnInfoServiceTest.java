package com.example.analyzelog.service;

import com.example.analyzelog.model.AsnInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsnInfoServiceTest {

    @Test
    void parsesRowsAndSkipsHeader() {
        List<AsnInfo> result = AsnInfoService.parseCsv(
                "asn,handle,description,country-code\n24940,HETZNER-AS,Hetzner Online GmbH,DE\n16276,OVH,OVH SAS,FR\n");

        assertEquals(2, result.size());
        assertEquals(new AsnInfo("24940", "HETZNER-AS", "Hetzner Online GmbH", "DE", null), result.get(0));
        assertEquals("OVH SAS", result.get(1).name());
    }

    @Test
    void handlesQuotedFieldsWithCommasQuotesAndLineBreaks() {
        List<AsnInfo> result = AsnInfoService.parseCsv(
                "asn,handle,description,country-code\r\n"
                + "1,A,\"Example, Inc.\",US\r\n"
                + "2,B,\"Say \"\"hi\"\"\",FR\r\n"
                + "3,C,\"Two\nlines\",GB");

        assertEquals(3, result.size());
        assertEquals("Example, Inc.", result.get(0).name());
        assertEquals("Say \"hi\"", result.get(1).name());
        assertEquals("Two\nlines", result.get(2).name());
        assertEquals("GB", result.get(2).countryCode());
    }

    @Test
    void emptyFieldsBecomeNullAndCategoryIsNeverSet() {
        List<AsnInfo> result = AsnInfoService.parseCsv("asn,handle,description,country-code\n99,,,\n");

        assertEquals(1, result.size());
        assertNull(result.get(0).handle());
        assertNull(result.get(0).name());
        assertNull(result.get(0).countryCode());
        assertNull(result.get(0).category());
    }

    @Test
    void ignoresMalformedRows() {
        List<AsnInfo> result = AsnInfoService.parseCsv(
                "asn,handle,description,country-code\nnot-a-number,X,Y,Z\n5,only,three\n\n");

        assertTrue(result.isEmpty());
    }
}
