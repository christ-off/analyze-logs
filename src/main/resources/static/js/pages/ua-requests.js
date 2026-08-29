'use strict';

import { initIpLookup } from '../ip-info.js';
import { initAbuseReportButtons, initBulkAbuseReport, initSelectSameIp } from '../abuse-report.js';

export function init() {
    initIpLookup();
    initAbuseReportButtons();
    initBulkAbuseReport();
    initSelectSameIp();
}

init();
