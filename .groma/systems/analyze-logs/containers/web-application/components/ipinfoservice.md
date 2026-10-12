---
type: C4 Component
title: IP info service
status: stable
groma:
  id: ipinfoservice
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/IpInfoService.java
description: Looks up owner and location of an IP.
---

Tries several public lookup services in order and caches results.

## Relationships

| Source | Target | Description | Technology |
| --- | --- | --- | --- |
| [ipinfoservice](ipinfoservice.md) | [ip-lookup-services](../../../../../externals/ip-lookup-services.md) | Looks up IP owner | HTTPS |
