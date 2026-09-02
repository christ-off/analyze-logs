'use strict';

import { Charts } from '../charts.js';
import { readMeta, buildBaseParams } from '../utils.js';
import { initIpLookup } from '../ip-info.js';
import { initAbuseReportButtons, initBulkAbuseReport, initSelectSameIp } from '../abuse-report.js';

function loadRequestsPerDayChart() {
    const ua = readMeta('cf-ua');
    const p = buildBaseParams({ ua });
    Charts.loadChart(`ua-requests/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));
}

export function init() {
    loadRequestsPerDayChart();
    initIpLookup();
    initAbuseReportButtons();
    initBulkAbuseReport();
    initSelectSameIp();
}

init();
