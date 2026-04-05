package com.example.analyzelog.config;

import com.example.analyzelog.service.UserAgentClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AppConfig {

    @Bean
    public S3Client s3Client(AppProperties props) {
        var aws = props.aws();
        String region = (aws.region() != null && !aws.region().isBlank())
                ? aws.region() : "us-east-1";
        var credProv = (aws.profile() != null && !aws.profile().isBlank())
                ? ProfileCredentialsProvider.create(aws.profile())
                : DefaultCredentialsProvider.create();
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credProv)
                .build();
    }

    @Bean
    public UserAgentClassifier userAgentClassifier(UaClassifierProperties props) {
        return new UserAgentClassifier(props);
    }
}