---
type: C4 Component
title: IP info
status: stable
groma:
  id: ip-info
  parent: dashboard-frontend
  code:
    - scanner: javascript
      file: src/main/resources/static/js/ip-info.js
description: Shows IP details on demand.
---

Calls /api/ip-info/:ip.

## Derived relationships

| Source | Target | Description | Technology |
| --- | --- | --- | --- |
| [src/main/resources/static/js/ip-info.js](../../../../../../src/main/resources/static/js/ip-info.js) | [src/main/java/com/example/analyzelog/web/ApiController.java](../../../../../../src/main/java/com/example/analyzelog/web/ApiController.java) | Calls HTTP endpoint: GET /api/ip-info/:ip | java, javascript |
