package com.example.analyzelog.web;

import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@WebMvcTest(CountryDetailController.class)
class CountryDetailControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void uaSplitReturnsJson() {
        when(dashboardService.countryTopUserAgentsByResultType(eq("FR"), any(Instant.class), any(Instant.class), anyInt(), anyBoolean()))
                .thenReturn(List.of(new NameResultTypeCount("Chrome / Windows", 50, 10, 0, 2)));

        assertThat(mvc.get().uri("/api/country-detail/ua-split")
                .param("country", "FR")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Chrome / Windows");

        assertThat(mvc.get().uri("/api/country-detail/ua-split")
                .param("country", "FR")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(50);
    }

    @Test
    void resultTypesReturnsJson() {
        when(dashboardService.countryResultTypes(eq("FR"), any(Instant.class), any(Instant.class), anyBoolean()))
                .thenReturn(List.of(new NameCount("Hit", 80), new NameCount("Miss", 20)));

        assertThat(mvc.get().uri("/api/country-detail/result-types")
                .param("country", "FR")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Hit");
    }

    @Test
    void urlSplitReturnsJson() {
        when(dashboardService.countryUrlsByResultType(eq("FR"), any(Instant.class), any(Instant.class), anyInt(), anyBoolean()))
                .thenReturn(List.of(new NameResultTypeCount("/index.html", 40, 10, 0, 2)));

        assertThat(mvc.get().uri("/api/country-detail/url-split")
                .param("country", "FR")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("/index.html");

        assertThat(mvc.get().uri("/api/country-detail/url-split")
                .param("country", "FR")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(40);
    }

    @Test
    void requestsPerDayReturnsJson() {
        when(dashboardService.countryRequestsPerDay(eq("FR"), any(Instant.class), any(Instant.class), anyBoolean()))
                .thenReturn(List.of(new DailyResultTypeCount(LocalDate.of(2026, 1, 15), 10, 2, 0, 0)));

        assertThat(mvc.get().uri("/api/country-detail/requests-per-day")
                .param("country", "FR")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(10);
    }

    @Test
    void invalidDateRangeReturns400() {
        assertThat(mvc.get().uri("/api/country-detail/result-types")
                .param("country", "FR")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingCountryParamReturns400() {
        assertThat(mvc.get().uri("/api/country-detail/result-types")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}