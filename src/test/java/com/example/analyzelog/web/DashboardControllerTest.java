package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.CountryClientCounts;
import com.example.analyzelog.model.HumanTrafficStats;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(DashboardController.class)
@EnableConfigurationProperties(AppProperties.class)
class DashboardControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @BeforeEach
    void stubHumanTrafficStats() {
        when(dashboardService.countryHumanTrafficStats(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new HumanTrafficStats(0, 0));
        when(dashboardService.countryClientCounts(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new CountryClientCounts(0, 0, 0));
    }

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

    @Test
    void humanReturns200() {
        assertThat(mvc.get().uri("/human").exchange())
                .hasStatusOk()
                .hasViewName("human");
    }

    @Test
    void humanDefaultRangeIs7Days() {
        assertThat(mvc.get().uri("/human").exchange())
                .model().containsEntry("activeRange", "7d");
    }

    @Test
    void securityReturns200() {
        assertThat(mvc.get().uri("/security").exchange())
                .hasStatusOk()
                .hasViewName("security");
    }

    @Test
    void securityDefaultRangeIs7Days() {
        assertThat(mvc.get().uri("/security").exchange())
                .model().containsEntry("activeRange", "7d");
    }

    @ParameterizedTest
    @CsvSource({"chrome,Chrome", "edge,Edge", "firefox,Firefox", "safari,Safari"})
    void browserPageRendersBrowserViewWithDefaultRange(String key, String label) {
        assertThat(mvc.get().uri("/" + key).exchange())
                .hasStatusOk()
                .hasViewName("browser")
                .model()
                .containsEntry("browserKey", key)
                .containsEntry("browserLabel", label)
                .containsEntry("activeRange", "7d");
    }

    @Test
    void firefoxModelContainsEsrVersion() {
        assertThat(mvc.get().uri("/firefox").exchange())
                .model().containsEntry("firefoxEsrVersion", 115);
    }

    @Test
    void nonFirefoxModelHasNoEsrVersion() {
        assertThat(mvc.get().uri("/chrome").exchange())
                .model().doesNotContainKey("firefoxEsrVersion");
    }
}
