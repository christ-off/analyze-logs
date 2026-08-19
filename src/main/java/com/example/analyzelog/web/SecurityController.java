package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.CountryCount;
import com.example.analyzelog.model.DailyCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/security")
class SecurityController extends DetailControllerBase {

    public SecurityController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/traffic-categories")
    public List<NameCount> trafficCategories(@RequestParam String from, @RequestParam String to) {
        var range = requestRange(null, from, to);
        return dashboardService.securityTrafficCategories(range.from(), range.to());
    }

    @GetMapping("/top-countries")
    public List<CountryCount> topCountries(@RequestParam String from, @RequestParam String to) {
        var range = requestRange(null, from, to);
        return dashboardService.securityTopCountries(range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/requests-per-day")
    public List<DailyCount> requestsPerDay(@RequestParam String from, @RequestParam String to) {
        var range = requestRange(null, from, to);
        return dashboardService.securityRequestsPerDay(range.from(), range.to());
    }
}
