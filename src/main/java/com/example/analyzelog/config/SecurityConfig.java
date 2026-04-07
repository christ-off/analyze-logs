package com.example.analyzelog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

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
                .httpBasic(hb -> hb.disable());

            return http.build();
        } catch (Exception e) {
            throw new IllegalStateException("Security filter chain configuration failed", e);
        }
    }
}