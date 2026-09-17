package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/errors-404")
class Errors404DetailController extends DetailControllerBase {

    public Errors404DetailController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/uris")
    public List<NameCount> uris(@RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.errors404UriCounts(range.from(), range.to(), appProperties.topUrlsLimit());
    }
}
