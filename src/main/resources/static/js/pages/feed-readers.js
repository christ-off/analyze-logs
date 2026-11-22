'use strict';

import { Charts } from '../charts.js';
import { buildBaseParams } from '../utils.js';
import { loadUaChart } from '../dashboard-charts.js';
import { initRefresh } from '../refresh.js';

export function loadFeedReaderCharts() {
    const p = buildBaseParams({});
    loadUaChart('chartFeedReaders', `feed-readers/user-agents?${p}`);
    Charts.loadChart(`feed-readers/subscribers-per-day?${p}`, data => Charts.stackedBarByDayCategory('chartSubscribersPerDay', data));
}

loadFeedReaderCharts();
initRefresh(loadFeedReaderCharts);
