package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.BurstIp;
import com.example.analyzelog.model.CountryResultTypeCount;
import com.example.analyzelog.model.FakeBrowserUa;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.DisobedientBot;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import com.example.analyzelog.service.IpInfoService;
import com.example.analyzelog.service.RobotsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@WebMvcTest(ApiController.class)
@EnableConfigurationProperties(AppProperties.class)
class ApiControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @MockitoBean
    RobotsService robotsService;

    @MockitoBean
    IpInfoService ipInfoService;

    @Test
    void uaGroupsReturnsJson() {
        when(dashboardService.uaGroupCounts(any(Instant.class), any(Instant.class), eq(false)))
                .thenReturn(List.of(
                        new NameCount("Browsers", 400),
                        new NameCount("AI Bots",  180),
                        new NameCount("Other",     20)));

        assertThat(mvc.get().uri("/api/ua-groups")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Browsers");

        assertThat(mvc.get().uri("/api/ua-groups")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].count").isEqualTo(400);
    }

    @Test
    void uaGroupsExcludeBotsPassesFlag() {
        when(dashboardService.uaGroupCounts(any(Instant.class), any(Instant.class), eq(true)))
                .thenReturn(List.of(new NameCount("Browsers", 400)));

        assertThat(mvc.get().uri("/api/ua-groups")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .param("excludeBots", "true")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Browsers");
    }

    @Test
    void uaNamesReturnsJson() {
        when(dashboardService.topUserAgentsByResultType(any(Instant.class), any(Instant.class), anyInt(), eq(false)))
                .thenReturn(List.of(new NameResultTypeCount("Chrome / Windows", 30, 10, 0, 1)));

        assertThat(mvc.get().uri("/api/ua-names-split")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Chrome / Windows");

        assertThat(mvc.get().uri("/api/ua-names-split")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(30);
    }

    @Test
    void countriesReturnsJson() {
        when(dashboardService.topCountriesByResultType(any(Instant.class), any(Instant.class), anyInt(), eq(false)))
                .thenReturn(List.of(new CountryResultTypeCount("CN", "China", 80, 15, 0, 3)));

        assertThat(mvc.get().uri("/api/countries")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].code").isEqualTo("CN");

        assertThat(mvc.get().uri("/api/countries")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(80);
    }

    @Test
    void topUrlsSplitReturnsJson() {
        when(dashboardService.topUrlsByResultType(any(Instant.class), any(Instant.class), anyInt(), eq(false)))
                .thenReturn(List.of(new NameResultTypeCount("/feed.xml", 40, 10, 0, 2)));

        assertThat(mvc.get().uri("/api/top-urls-split")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("/feed.xml");

        assertThat(mvc.get().uri("/api/top-urls-split")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(40);
    }

    @Test
    void requestsPerDayReturnsJson() {
        when(dashboardService.requestsPerDay(any(Instant.class), any(Instant.class), eq(false)))
                .thenReturn(List.of(new DailyResultTypeCount(LocalDate.of(2026, Month.JANUARY, 15), 80, 20, 3, 5)));

        assertThat(mvc.get().uri("/api/requests-per-day")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].day").isEqualTo("2026-01-15");

        assertThat(mvc.get().uri("/api/requests-per-day")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(80);

        assertThat(mvc.get().uri("/api/requests-per-day")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].miss").isEqualTo(20);
    }

    @Test
    void invalidFromToReturns400() {
        assertThat(mvc.get().uri("/api/ua-names-split")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingParamsReturns400() {
        assertThat(mvc.get().uri("/api/ua-names-split").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void edgeLocationsReturnsJson() {
        when(dashboardService.topEdgeLocations(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameCount("Paris (CDG)", 120)));

        assertThat(mvc.get().uri("/api/edge-locations")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Paris (CDG)");
    }

    @Test
    void referersReturnsJson() {
        when(dashboardService.topReferers(any(Instant.class), any(Instant.class), anyInt(), eq(false)))
                .thenReturn(List.of(new NameCount("https://example.com", 55)));

        assertThat(mvc.get().uri("/api/referers")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("https://example.com");
    }

    @Test
    void unexpectedErrorReturns500() {
        when(dashboardService.uaGroupCounts(any(Instant.class), any(Instant.class), eq(false)))
                .thenThrow(new RuntimeException("db failure"));

        assertThat(mvc.get().uri("/api/ua-groups")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void missingRequiredParamReturns400() {
        // 'from' is required — omitting it triggers MissingServletRequestParameterException
        // which GlobalExceptionHandler.handleMissingParam converts to 400
        assertThat(mvc.get().uri("/api/ua-groups").param("to", "2026-01-31").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void probableBotsReturnsJson() {
        when(dashboardService.probableBots(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(
                        new NameResultTypeCount("Googlebot/2.1", 100, 30, 10, 10),
                        new NameResultTypeCount("Bingbot/2.0",    80, 20, 10, 10),
                        new NameResultTypeCount("YandexBot/3.0",  60, 15,  5, 10)));

        assertThat(mvc.get().uri("/api/probable-bots")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Googlebot/2.1");

        assertThat(mvc.get().uri("/api/probable-bots")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(100);
    }

    @Test
    void robotsDisobedientReturnsJson() {
        when(robotsService.findDisobedientBots(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new DisobedientBot("Googlebot", 10, 8, 2, 0, 0)));

        assertThat(mvc.get().uri("/api/robots-disobedient")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].userAgent").isEqualTo("Googlebot");
    }

    @Test
    void robotsRefreshReturnsOk() {
        when(robotsService.getRefreshedAt()).thenReturn(java.util.Optional.of("2026-01-01T00:00:00Z"));

        assertThat(mvc.get().uri("/api/robots-refresh").exchange())
                .hasStatusOk()
                .bodyText().contains("OK");
    }

    @Test
    void robotsRefreshReturnsErrorOnException() {
        doThrow(new RuntimeException("timeout")).when(robotsService).refresh();

        assertThat(mvc.get().uri("/api/robots-refresh").exchange())
                .hasStatusOk()
                .bodyText().contains("Error: robots refresh failed, see server logs for details");
    }

    @Test
    void fakeBrowsersReturnsJson() {
        when(dashboardService.fakeBrowserUas(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new FakeBrowserUa("Mozilla/5.0 Chrome/70", 17983, 24, 67)));

        assertThat(mvc.get().uri("/api/fake-browsers")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].activeHours").isEqualTo(24);
    }

    @Test
    void browserConfigReturnsJson() {
        when(dashboardService.browserConfigFetches(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameResultTypeCount("Mozilla/5.0 Chrome/103", 40, 10, 4, 2)));

        assertThat(mvc.get().uri("/api/browser-config")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(40);
    }

    @Test
    void burstIpsReturnsJson() {
        when(dashboardService.burstIps(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new BurstIp("20.203.183.116", 815, 815, "US")));

        assertThat(mvc.get().uri("/api/burst-ips")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].clientIp").isEqualTo("20.203.183.116");
    }

    @Test
    void ipInfoReturnsJson() {
        when(ipInfoService.lookup("1.2.3.4"))
                .thenReturn(new IpInfoService.IpInfo("1.2.3.4", "host.example.com", "AS1 Acme", "Paris", "FR"));

        assertThat(mvc.get().uri("/api/ip-info/1.2.3.4").exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$.hostname").isEqualTo("host.example.com");
    }
}