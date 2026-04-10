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

@WebMvcTest(UaDetailController.class)
class UaDetailControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void resultTypesReturnsJson() {
        when(dashboardService.uaResultTypes(eq("Chrome / Windows"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new NameCount("Hit", 80), new NameCount("Miss", 20)));

        assertThat(mvc.get().uri("/api/ua-detail/result-types")
                .param("ua", "Chrome / Windows")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Hit");
    }

    @Test
    void countriesReturnsJson() {
        when(dashboardService.uaCountries(eq("Chrome / Windows"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new NameCount("France", 50)));

        assertThat(mvc.get().uri("/api/ua-detail/countries")
                .param("ua", "Chrome / Windows")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("France");
    }

    @Test
    void uriStemsReturnsJson() {
        when(dashboardService.uaUrlsByResultType(eq("Chrome / Windows"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameResultTypeCount("/index.html", 20, 5, 0, 3, 2)));

        assertThat(mvc.get().uri("/api/ua-detail/uri-stems")
                .param("ua", "Chrome / Windows")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("/index.html");
    }

    @Test
    void requestsPerDayReturnsJson() {
        when(dashboardService.uaRequestsPerDay(eq("Chrome / Windows"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new DailyResultTypeCount(LocalDate.of(2026, 1, 15), 10, 2, 0, 0, 1)));

        assertThat(mvc.get().uri("/api/ua-detail/requests-per-day")
                .param("ua", "Chrome / Windows")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(10);
    }

    @Test
    void userAgentsReturnsJson() {
        when(dashboardService.uaRawUserAgents(eq("Chrome / Windows"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        new NameResultTypeCount("Mozilla/5.0 (Windows NT 10.0)", 80, 30, 5, 3, 2),
                        new NameResultTypeCount("Mozilla/5.0 (Windows NT 6.1)", 20, 8, 0, 1, 1)));

        assertThat(mvc.get().uri("/api/ua-detail/user-agents")
                .param("ua", "Chrome / Windows")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Mozilla/5.0 (Windows NT 10.0)");
    }

    @Test
    void invalidDateRangeReturns400() {
        assertThat(mvc.get().uri("/api/ua-detail/result-types")
                .param("ua", "Chrome / Windows")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingUaParamReturns400() {
        assertThat(mvc.get().uri("/api/ua-detail/result-types")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
