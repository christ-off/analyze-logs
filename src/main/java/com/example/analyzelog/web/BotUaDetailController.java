package com.example.analyzelog.web;

import com.example.analyzelog.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class BotUaDetailController extends DateRangeController {

    private final DashboardService dashboardService;

    public BotUaDetailController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/bot-ua-detail")
    public String botUaDetail(
            @RequestParam String ua,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        var dateRange = resolveRange(range, from, to);
        addDateAttributes(model, dateRange, resolveActiveRange(range, from, to));
        model.addAttribute("ua", ua);
        model.addAttribute("requests", dashboardService.requestsByUserAgent(ua, dateRange.from(), dateRange.to()));
        return "bot-ua-detail";
    }
}
