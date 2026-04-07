package com.example.analyzelog.web;

import com.example.analyzelog.model.DateRange;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DashboardController {

    private static final String ATTR_FROM_DATE = "fromDate";
    private static final String ATTR_TO_DATE = "toDate";

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
        model.addAttribute(ATTR_FROM_DATE, dateRange.fromDate().toString());
        model.addAttribute(ATTR_TO_DATE, dateRange.toDate().toString());
        String activeRange = resolveActiveRange(range, from, to);
        model.addAttribute("activeRange", activeRange);
        return "dashboard";
    }

    @GetMapping("/ua-detail")
    public String uaDetail(
            @RequestParam String ua,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        DateRange dateRange = resolveRange(range, from, to);
        model.addAttribute("uaName", ua);
        model.addAttribute("from", dateRange.fromIso());
        model.addAttribute("to", dateRange.toIso());
        model.addAttribute(ATTR_FROM_DATE, dateRange.fromDate().toString());
        model.addAttribute(ATTR_TO_DATE, dateRange.toDate().toString());
        model.addAttribute("activeRange", resolveActiveRange(range, from, to));
        return "ua-detail";
    }

    @GetMapping("/country-detail")
    public String countryDetail(
            @RequestParam String country,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        DateRange dateRange = resolveRange(range, from, to);
        String displayName = Locale.of("", country).getDisplayCountry(Locale.ENGLISH);
        model.addAttribute("countryCode", country);
        model.addAttribute("countryName", displayName.isBlank() ? country : displayName);
        model.addAttribute("from", dateRange.fromIso());
        model.addAttribute("to", dateRange.toIso());
        model.addAttribute(ATTR_FROM_DATE, dateRange.fromDate().toString());
        model.addAttribute(ATTR_TO_DATE, dateRange.toDate().toString());
        model.addAttribute("activeRange", resolveActiveRange(range, from, to));
        return "country-detail";
    }

    private String resolveActiveRange(String range, String from, String to) {
        if (from != null && to != null) return "custom";
        return range != null ? range : "7d";
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