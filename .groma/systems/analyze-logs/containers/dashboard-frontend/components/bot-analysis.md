---
type: C4 Component
title: Analysis pages
status: stable
groma:
  id: bot-analysis
  parent: dashboard-frontend
  code:
    - scanner: javascript
      file: src/main/resources/static/js/bot-analysis.js
    - scanner: javascript
      file: src/main/resources/static/js/human.js
    - scanner: javascript
      file: src/main/resources/static/js/abuse-report.js
    - scanner: javascript
      file: src/main/resources/static/js/security.js
      symbol: loadAllCharts
    - scanner: javascript
      file: src/main/resources/static/js/social-referrals.js
    - scanner: javascript
      file: src/main/resources/static/js/pages/ip-requests.js
      symbol: init
    - scanner: javascript
      file: src/main/resources/static/js/pages/ua-requests.js
    - scanner: javascript
      file: src/main/resources/static/js/pages/zip-requests.js
    - scanner: javascript
      file: src/main/resources/static/js/pages/covers.js
      symbol: loadCoverCharts
    - scanner: javascript
      file: src/main/resources/static/js/pages/countries.js
description: Scripts for list and analysis pages.
---

Bot analysis, human traffic, security, social referrals, countries, covers, IP, UA and zip request pages.

## Derived relationships

| Source | Target | Description | Technology |
| --- | --- | --- | --- |
| [src/main/resources/static/js/bot-analysis.js](../../../../../../src/main/resources/static/js/bot-analysis.js) | [src/main/java/com/example/analyzelog/web/ApiController.java](../../../../../../src/main/java/com/example/analyzelog/web/ApiController.java) | Calls HTTP endpoint: GET /api/robots-refresh | java, javascript |
| [src/main/resources/static/js/pages/countries.js](../../../../../../src/main/resources/static/js/pages/countries.js) | [src/main/java/com/example/analyzelog/web/ApiController.java](../../../../../../src/main/java/com/example/analyzelog/web/ApiController.java) | Calls HTTP endpoint: GET /api/country-stats | java, javascript |
| [src/main/resources/static/js/human.js](../../../../../../src/main/resources/static/js/human.js) | [src/main/java/com/example/analyzelog/web/ApiController.java](../../../../../../src/main/java/com/example/analyzelog/web/ApiController.java) | Calls HTTP endpoints: GET /api/human-count, GET /api/human-unknown-uas | java, javascript |
| [src/main/resources/static/js/social-referrals.js](../../../../../../src/main/resources/static/js/social-referrals.js) | [src/main/java/com/example/analyzelog/web/ApiController.java](../../../../../../src/main/java/com/example/analyzelog/web/ApiController.java) | Calls HTTP endpoints: GET /api/social-networks, GET /api/social-networks/result-types | java, javascript |
