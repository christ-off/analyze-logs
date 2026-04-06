package com.example.analyzelog.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class EdgeLocationResolverTest {

    private static EdgeLocationResolver resolver;

    @BeforeAll
    static void setUp() {
        resolver = new EdgeLocationResolver();
    }

    // --- extractIata ---

    @ParameterizedTest
    @CsvSource({
        "CDG55-P2,  CDG",
        "SFO53-P7,  SFO",
        "LHR5-P1,   LHR",
        "ATL59-P10, ATL",
        "ORD56-P13, ORD",
        "HKG54-P1,  HKG",
    })
    void extractIata_validCodes(String raw, String expectedIata) {
        assertEquals(expectedIata.strip(), resolver.extractIata(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "NOTACODE", "cdg55-P2", "123-P1"})
    void extractIata_invalidOrBlank_returnsNull(String raw) {
        assertNull(resolver.extractIata(raw));
    }

    // --- resolve ---

    @Test
    void resolve_knownIata_returnsLocation() {
        var loc = resolver.resolve("CDG");

        assertNotNull(loc);
        assertEquals("Paris", loc.city());
        assertEquals("France", loc.country());
        assertEquals("FR", loc.countryCode());
        assertNotNull(loc.pricingRegion());
    }

    @Test
    void resolve_unknownIata_returnsNull() {
        assertNull(resolver.resolve("ZZZ"));
    }

    @Test
    void resolve_null_returnsNull() {
        assertNull(resolver.resolve(null));
    }

    // --- resolveDisplay ---

    @ParameterizedTest
    @CsvSource({
        "CDG, 'Paris, France'",
        "SFO, 'San Francisco, United States'",
        "LHR, 'London, Great Britain'",
        "FRA, 'Frankfurt am Main, Germany'",
        "ARN, 'Stockholm, Sweden'",
        "HKG, 'Hong Kong, China'",
    })
    void resolveDisplay_knownIata(String iata, String expected) {
        assertEquals(expected, resolver.resolveDisplay(iata));
    }

    @Test
    void resolveDisplay_unknownIata_returnsRawValue() {
        assertEquals("ZZZ", resolver.resolveDisplay("ZZZ"));
    }

    @Test
    void resolveDisplay_null_returnsNull() {
        assertNull(resolver.resolveDisplay(null));
    }

    // --- pricingRegion coverage ---

    @Test
    void pricingRegion_mappedForEuropeanLocation() {
        var loc = resolver.resolve("FRA");

        assertNotNull(loc);
        assertEquals("Europe", loc.pricingRegion());
    }

    @Test
    void pricingRegion_mappedForUsLocation() {
        var loc = resolver.resolve("IAD");

        assertNotNull(loc);
        assertTrue(loc.pricingRegion().contains("United States"));
    }
}
