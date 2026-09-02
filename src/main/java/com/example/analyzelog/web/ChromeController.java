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
@RequestMapping("/api/chrome")
class ChromeController extends DetailControllerBase {

    public ChromeController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = range(from, to);
        return dashboardService.chromeRawUserAgents(range.from(), range.to(), excludeBots);
    }

    @GetMapping("/human-traffic")
    public List<NameHumanTrafficStats> humanTraffic(
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = range(from, to);
        return dashboardService.chromeHumanTraffic(range.from(), range.to(), excludeBots);
    }

    @GetMapping("/result-types")
    public List<NameCount> resultTypes(
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = range(from, to);
        return dashboardService.chromeResultTypes(range.from(), range.to(), excludeBots);
    }

    @GetMapping("/countries")
    public List<NameCount> countries(
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = range(from, to);
        return dashboardService.chromeCountries(range.from(), range.to(), excludeBots);
    }

    @GetMapping("/uri-stems")
    public List<NameResultTypeCount> uriStems(
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = range(from, to);
        return dashboardService.chromeUrlsByResultType(range.from(), range.to(), appProperties.topUrlsLimit(), excludeBots);
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = range(from, to);
        return dashboardService.chromeRequestsPerDay(range.from(), range.to(), excludeBots);
    }
}
