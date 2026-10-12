---
type: C4 Component
title: Test and build tooling
status: stable
groma:
  id: development-tooling
  parent: analyze-logs
  code:
    - scanner: javascript
      file: playwright.config.js
    - scanner: javascript
      file: src/test/js/abuse-report.test.js
    - scanner: javascript
      file: src/test/js/bot-analysis.test.js
    - scanner: javascript
      file: src/test/js/browsers.test.js
    - scanner: javascript
      file: src/test/js/charts.test.js
    - scanner: javascript
      file: src/test/js/countries.test.js
      symbol: row
    - scanner: javascript
      file: e2e/country-detail.spec.js
    - scanner: javascript
      file: src/test/js/covers.test.js
    - scanner: javascript
      file: src/test/js/dashboard-charts.test.js
    - scanner: javascript
      file: e2e/dashboard.spec.js
    - scanner: javascript
      file: src/test/js/dashboard.test.js
    - scanner: javascript
      file: src/test/js/human.test.js
    - scanner: javascript
      file: src/test/js/ip-requests.test.js
    - scanner: javascript
      file: src/test/js/refresh.test.js
    - scanner: javascript
      file: src/test/js/social-referrals.test.js
      symbol: section
    - scanner: javascript
      file: src/test/js/test-helpers.js
      symbol: flushPromises
    - scanner: javascript
      file: src/test/js/ua-requests.test.js
    - scanner: javascript
      file: src/test/js/utils.test.js
      symbol: stat
    - scanner: javascript
      file: vitest.config.js
    - scanner: javascript
      file: src/test/js/zip-requests.test.js
description: Vitest unit tests, Playwright e2e specs and their configuration.
---

Not product architecture. Vitest tests under src/test/js and Playwright specs under e2e exercise the frontend scripts. These files are excluded from scanning in scanners.json; the component remains only because the first scan stored them.
