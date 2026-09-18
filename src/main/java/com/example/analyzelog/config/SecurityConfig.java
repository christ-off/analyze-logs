package com.example.analyzelog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        try {
            http
                // No authentication — all requests permitted
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

                // Session required for CSRF token storage across the POST → redirect
                .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // CSRF enabled (default) — protects POST /refresh
                // Thymeleaf injects tokens automatically via th:action

                // No form login or HTTP Basic
                .formLogin(fl -> fl.disable())
                .httpBasic(hb -> hb.disable())

                // Resolve the (by default lazily-loaded) CSRF token right away, before the
                // Thymeleaf view starts streaming the response body. Without this, the token
                // is only loaded when Thymeleaf's th:action processor first reaches a <form>
                // tag deep in the page (e.g. in the toolbar fragment); by then the sidebar nav
                // markup rendered ahead of it can already have filled Tomcat's output buffer
                // and committed the response, so creating the session to store the token fails
                // with "Cannot create a session after the response has been committed".
                .addFilterAfter(csrfTokenEagerLoadFilter(), CsrfFilter.class);

            return http.build();
        } catch (Exception e) {
            throw new IllegalStateException("Security filter chain configuration failed", e);
        }
    }

    private OncePerRequestFilter csrfTokenEagerLoadFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                             HttpServletResponse response,
                                             FilterChain filterChain) throws ServletException, IOException {
                CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (csrfToken != null) {
                    csrfToken.getToken();
                }
                filterChain.doFilter(request, response);
            }
        };
    }
}