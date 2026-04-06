package com.example.analyzelog.web;

import com.example.analyzelog.model.DateRange;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String dashboard(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model,
            RedirectAttributes redirectAttributes) {

        DateRange dateRange = resolveRange(range, from, to);
        model.addAttribute("from", dateRange.fromIso());
        model.addAttribute("to", dateRange.toIso());
        model.addAttribute("fromDate", dateRange.fromDate().toString());
        model.addAttribute("toDate", dateRange.toDate().toString());
        String activeRange = (from != null && to != null) ? "custom" : (range != null ? range : "7d");
        model.addAttribute("activeRange", activeRange);
        return "dashboard";
    }

    private DateRange resolveRange(String range, String from, String to) {
        if (from != null && to != null) return DateRange.fromParams(from, to);
        return switch (range != null ? range : "7d") {
            case "1d"  -> DateRange.lastDays(1);
            case "30d" -> DateRange.lastDays(30);
            case "3m"  -> DateRange.lastMonths(3);
            default    -> DateRange.lastDays(7);
        };
    }
}