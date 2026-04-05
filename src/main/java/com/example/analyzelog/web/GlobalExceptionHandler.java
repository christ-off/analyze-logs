package com.example.analyzelog.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request [{}]: {}", request.getRequestURI(), ex.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericError(Exception ex, HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        log.error("Unhandled error [{}]", request.getRequestURI(), ex);
        if (request.getMethod().equalsIgnoreCase("POST")) {
            redirectAttributes.addFlashAttribute("flashError",
                "Operation failed: " + ex.getMessage());
            return "redirect:/";
        }
        return "error";
    }
}