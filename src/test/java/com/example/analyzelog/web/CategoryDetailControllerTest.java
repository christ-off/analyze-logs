package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@WebMvcTest(CategoryDetailController.class)
@EnableConfigurationProperties(AppProperties.class)
class CategoryDetailControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void urlSplitReturnsJson() {
        when(dashboardService.categoryUrlsByResultType(eq("Probable human"), any(Instant.class), any(Instant.class), anyInt(), anyBoolean()))
                .thenReturn(List.of(new NameResultTypeCount("/index.html", 40, 10, 0, 2)));

        assertThat(mvc.get().uri("/api/category-detail/url-split")
                .param("category", "Probable human")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("/index.html");
    }

    @Test
    void userAgentsReturnsJson() {
        when(dashboardService.categoryTopUserAgentsByResultType(eq("Declared bots"), any(Instant.class), any(Instant.class), anyInt(), anyBoolean()))
                .thenReturn(List.of(new NameResultTypeCount("Googlebot", 30, 5, 0, 0)));

        assertThat(mvc.get().uri("/api/category-detail/user-agents")
                .param("category", "Declared bots")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Googlebot");
    }

    @Test
    void invalidDateRangeReturns400() {
        assertThat(mvc.get().uri("/api/category-detail/url-split")
                .param("category", "Other")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingCategoryParamReturns400() {
        assertThat(mvc.get().uri("/api/category-detail/url-split")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
