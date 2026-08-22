package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DateRange;
import com.example.analyzelog.service.DashboardService;

abstract class DetailControllerBase {

    protected final DashboardService dashboardService;
    protected final AppProperties appProperties;

    protected DetailControllerBase(DashboardService dashboardService, AppProperties appProperties) {
        this.dashboardService = dashboardService;
        this.appProperties = appProperties;
    }

    protected DateRange range(String from, String to) {
        return DateRange.fromParams(from, to);
    }
}
