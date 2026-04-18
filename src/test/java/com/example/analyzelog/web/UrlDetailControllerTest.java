package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.CountryResultTypeCount;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

@WebMvcTest(UrlDetailController.class)
@EnableConfigurationProperties(AppProperties.class)
class UrlDetailControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void urlsReturnsJson() {
        when(dashboardService.urlMatchingUriStems(eq("/index.html"), any(Instant.class), any(Instant.class), anyBoolean()))
                .thenReturn(List.of(new NameCount("/index.html", 42)));

        assertThat(mvc.get().uri("/api/url-detail/urls")
                .param("url", "/index.html")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("/index.html");
    }

    @Test
    void countriesReturnsJson() {
        when(dashboardService.urlTopCountriesByResultType(eq("PHP"), any(Instant.class), any(Instant.class), anyInt(), anyBoolean()))
                .thenReturn(List.of(new CountryResultTypeCount("FR", "France", 30, 5, 0, 1)));

        assertThat(mvc.get().uri("/api/url-detail/countries")
                .param("url", "PHP")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("France");
    }

    @Test
    void userAgentsReturnsJson() {
        when(dashboardService.urlTopUserAgentsByResultType(eq("WordPress"), any(Instant.class), any(Instant.class), anyInt(), anyBoolean()))
                .thenReturn(List.of(new NameResultTypeCount("Chrome / Windows", 20, 5, 0, 1)));

        assertThat(mvc.get().uri("/api/url-detail/user-agents")
                .param("url", "WordPress")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Chrome / Windows");
    }

    @Test
    void requestsPerDayReturnsJson() {
        when(dashboardService.urlRequestsPerDay(eq("/index.html"), any(Instant.class), any(Instant.class), anyBoolean()))
                .thenReturn(List.of(new DailyResultTypeCount(LocalDate.of(2026, 1, 15), 10, 2, 0, 0)));

        assertThat(mvc.get().uri("/api/url-detail/requests-per-day")
                .param("url", "/index.html")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(10);
    }

    @Test
    void invalidDateRangeReturns400() {
        assertThat(mvc.get().uri("/api/url-detail/urls")
                .param("url", "/index.html")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingUrlParamReturns400() {
        assertThat(mvc.get().uri("/api/url-detail/urls")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
