'use strict';

import { Charts } from '../charts.js';
import { buildBaseParams } from '../utils.js';
import { loadUaChart } from '../dashboard-charts.js';
import { initRefresh } from '../refresh.js';

export function loadFeedReaderCharts() {
    const p = buildBaseParams({});
    loadUaChart('chartFeedReaders', `feed-readers/user-agents?${p}`);
    Charts.loadChart(`feed-readers/requests-per-day?${p}`, data => Charts.stackedBarByDay('chartRequestsPerDay', data));
}

loadFeedReaderCharts();
initRefresh(loadFeedReaderCharts);
