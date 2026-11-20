package com.example.analyzelog.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FeedReadersController extends DateRangeController {

    @GetMapping("/feed-readers")
    public String feedReaders(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        addDateAttributes(model, resolveRange(range, from, to), resolveActiveRange(range, from, to));
        return "feed-readers";
    }
}
