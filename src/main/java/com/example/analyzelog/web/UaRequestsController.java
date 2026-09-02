package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ua-requests")
class UaRequestsController extends DetailControllerBase {

    public UaRequestsController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String ua,
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.requestsPerDayByUserAgent(ua, range.from(), range.to());
    }
}