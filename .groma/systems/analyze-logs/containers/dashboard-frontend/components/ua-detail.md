---
type: C4 Component
title: Detail pages
status: stable
groma:
  id: ua-detail
  parent: dashboard-frontend
  code:
    - scanner: javascript
      file: src/main/resources/static/js/ua-detail.js
      symbol: loadAllCharts
    - scanner: javascript
      file: src/main/resources/static/js/country-detail.js
      symbol: loadAllCharts
    - scanner: javascript
      file: src/main/resources/static/js/url-detail.js
    - scanner: javascript
      file: src/main/resources/static/js/referer-detail.js
    - scanner: javascript
      file: src/main/resources/static/js/browser.js
    - scanner: javascript
      file: src/main/resources/static/js/browsers.js
description: Scripts for drill-down pages.
---

User agent, country, URL, referer and browser detail charts.

## Derived relationships

| Source | Target | Description | Technology |
| --- | --- | --- | --- |
| [src/main/resources/static/js/referer-detail.js](../../../../../../src/main/resources/static/js/referer-detail.js) | [src/main/java/com/example/analyzelog/web/RefererDetailController.java](../../../../../../src/main/java/com/example/analyzelog/web/RefererDetailController.java) | Calls HTTP endpoint: GET /api/referer-detail/urls | java, javascript |
| [src/main/resources/static/js/ua-detail.js](../../../../../../src/main/resources/static/js/ua-detail.js) | [src/main/java/com/example/analyzelog/web/UaDetailController.java](../../../../../../src/main/java/com/example/analyzelog/web/UaDetailController.java) | Calls HTTP endpoints: GET /api/ua-detail/human-traffic, GET /api/ua-detail/user-agents | java, javascript |
| [src/main/resources/static/js/url-detail.js](../../../../../../src/main/resources/static/js/url-detail.js) | [src/main/java/com/example/analyzelog/web/UrlDetailController.java](../../../../../../src/main/java/com/example/analyzelog/web/UrlDetailController.java) | Calls HTTP endpoint: GET /api/url-detail/urls | java, javascript |
