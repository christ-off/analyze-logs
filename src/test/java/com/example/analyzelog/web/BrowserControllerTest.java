package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameHumanTrafficStats;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(BrowserController.class)
@EnableConfigurationProperties(AppProperties.class)
class BrowserControllerTest {

    private static final String KEYS_AND_LABELS = """
            chrome,  Chrome
            edge,    Edge
            firefox, Firefox
            safari,  Safari
            """;

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @ParameterizedTest
    @CsvSource(textBlock = KEYS_AND_LABELS)
    void resultTypesReturnsJson(String key, String label) {
        when(dashboardService.browserResultTypes(eq(label), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new NameCount("Hit", 80), new NameCount("Miss", 20)));

        assertThat(get(key, "result-types"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Hit");
    }

    @ParameterizedTest
    @CsvSource(textBlock = KEYS_AND_LABELS)
    void countriesReturnsJson(String key, String label) {
        when(dashboardService.browserCountries(eq(label), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new NameCount("France", 50)));

        assertThat(get(key, "countries"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("France");
    }

    @ParameterizedTest
    @CsvSource(textBlock = KEYS_AND_LABELS)
    void uriStemsReturnsJson(String key, String label) {
        when(dashboardService.browserUrlsByResultType(eq(label), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameResultTypeCount("/index.html", 20, 5, 0, 3)));

        assertThat(get(key, "uri-stems"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("/index.html");
    }

    @ParameterizedTest
    @CsvSource(textBlock = KEYS_AND_LABELS)
    void requestsPerDayReturnsJson(String key, String label) {
        when(dashboardService.browserRequestsPerDay(eq(label), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new DailyResultTypeCount(LocalDate.of(2026, Month.JANUARY, 15), 10, 2, 0, 0)));

        assertThat(get(key, "requests-per-day"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(10);
    }

    @ParameterizedTest
    @CsvSource(textBlock = KEYS_AND_LABELS)
    void userAgentsReturnsJson(String key, String label) {
        when(dashboardService.browserRawUserAgents(eq(label), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new NameResultTypeCount("Mozilla/5.0 (Windows NT 10.0)", 80, 30, 5, 3)));

        assertThat(get(key, "user-agents"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Mozilla/5.0 (Windows NT 10.0)");
    }

    @ParameterizedTest
    @CsvSource(textBlock = KEYS_AND_LABELS)
    void humanTrafficReturnsJson(String key, String label) {
        when(dashboardService.browserHumanTraffic(eq(label), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new NameHumanTrafficStats("Mozilla/5.0 (Windows NT 10.0)", 8, 10)));

        assertThat(get(key, "human-traffic"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].humanRequests").isEqualTo(8);
    }

    @ParameterizedTest
    @CsvSource({"chrome", "edge", "firefox", "safari"})
    void invalidDateRangeReturns400(String key) {
        assertThat(mvc.get().uri("/api/" + key + "/result-types")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @CsvSource({"opera", "ua-detail-x"})
    void unknownBrowserReturns404(String key) {
        assertThat(get(key, "result-types")).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult get(String key, String endpoint) {
        return mvc.get().uri("/api/" + key + "/" + endpoint)
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange();
    }
}
