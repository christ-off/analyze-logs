package com.example.analyzelog.web;

import com.example.analyzelog.model.CountryCount;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.DateRange;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
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

    @GetMapping("/ua-names-split")
    public List<NameResultTypeCount> uaNames(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topUserAgentsByResultType(range.from(), range.to(), TOP_LIMIT);
    }

    @GetMapping("/countries")
    public List<CountryCount> countries(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topBlockedCountries(range.from(), range.to(), TOP_LIMIT);
    }

    @GetMapping("/top-urls-split")
    public List<NameResultTypeCount> topUrlsSplit(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topUrlsByResultType(range.from(), range.to(), TOP_LIMIT);
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.requestsPerDay(range.from(), range.to());
    }

    @GetMapping("/edge-locations")
    public List<NameCount> edgeLocations(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topEdgeLocations(range.from(), range.to(), TOP_LIMIT);
    }

    @GetMapping("/referers")
    public List<NameCount> referers(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topReferers(range.from(), range.to(), TOP_LIMIT);
    }
}