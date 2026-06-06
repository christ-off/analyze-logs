package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.BotUaRequest;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(BotUaDetailController.class)
@EnableConfigurationProperties(AppProperties.class)
class BotUaDetailControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    private static final String UA = "Googlebot/2.1";

    @Test
    void defaultRangeRendersPage() {
        when(dashboardService.requestsByUserAgent(eq(UA), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        assertThat(mvc.get().uri("/ua-requests").param("ua", UA).exchange())
                .hasStatusOk()
                .model().containsEntry("activeRange", "7d")
                         .containsEntry("ua", UA);
    }

    @Test
    void customRangeSetsDateAttributes() {
        when(dashboardService.requestsByUserAgent(eq(UA), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        assertThat(mvc.get().uri("/ua-requests")
                .param("ua", UA).param("from", "2026-01-01").param("to", "2026-01-31").exchange())
                .hasStatusOk()
                .model().containsEntry("activeRange", "custom")
                         .containsEntry("fromDate", "2026-01-01")
                         .containsEntry("toDate", "2026-01-31");
    }

    @Test
    void requestsArePassedToModel() {
        var request = new BotUaRequest(Instant.parse("2026-01-15T10:00:00Z"), "1.2.3.4", "/index.html", "Hit", "France");
        when(dashboardService.requestsByUserAgent(eq(UA), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(request));

        assertThat(mvc.get().uri("/ua-requests")
                .param("ua", UA).param("from", "2026-01-01").param("to", "2026-01-31").exchange())
                .hasStatusOk()
                .model().containsEntry("requests", List.of(request));
    }

    @Test
    void missingUaParamReturns400() {
        assertThat(mvc.get().uri("/ua-requests")
                .param("from", "2026-01-01").param("to", "2026-01-31").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
