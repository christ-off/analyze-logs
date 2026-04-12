package com.example.analyzelog.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void rootReturns200() {
        assertThat(mvc.get().uri("/").exchange())
                .hasStatusOk()
                .hasViewName("dashboard");
    }

    @Test
    void defaultRangeIs7Days() {
        assertThat(mvc.get().uri("/").exchange())
                .model().containsEntry("activeRange", "7d");
    }

    @Test
    void rangeParamSetsActiveRange() {
        assertThat(mvc.get().uri("/").param("range", "30d").exchange())
                .model().containsEntry("activeRange", "30d");
    }

    @Test
    void customDateParamsSetCustomRange() {
        assertThat(mvc.get().uri("/")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .model()
                .containsEntry("activeRange", "custom")
                .containsEntry("fromDate", "2026-01-01")
                .containsEntry("toDate", "2026-01-31");
    }

    @Test
    void invalidDateRangeReturns400() {
        assertThat(mvc.get().uri("/")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void modelContainsFromAndToIso() {
        assertThat(mvc.get().uri("/").param("range", "1d").exchange())
                .model()
                .containsKey("from")
                .containsKey("to");
    }

    @Test
    void uaDetailDefaultRangeIs7Days() {
        assertThat(mvc.get().uri("/ua-detail").param("ua", "TestBot").exchange())
                .hasStatusOk()
                .hasViewName("ua-detail")
                .model().containsEntry("activeRange", "7d");
    }

    @Test
    void uaDetailRangeParamSetsActiveRange() {
        assertThat(mvc.get().uri("/ua-detail").param("ua", "TestBot").param("range", "30d").exchange())
                .model().containsEntry("activeRange", "30d");
    }

    @Test
    void uaDetailCustomDateSetsCustomRange() {
        assertThat(mvc.get().uri("/ua-detail")
                .param("ua", "TestBot").param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .model().containsEntry("activeRange", "custom");
    }

    @Test
    void countryDetailDefaultRangeIs7Days() {
        assertThat(mvc.get().uri("/country-detail").param("country", "US").exchange())
                .hasStatusOk()
                .hasViewName("country-detail")
                .model().containsEntry("activeRange", "7d");
    }

    @Test
    void countryDetailRangeParamSetsActiveRange() {
        assertThat(mvc.get().uri("/country-detail").param("country", "US").param("range", "1d").exchange())
                .model().containsEntry("activeRange", "1d");
    }

    @Test
    void countryDetailCustomDateSetsCustomRange() {
        assertThat(mvc.get().uri("/country-detail")
                .param("country", "US").param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .model().containsEntry("activeRange", "custom");
    }

    @Test
    void urlDetailDefaultRangeIs7Days() {
        assertThat(mvc.get().uri("/url-detail").param("url", "/index.html").exchange())
                .hasStatusOk()
                .hasViewName("url-detail")
                .model()
                .containsEntry("activeRange", "7d")
                .containsEntry("urlName", "/index.html");
    }

    @Test
    void urlDetailRangeParamSetsActiveRange() {
        assertThat(mvc.get().uri("/url-detail").param("url", "/index.html").param("range", "3m").exchange())
                .model()
                .containsEntry("activeRange", "3m")
                .containsKey("from")
                .containsKey("to");
    }

    @Test
    void urlDetailCustomDateSetsCustomRange() {
        assertThat(mvc.get().uri("/url-detail")
                .param("url", "/index.html").param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .model().containsEntry("activeRange", "custom");
    }
}