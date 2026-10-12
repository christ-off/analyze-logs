---
type: C4 Component
title: Log repository
status: stable
groma:
  id: logrepository
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/repository/LogRepository.java
      symbol: LogRepository
description: Persists and queries imported log rows.
---

JdbcTemplate access to the cloudfront_logs table in SQLite.
