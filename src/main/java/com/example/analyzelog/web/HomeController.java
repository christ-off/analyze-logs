package com.example.analyzelog.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HomeController {

    @GetMapping("/")
    public String home() {
        return "dashboard";
    }
}