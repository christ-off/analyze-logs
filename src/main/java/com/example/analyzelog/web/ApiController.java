package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.BotHumanDailyCount;
import com.example.analyzelog.model.BurstIp;
import com.example.analyzelog.model.FakeBrowserUa;
import com.example.analyzelog.model.CountryResultTypeCount;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.DateRange;
import com.example.analyzelog.model.DisobedientBot;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import com.example.analyzelog.service.IpInfoService;
import com.example.analyzelog.service.RobotsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final DashboardService dashboardService;
    private final AppProperties appProperties;
    private final RobotsService robotsService;
    private final IpInfoService ipInfoService;

    public ApiController(DashboardService dashboardService, AppProperties appProperties,
                         RobotsService robotsService, IpInfoService ipInfoService) {
        this.dashboardService = dashboardService;
        this.appProperties = appProperties;
        this.robotsService = robotsService;
        this.ipInfoService = ipInfoService;
    }

    @GetMapping("/ua-groups")
    public List<NameCount> uaGroups(@RequestParam String from, @RequestParam String to,
                                    @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaGroupCounts(range.from(), range.to(), excludeBots);
    }

    @GetMapping("/ua-names-split")
    public List<NameResultTypeCount> uaNames(@RequestParam String from, @RequestParam String to,
                                             @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topUserAgentsByResultType(range.from(), range.to(), appProperties.topLimit(), excludeBots);
    }

    @GetMapping("/countries")
    public List<CountryResultTypeCount> countries(@RequestParam String from, @RequestParam String to,
                                                  @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topCountriesByResultType(range.from(), range.to(), appProperties.topLimit(), excludeBots);
    }

    @GetMapping("/top-urls-split")
    public List<NameResultTypeCount> topUrlsSplit(@RequestParam String from, @RequestParam String to,
                                                  @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topUrlsByResultType(range.from(), range.to(), appProperties.topUrlsLimit(), excludeBots);
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(@RequestParam String from, @RequestParam String to,
                                                     @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.requestsPerDay(range.from(), range.to(), excludeBots);
    }

    @GetMapping("/edge-locations")
    public List<NameCount> edgeLocations(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topEdgeLocations(range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/platforms")
    public List<NameCount> platforms(@RequestParam String from, @RequestParam String to,
                                     @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.platformCounts(range.from(), range.to(), excludeBots);
    }

    @GetMapping("/referers")
    public List<NameCount> referers(@RequestParam String from, @RequestParam String to,
                                    @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topReferers(range.from(), range.to(), appProperties.topReferersLimit(), excludeBots);
    }

    @GetMapping("/top-bots")
    public List<NameResultTypeCount> topBots(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topBots(range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/probable-bots")
    public List<NameResultTypeCount> probableBots(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.probableBots(range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/bot-human-daily")
    public List<BotHumanDailyCount> botHumanDaily(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.botHumanDailyCounts(range.from(), range.to());
    }

    @GetMapping("/fake-browsers")
    public List<FakeBrowserUa> fakeBrowsers(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.fakeBrowserUas(range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/browser-config")
    public List<NameCount> browserConfig(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.browserConfigFetches(range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/burst-ips")
    public List<BurstIp> burstIps(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.burstIps(range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/robots-disobedient")
    public List<DisobedientBot> robotsDisobedient(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return robotsService.findDisobedientBots(range.from(), range.to());
    }

    @GetMapping("/ip-info/{ip}")
    public IpInfoService.IpInfo ipInfo(@PathVariable String ip) {
        return ipInfoService.lookup(ip);
    }

    @GetMapping("/robots-refresh")
    public String robotsRefresh() {
        try {
            robotsService.refresh();
            return "OK — refreshed at " + robotsService.getRefreshedAt().orElse("?");
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}