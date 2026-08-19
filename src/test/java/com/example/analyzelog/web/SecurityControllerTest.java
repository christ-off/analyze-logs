package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.CountryCount;
import com.example.analyzelog.model.DailyCount;
import com.example.analyzelog.model.NameCount;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(SecurityController.class)
@EnableConfigurationProperties(AppProperties.class)
class SecurityControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void trafficCategoriesReturnsJson() {
        when(dashboardService.securityTrafficCategories(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new NameCount("PHP/WordPress", 42)));

        assertThat(mvc.get().uri("/api/security/traffic-categories")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("PHP/WordPress");
    }

    @Test
    void topCountriesReturnsJson() {
        when(dashboardService.securityTopCountries(any(Instant.class), any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new CountryCount("US", "United States", 10)));

        assertThat(mvc.get().uri("/api/security/top-countries")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].code").isEqualTo("US");
    }

    @Test
    void requestsPerDayReturnsJson() {
        when(dashboardService.securityRequestsPerDay(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new DailyCount(LocalDate.parse("2026-01-05"), 7)));

        assertThat(mvc.get().uri("/api/security/requests-per-day")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].count").isEqualTo(7);
    }

    @Test
    void invalidDateRangeReturns400() {
        assertThat(mvc.get().uri("/api/security/traffic-categories")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
