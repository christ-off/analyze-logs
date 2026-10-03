package com.example.analyzelog.web;

import com.example.analyzelog.model.IpRequest;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(IpRequestsController.class)
class IpRequestsControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void rendersRequestsForIp() {
        when(dashboardService.requestsByIp(eq("1.2.3.4"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new IpRequest(Instant.parse("2026-01-15T10:00:00Z"), "1.2.3.4",
                        "Googlebot", "France", "Mozilla/5.0 Googlebot", "/robots.txt", "Hit", 200)));

        assertThat(mvc.get().uri("/ip-requests").param("ip", "1.2.3.4").exchange())
                .hasStatusOk()
                .bodyText().contains("Googlebot", "/robots.txt", "France", "Mozilla/5.0 Googlebot");
    }
}
