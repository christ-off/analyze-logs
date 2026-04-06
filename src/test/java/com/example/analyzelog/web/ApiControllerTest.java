package com.example.analyzelog.web;

import com.example.analyzelog.model.DailyStatusCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@WebMvcTest(ApiController.class)
class ApiControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void uaNamesReturnsJson() {
        when(dashboardService.topUserAgents(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameCount("Chrome / Windows", 42)));

        assertThat(mvc.get().uri("/api/ua-names")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Chrome / Windows");
    }

    @Test
    void countriesReturnsJson() {
        when(dashboardService.topBlockedCountries(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameCount("CN", 100)));

        assertThat(mvc.get().uri("/api/countries")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("CN");
    }

    @Test
    void uriStemsReturnsJson() {
        when(dashboardService.topAllowedUriStems(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameCount("/feed.xml", 55)));

        assertThat(mvc.get().uri("/api/uri-stems")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("/feed.xml");
    }

    @Test
    void requestsPerDayReturnsJson() {
        when(dashboardService.requestsPerDay(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new DailyStatusCount(LocalDate.of(2026, 1, 15), 80, 15, 5)));

        assertThat(mvc.get().uri("/api/requests-per-day")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].day").isEqualTo("2026-01-15");
    }

    @Test
    void invalidFromToReturns400() {
        assertThat(mvc.get().uri("/api/ua-names")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingParamsReturns400() {
        assertThat(mvc.get().uri("/api/ua-names").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}