package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

@WebMvcTest(AsnDetailController.class)
@EnableConfigurationProperties(AppProperties.class)
class AsnDetailControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void uaSplitReturnsJson() {
        when(dashboardService.asnTopUserAgentsByResultType(eq("24940"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameResultTypeCount("Miniflux", 50, 10, 0, 2)));

        assertThat(mvc.get().uri("/api/asn-detail/ua-split")
                .param("asn", "24940")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Miniflux");

        assertThat(mvc.get().uri("/api/asn-detail/ua-split")
                .param("asn", "24940")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(50);
    }

    @Test
    void urlSplitReturnsJson() {
        when(dashboardService.asnUrlsByResultType(eq("24940"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameResultTypeCount("/feed.xml", 40, 10, 0, 2)));

        assertThat(mvc.get().uri("/api/asn-detail/url-split")
                .param("asn", "24940")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("/feed.xml");

        assertThat(mvc.get().uri("/api/asn-detail/url-split")
                .param("asn", "24940")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(40);
    }

    @Test
    void requestsPerDayReturnsJson() {
        when(dashboardService.asnRequestsPerDay(eq("24940"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new DailyResultTypeCount(LocalDate.of(2026, Month.JANUARY, 15), 10, 2, 0, 0)));

        assertThat(mvc.get().uri("/api/asn-detail/requests-per-day")
                .param("asn", "24940")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(10);
    }
}
