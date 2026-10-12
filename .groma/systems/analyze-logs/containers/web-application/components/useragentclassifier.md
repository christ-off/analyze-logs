---
type: C4 Component
title: Classifiers
status: stable
groma:
  id: useragentclassifier
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/UserAgentClassifier.java
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/ReloadableClassifierService.java
      symbol: ReloadableClassifierService
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/UaClassifierRule.java
      symbol: UaClassifierRule
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/ReloadableRefererService.java
      symbol: ReloadableRefererService
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/RefererRule.java
      symbol: RefererRule
description: Classifies user agents and referers into labels.
---

Rules loaded from the database map user-agent and referer strings to names; reloadable after admin edits.
