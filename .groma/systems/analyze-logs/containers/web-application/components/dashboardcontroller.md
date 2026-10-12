---
type: C4 Component
title: Page and detail controllers
status: stable
groma:
  id: dashboardcontroller
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/DashboardController.java
      symbol: DashboardController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/DateRangeController.java
      symbol: DateRangeController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/SecurityController.java
      symbol: SecurityController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/CountriesController.java
      symbol: CountriesController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/CountryDetailController.java
      symbol: CountryDetailController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/UrlDetailController.java
      symbol: UrlDetailController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/RefererDetailController.java
      symbol: RefererDetailController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/SocialReferralsController.java
      symbol: SocialReferralsController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/DetailControllerBase.java
      symbol: DetailControllerBase
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/ZipRequestsController.java
      symbol: ZipRequestsController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/ZipRequestsDetailController.java
      symbol: ZipRequestsDetailController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/CoversController.java
      symbol: CoversController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/CoversDetailController.java
      symbol: CoversDetailController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/IpRequestsController.java
      symbol: IpRequestsController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/UaRequestsController.java
      symbol: UaRequestsController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/UaDetailController.java
      symbol: UaDetailController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/BotUaDetailController.java
      symbol: BotUaDetailController
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/BotAnalysisController.java
      symbol: BotAnalysisController
description: Thymeleaf pages and chart JSON for every dashboard view.
---

Dashboard, Security, Countries, URL, Referer, UA, Bot analysis, Covers, Zip and IP pages. Each resolves the requested date range through a shared base and asks the Dashboard service for data.

## Relationships

| Source | Target | Description | Technology |
| --- | --- | --- | --- |
| [src/main/java/com/example/analyzelog/web/DashboardController.java](../../../../../../src/main/java/com/example/analyzelog/web/DashboardController.java) | [src/main/java/com/example/analyzelog/service/DashboardService.java](../../../../../../src/main/java/com/example/analyzelog/service/DashboardService.java) | Requests statistics | Java call |
