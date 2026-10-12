---
type: C4 Component
title: Robots.txt service
status: stable
groma:
  id: robotsservice
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/RobotsService.java
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/RobotsTxtSkippingBot.java
      symbol: RobotsTxtSkippingBot
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/DisobedientBot.java
      symbol: DisobedientBot
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/ObedientBot.java
      symbol: ObedientBot
description: Downloads robots.txt and evaluates bot obedience.
---

Stores robots rules and classifies bots as obedient or disobedient from their requests.

## Relationships

| Source | Target | Description | Technology |
| --- | --- | --- | --- |
| [robotsservice](robotsservice.md) | [site-robots-txt](../../../../../externals/site-robots-txt.md) | Downloads robots.txt | HTTPS |
