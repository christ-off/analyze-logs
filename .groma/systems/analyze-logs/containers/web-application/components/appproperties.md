---
type: C4 Component
title: Configuration
status: stable
groma:
  id: appproperties
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/config/AppProperties.java
    - scanner: java
      file: src/main/java/com/example/analyzelog/config/AppConfig.java
      symbol: AppConfig
    - scanner: java
      file: src/main/java/com/example/analyzelog/config/RefererFilterProperties.java
      symbol: RefererFilterProperties
    - scanner: java
      file: src/main/java/com/example/analyzelog/config/UriStemFilterProperties.java
      symbol: UriStemFilterProperties
    - scanner: java
      file: src/main/java/com/example/analyzelog/config/UriStemGroupProperties.java
description: Typed application properties and beans.
---

Binds app, AWS, referer and URI-stem group settings from application.yml and defines the S3 and REST clients.
