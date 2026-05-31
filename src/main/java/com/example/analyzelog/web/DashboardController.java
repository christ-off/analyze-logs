package com.example.analyzelog.web;

import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController extends DateRangeController {

    @GetMapping("/")
    public String dashboard(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        addDateAttributes(model, resolveRange(range, from, to), resolveActiveRange(range, from, to));
        return "dashboard";
    }

    @GetMapping("/ua-detail")
    public String uaDetail(
            @RequestParam String ua,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        model.addAttribute("uaName", ua);
        addDateAttributes(model, resolveRange(range, from, to), resolveActiveRange(range, from, to));
        return "ua-detail";
    }

    @GetMapping("/country-detail")
    public String countryDetail(
            @RequestParam String country,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        String displayName = Locale.of("", country).getDisplayCountry(Locale.ENGLISH);
        model.addAttribute("countryCode", country);
        model.addAttribute("countryName", displayName.isBlank() ? country : displayName);
        addDateAttributes(model, resolveRange(range, from, to), resolveActiveRange(range, from, to));
        return "country-detail";
    }

    @GetMapping("/url-detail")
    public String urlDetail(
            @RequestParam String url,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        model.addAttribute("urlName", url);
        addDateAttributes(model, resolveRange(range, from, to), resolveActiveRange(range, from, to));
        return "url-detail";
    }

}