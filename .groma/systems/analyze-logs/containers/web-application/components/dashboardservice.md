---
type: C4 Component
title: Dashboard service
status: stable
groma:
  id: dashboardservice
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/DashboardService.java
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/ResultTypeSql.java
      symbol: ResultTypeSql
description: Builds the aggregated statistics shown on every page.
---

Queries the log tables with SQL (hit/miss/function/error counting) and returns typed results for charts and tables.
