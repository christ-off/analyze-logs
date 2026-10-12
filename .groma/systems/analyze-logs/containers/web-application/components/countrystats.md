---
type: C4 Component
title: Result models
status: stable
groma:
  id: countrystats
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/CountryStats.java
      symbol: CountryStats
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/BotUaRequest.java
      symbol: BotUaRequest
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/CountryClientCounts.java
      symbol: CountryClientCounts
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/CountryResultTypeCount.java
      symbol: CountryResultTypeCount
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/CoverUserAgent.java
      symbol: CoverUserAgent
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/DailyNameCount.java
      symbol: DailyNameCount
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/DailyResultTypeCount.java
      symbol: DailyResultTypeCount
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/DateRange.java
      symbol: DateRange
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/HumanTrafficStats.java
      symbol: HumanTrafficStats
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/IdentityShift.java
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/IpRequest.java
      symbol: IpRequest
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/NameCount.java
      symbol: NameCount
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/NameHumanTrafficStats.java
      symbol: NameHumanTrafficStats
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/NameResultTypeCount.java
      symbol: NameResultTypeCount
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/SiteConfigFetcher.java
      symbol: SiteConfigFetcher
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/SocialNetworkRequest.java
      symbol: SocialNetworkRequest
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/UnknownUaRequest.java
      symbol: UnknownUaRequest
description: Records carrying query results.
---

Immutable records for counts, date ranges and per-name statistics passed between services and controllers.
