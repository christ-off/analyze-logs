---
type: C4 Container
title: Web application
status: stable
groma:
  id: web-application
  parent: analyze-logs
  technology: Spring Boot, Thymeleaf, JdbcTemplate, Liquibase
description: Spring Boot server that imports logs, serves pages and JSON APIs.
---

Imports CloudFront logs from S3 into SQLite, applies Liquibase schema migrations at startup, renders Thymeleaf pages and exposes JSON endpoints that the frontend scripts call for chart data.
