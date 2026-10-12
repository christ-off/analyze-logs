---
type: C4 Component
title: JSON API controller
status: stable
groma:
  id: apicontroller
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/ApiController.java
      symbol: ApiController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/BrowserController.java
      symbol: BrowserController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/Browser.java
      symbol: Browser
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/GlobalExceptionHandler.java
      symbol: GlobalExceptionHandler
description: Cross-page JSON endpoints and error handling.
---

Serves shared endpoints such as country stats, human counts, social networks, IP info and per-browser data, and maps exceptions to error responses.
