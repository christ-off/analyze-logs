'use strict';

import { Charts } from '../charts.js';
import { buildBaseParams, detailUrl } from '../utils.js';
import { initRefresh } from '../refresh.js';

export function loadFeedReaderCharts() {
    const p = buildBaseParams({});
    Charts.loadChart(`feed-readers/user-agents?${p}`, data => Charts.horizontalStackedBar('chartFeedReaders', data,
        d => detailUrl('/ua-detail', { ua: d.name })));
    Charts.loadChart(`feed-readers/requests-per-day?${p}`, data => Charts.stackedBarByDay('chartRequestsPerDay', data));
}

loadFeedReaderCharts();
initRefresh(loadFeedReaderCharts);
