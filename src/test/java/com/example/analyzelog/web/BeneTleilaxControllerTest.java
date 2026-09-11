package com.example.analyzelog.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(BeneTleilaxController.class)
class BeneTleilaxControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void defaultRangeRendersPage() {
        assertThat(mvc.get().uri("/bene-tleilax").exchange())
                .hasStatusOk()
                .model().containsEntry("activeRange", "7d");
    }

    @Test
    void customRangeSetsDateAttributes() {
        assertThat(mvc.get().uri("/bene-tleilax")
                .param("from", "2026-01-01").param("to", "2026-01-31").exchange())
                .hasStatusOk()
                .model().containsEntry("activeRange", "custom")
                .containsEntry("fromDate", "2026-01-01")
                .containsEntry("toDate", "2026-01-31");
    }
}
