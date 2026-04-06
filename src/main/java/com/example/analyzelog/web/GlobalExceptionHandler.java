package com.example.analyzelog.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_VIEW = "error";

    /**
     * Invalid date range or other application-level bad request.
     * Sets 400 explicitly on the response before returning the error view.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBadRequest(IllegalArgumentException ex,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        log.warn("Bad request [{}]: {}", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ERROR_VIEW;
    }

    /**
     * Missing required query parameter (e.g. from/to on API endpoints).
     * Previously handled by ResponseEntityExceptionHandler; now explicit.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public String handleMissingParam(MissingServletRequestParameterException ex,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        log.warn("Missing parameter [{}]: {}", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ERROR_VIEW;
    }

    /**
     * Unexpected errors.
     * POST errors redirect with a flash message; GET errors render the error page.
     */
    @ExceptionHandler(Exception.class)
    public String handleError(Exception ex,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              RedirectAttributes redirectAttributes) {
        log.error("Unhandled error [{}]", request.getRequestURI(), ex);
        if (request.getMethod().equalsIgnoreCase("POST")) {
            redirectAttributes.addFlashAttribute("flashError",
                "Operation failed: " + ex.getMessage());
            return "redirect:/";
        }
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ERROR_VIEW;
    }
}