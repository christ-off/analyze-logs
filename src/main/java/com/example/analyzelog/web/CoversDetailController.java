package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.CoverUserAgent;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/covers")
class CoversDetailController extends DetailControllerBase {

    public CoversDetailController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(@RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.coverUserAgents(range.from(), range.to(), appProperties.topDetailLimit());
    }

    @GetMapping("/user-agent-table")
    public List<CoverUserAgent> userAgentTable(@RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.coverUserAgentTable(range.from(), range.to(), appProperties.topDetailLimit());
    }

    @GetMapping("/referer-split")
    public List<NameCount> refererSplit(@RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.coverRefererSplit(range.from(), range.to());
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(@RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.coverRequestsPerDay(range.from(), range.to());
    }
}
