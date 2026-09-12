package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameHumanTrafficStats;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/firefox")
class FirefoxController extends DetailControllerBase {

    public FirefoxController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.firefoxRawUserAgents(range.from(), range.to());
    }

    @GetMapping("/human-traffic")
    public List<NameHumanTrafficStats> humanTraffic(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.firefoxHumanTraffic(range.from(), range.to());
    }

    @GetMapping("/result-types")
    public List<NameCount> resultTypes(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.firefoxResultTypes(range.from(), range.to());
    }

    @GetMapping("/countries")
    public List<NameCount> countries(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.firefoxCountries(range.from(), range.to());
    }

    @GetMapping("/uri-stems")
    public List<NameResultTypeCount> uriStems(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.firefoxUrlsByResultType(range.from(), range.to(), appProperties.topUrlsLimit());
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.firefoxRequestsPerDay(range.from(), range.to());
    }
}
