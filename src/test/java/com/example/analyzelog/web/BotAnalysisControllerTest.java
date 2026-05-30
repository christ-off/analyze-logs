package com.example.analyzelog.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(BotAnalysisController.class)
class BotAnalysisControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void defaultRangeRendersPage() {
        assertThat(mvc.get().uri("/bot-analysis").exchange())
                .hasStatusOk()
                .model().containsEntry("activeRange", "7d");
    }

    @Test
    void range1dSetsActiveRange() {
        assertThat(mvc.get().uri("/bot-analysis").param("range", "1d").exchange())
                .hasStatusOk()
                .model().containsEntry("activeRange", "1d");
    }

    @Test
    void range30dSetsActiveRange() {
        assertThat(mvc.get().uri("/bot-analysis").param("range", "30d").exchange())
                .hasStatusOk()
                .model().containsEntry("activeRange", "30d");
    }

    @Test
    void range3mSetsActiveRange() {
        assertThat(mvc.get().uri("/bot-analysis").param("range", "3m").exchange())
                .hasStatusOk()
                .model().containsEntry("activeRange", "3m");
    }

    @Test
    void customRangeSetsDateAttributes() {
        assertThat(mvc.get().uri("/bot-analysis")
                .param("from", "2026-01-01").param("to", "2026-01-31").exchange())
                .hasStatusOk()
                .model().containsEntry("activeRange", "custom")
                .containsEntry("fromDate", "2026-01-01")
                .containsEntry("toDate", "2026-01-31");
    }
}
