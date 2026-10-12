---
type: C4 Component
title: Refresh
status: stable
groma:
  id: refresh
  parent: dashboard-frontend
  code:
    - scanner: javascript
      file: src/main/resources/static/js/refresh.js
      symbol: initRefresh
description: Starts S3 refresh and shows progress.
---

Posts to /refresh and polls /refresh/progress.

## Derived relationships

| Source | Target | Description | Technology |
| --- | --- | --- | --- |
| [src/main/resources/static/js/refresh.js](../../../../../../src/main/resources/static/js/refresh.js) | [src/main/java/com/example/analyzelog/web/RefreshController.java](../../../../../../src/main/java/com/example/analyzelog/web/RefreshController.java) | Calls HTTP endpoints: GET /refresh/progress, POST /refresh | java, javascript |
