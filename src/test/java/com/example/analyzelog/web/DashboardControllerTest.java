package com.example.analyzelog.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rootReturns200() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    void defaultRangeIs7Days() throws Exception {
        mvc.perform(get("/"))
                .andExpect(model().attribute("activeRange", "7d"));
    }

    @Test
    void rangeParamSetsActiveRange() throws Exception {
        mvc.perform(get("/?range=30d"))
                .andExpect(model().attribute("activeRange", "30d"));
    }

    @Test
    void customDateParamsSetCustomRange() throws Exception {
        mvc.perform(get("/").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(model().attribute("activeRange", "custom"))
                .andExpect(model().attribute("fromDate", "2026-01-01"))
                .andExpect(model().attribute("toDate", "2026-01-31"));
    }

    @Test
    void invalidDateRangeReturns400() throws Exception {
        mvc.perform(get("/").param("from", "2026-02-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modelContainsFromAndToIso() throws Exception {
        mvc.perform(get("/?range=1d"))
                .andExpect(model().attributeExists("from", "to"))
                .andExpect(model().attribute("from", not(emptyString())))
                .andExpect(model().attribute("to",   not(emptyString())));
    }
}