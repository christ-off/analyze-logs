package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai-bots")
class AiBotsController extends DetailControllerBase {

    public AiBotsController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(@RequestParam String from, @RequestParam String to) {
        var range = requestRange(null, from, to);
        return dashboardService.aiBotsUserAgents(range.from(), range.to(), appProperties.topDetailLimit());
    }

    @GetMapping("/urls")
    public List<NameResultTypeCount> urls(@RequestParam String from, @RequestParam String to) {
        var range = requestRange(null, from, to);
        return dashboardService.aiBotsUrls(range.from(), range.to(), appProperties.topUrlsLimit());
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(@RequestParam String from, @RequestParam String to) {
        var range = requestRange(null, from, to);
        return dashboardService.aiBotsRequestsPerDay(range.from(), range.to());
    }
}
