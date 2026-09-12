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
@RequestMapping("/api/edge")
class EdgeController extends DetailControllerBase {

    public EdgeController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.edgeRawUserAgents(range.from(), range.to());
    }

    @GetMapping("/human-traffic")
    public List<NameHumanTrafficStats> humanTraffic(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.edgeHumanTraffic(range.from(), range.to());
    }

    @GetMapping("/result-types")
    public List<NameCount> resultTypes(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.edgeResultTypes(range.from(), range.to());
    }

    @GetMapping("/countries")
    public List<NameCount> countries(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.edgeCountries(range.from(), range.to());
    }

    @GetMapping("/uri-stems")
    public List<NameResultTypeCount> uriStems(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.edgeUrlsByResultType(range.from(), range.to(), appProperties.topUrlsLimit());
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.edgeRequestsPerDay(range.from(), range.to());
    }
}
