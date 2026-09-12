package com.example.analyzelog.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SocialReferralsController extends DateRangeController {

    @GetMapping("/social-referrals")
    public String socialReferrals(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        addDateAttributes(model, resolveRange(range, from, to), resolveActiveRange(range, from, to));
        return "social-referrals";
    }
}
