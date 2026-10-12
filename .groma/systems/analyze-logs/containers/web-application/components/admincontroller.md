---
type: C4 Component
title: Admin
status: stable
groma:
  id: admincontroller
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/AdminController.java
      symbol: AdminController
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/AdminService.java
      symbol: AdminService
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/StaticUaEntry.java
      symbol: StaticUaEntry
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/StaticRefererEntry.java
      symbol: StaticRefererEntry
description: Maintains user-agent and referer classification rules.
---

Admin page and service editing the static_ua and static_referer tables, then reloading the in-memory classifiers.
