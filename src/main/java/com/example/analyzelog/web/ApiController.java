package com.example.analyzelog.web;

import com.example.analyzelog.model.DailyStatusCount;
import com.example.analyzelog.model.DateRange;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final int TOP_LIMIT = 10;

    private final DashboardService dashboardService;

    public ApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/ua-names")
    public List<NameCount> uaNames(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topUserAgents(range.from(), range.to(), TOP_LIMIT);
    }

    @GetMapping("/countries")
    public List<NameCount> countries(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topBlockedCountries(range.from(), range.to(), TOP_LIMIT);
    }

    @GetMapping("/uri-stems")
    public List<NameCount> uriStems(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topAllowedUriStems(range.from(), range.to(), TOP_LIMIT);
    }

    @GetMapping("/requests-per-day")
    public List<DailyStatusCount> requestsPerDay(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.requestsPerDay(range.from(), range.to());
    }
}