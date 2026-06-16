package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@WebMvcTest(RefererDetailController.class)
@EnableConfigurationProperties(AppProperties.class)
class RefererDetailControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void urlsReturnsJson() {
        when(dashboardService.refererTopUrlsByResultType(eq("google.com"), any(Instant.class), any(Instant.class), anyInt(), anyBoolean()))
                .thenReturn(List.of(new NameResultTypeCount("/blog/", 50, 10, 0, 0)));

        assertThat(mvc.get().uri("/api/referer-detail/urls")
                .param("referer", "google.com")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("/blog/");
    }

    @Test
    void requestsPerDayReturnsJson() {
        when(dashboardService.refererRequestsPerDay(eq("google.com"), any(Instant.class), any(Instant.class), anyBoolean()))
                .thenReturn(List.of(new DailyResultTypeCount(LocalDate.of(2026, Month.JANUARY, 15), 10, 2, 0, 0)));

        assertThat(mvc.get().uri("/api/referer-detail/requests-per-day")
                .param("referer", "google.com")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(10);
    }

    @Test
    void invalidDateRangeReturns400() {
        assertThat(mvc.get().uri("/api/referer-detail/requests-per-day")
                .param("referer", "google.com")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingRefererParamReturns400() {
        assertThat(mvc.get().uri("/api/referer-detail/requests-per-day")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
