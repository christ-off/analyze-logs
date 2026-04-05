package com.example.analyzelog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.example.analyzelog.config")
public class AnalyzeLogsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyzeLogsApplication.class, args);
    }
}