package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyFeedSubscribers;
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

@WebMvcTest(FeedReadersDetailController.class)
@EnableConfigurationProperties(AppProperties.class)
class FeedReadersDetailControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void userAgentsReturnsJson() {
        when(dashboardService.feedReaderUserAgents(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameResultTypeCount("Miniflux", 50, 10, 0, 2)));

        assertThat(mvc.get().uri("/api/feed-readers/user-agents")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Miniflux");

        assertThat(mvc.get().uri("/api/feed-readers/user-agents")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].hit").isEqualTo(50);
    }

    @Test
    void subscribersPerDayReturnsJson() {
        when(dashboardService.feedSubscribersPerDay(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new DailyFeedSubscribers(LocalDate.of(2026, Month.JANUARY, 15), "Feedly", 7)));

        assertThat(mvc.get().uri("/api/feed-readers/subscribers-per-day")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].count").isEqualTo(7);
    }
}
