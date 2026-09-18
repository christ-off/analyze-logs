package com.example.analyzelog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

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

                // CSRF enabled (default) — protects POST /refresh.
                // Thymeleaf injects tokens automatically via th:action, but it only reads the
                // token when its th:action processor reaches a <form> deep in the page; by then
                // the markup rendered ahead of it can already have committed the response, so
                // creating the session to store the token fails with "Cannot create a session
                // after the response has been committed". Opting out of deferred loading (a null
                // request-attribute name) resolves the token before the view renders.
                .csrf(csrf -> csrf.csrfTokenRequestHandler(eagerCsrfTokenRequestHandler()))

                // No form login or HTTP Basic
                .formLogin(fl -> fl.disable())
                .httpBasic(hb -> hb.disable());

            return http.build();
        } catch (Exception e) {
            throw new IllegalStateException("Security filter chain configuration failed", e);
        }
    }

    private XorCsrfTokenRequestAttributeHandler eagerCsrfTokenRequestHandler() {
        var handler = new XorCsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
