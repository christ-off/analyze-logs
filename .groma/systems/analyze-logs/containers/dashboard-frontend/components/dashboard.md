---
type: C4 Component
title: Dashboard page script
status: stable
groma:
  id: dashboard
  parent: dashboard-frontend
  code:
    - scanner: javascript
      file: src/main/resources/static/js/dashboard.js
      symbol: loadAllCharts
    - scanner: javascript
      file: src/main/resources/static/js/dashboard-charts.js
      symbol: loadCoreCharts
description: Main dashboard charts.
---

Draws the dashboard charts and the hide-bots toggle.
