'use strict';

import { Charts } from '../charts.js';
import { readMeta, buildBaseParams } from '../utils.js';
import { initRefresh } from '../refresh.js';

function loadRequestsPerDayChart() {
    const ua = readMeta('cf-ua');
    const p = buildBaseParams({ ua });
    Charts.loadChart(`ua-requests/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));
}

export function init() {
    loadRequestsPerDayChart();
}

init();
initRefresh(() => location.reload());
