---
type: C4 Component
title: Security config
status: stable
groma:
  id: securityconfig
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/config/SecurityConfig.java
      symbol: SecurityConfig
description: Spring Security setup with CSRF enabled.
---

Protects the POST refresh endpoint with CSRF; there is no authentication.
