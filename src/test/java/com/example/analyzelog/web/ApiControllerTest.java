package com.example.analyzelog.web;

import com.example.analyzelog.model.DailyStatusCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiController.class)
class ApiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void uaNamesReturnsJson() throws Exception {
        when(dashboardService.topUserAgents(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameCount("Chrome / Windows", 42)));

        mvc.perform(get("/api/ua-names").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].name").value("Chrome / Windows"))
                .andExpect(jsonPath("$[0].count").value(42));
    }

    @Test
    void countriesReturnsJson() throws Exception {
        when(dashboardService.topBlockedCountries(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameCount("CN", 100)));

        mvc.perform(get("/api/countries").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("CN"))
                .andExpect(jsonPath("$[0].count").value(100));
    }

    @Test
    void uriStemsReturnsJson() throws Exception {
        when(dashboardService.topAllowedUriStems(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(new NameCount("/feed.xml", 55)));

        mvc.perform(get("/api/uri-stems").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("/feed.xml"))
                .andExpect(jsonPath("$[0].count").value(55));
    }

    @Test
    void requestsPerDayReturnsJson() throws Exception {
        when(dashboardService.requestsPerDay(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new DailyStatusCount(LocalDate.of(2026, 1, 15), 80, 15, 5)));

        mvc.perform(get("/api/requests-per-day").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].day").value("2026-01-15"))
                .andExpect(jsonPath("$[0].success").value(80))
                .andExpect(jsonPath("$[0].clientError").value(15))
                .andExpect(jsonPath("$[0].serverError").value(5));
    }

    @Test
    void invalidFromToReturns400() throws Exception {
        mvc.perform(get("/api/ua-names").param("from", "2026-02-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingParamsReturns400() throws Exception {
        mvc.perform(get("/api/ua-names"))
                .andExpect(status().isBadRequest());
    }
}