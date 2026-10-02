'use strict';

import { Charts } from '../charts.js';
import { buildBaseParams, detailUrl } from '../utils.js';
import { initRefresh } from '../refresh.js';

export function loadCoverCharts() {
    const p = buildBaseParams({});
    Charts.loadChart(`covers/user-agents?${p}`, data => Charts.horizontalStackedBar('chartCoverUas', data,
        d => detailUrl('/ua-detail', { ua: d.name })));
    Charts.loadChart(`covers/referer-split?${p}`, data => Charts.pie('chartCoverReferer', data, null));
    Charts.loadChart(`covers/requests-per-day?${p}`, data => Charts.stackedBarByDay('chartRequestsPerDay', data));
}

loadCoverCharts();
initRefresh(loadCoverCharts);
