package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/category-detail")
class CategoryDetailController extends DetailControllerBase {

    public CategoryDetailController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/url-split")
    public List<NameResultTypeCount> urlSplit(
            @RequestParam String category,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = requestRange(null, from, to);
        return dashboardService.categoryUrlsByResultType(category, range.from(), range.to(), appProperties.topUrlsLimit(), excludeBots);
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(
            @RequestParam String category,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = requestRange(null, from, to);
        return dashboardService.categoryTopUserAgentsByResultType(category, range.from(), range.to(), appProperties.topDetailLimit(), excludeBots);
    }
}
