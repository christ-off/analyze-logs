package com.example.analyzelog.config;

import com.example.analyzelog.service.UaClassifierRule;
import com.example.analyzelog.service.UserAgentClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

@Configuration
public class AppConfig {

    @Bean
    public S3Client s3Client(AppProperties props) {
        var aws = props.aws();
        String region = (aws.region() != null && !aws.region().isBlank())
                ? aws.region() : "us-east-1";
        var credProv = (aws.profile() != null && !aws.profile().isBlank())
                ? ProfileCredentialsProvider.create(aws.profile())
                : DefaultCredentialsProvider.builder().build();
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credProv)
                .build();
    }

    @Bean
    @DependsOn("liquibase")
    public UserAgentClassifier userAgentClassifier(JdbcTemplate jdbc) {
        List<UaClassifierRule> rules = jdbc.query(
                "SELECT pattern, ua_name FROM static_ua_classifier ORDER BY sort_order",
                (rs, _) -> new UaClassifierRule(rs.getString("pattern"), rs.getString("ua_name")));
        return new UserAgentClassifier(rules);
    }
}
