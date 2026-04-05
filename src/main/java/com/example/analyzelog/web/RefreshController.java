package com.example.analyzelog.web;

import com.example.analyzelog.service.FetchService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RefreshController {

    private final FetchService fetchService;

    public RefreshController(FetchService fetchService) {
        this.fetchService = fetchService;
    }

    @PostMapping("/refresh")
    public String refresh(RedirectAttributes redirectAttributes) {
        var result = fetchService.fetch(null, true);
        redirectAttributes.addFlashAttribute("flashMessage",
            "Refresh complete — fetched: %d, skipped: %d, failed: %d"
                .formatted(result.fetched(), result.skipped(), result.failed()));
        return "redirect:/";
    }
}