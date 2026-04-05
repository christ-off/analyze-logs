package com.example.analyzelog.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Application-level bad requests (e.g. invalid date range).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request [{}]: {}", request.getRequestURI(), ex.getMessage());
        return "error";
    }

    /**
     * Unexpected errors on POST endpoints — redirect with flash rather than showing an error page.
     */
    @ExceptionHandler(Exception.class)
    public String handlePostError(Exception ex, HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        log.error("Unhandled error [{}]", request.getRequestURI(), ex);
        if (request.getMethod().equalsIgnoreCase("POST")) {
            redirectAttributes.addFlashAttribute("flashError",
                "Operation failed: " + ex.getMessage());
            return "redirect:/";
        }
        return "error";
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.debug("Spring MVC exception [{}]: {}", status, ex.getMessage());
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }
}