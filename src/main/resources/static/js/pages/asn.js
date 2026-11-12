'use strict';

import { Charts } from '../charts.js';
import { buildBaseParams } from '../utils.js';
import { initRefresh } from '../refresh.js';

export function loadAsnCharts() {
    const p = buildBaseParams({});
    Charts.loadChart(`asns?${p}`, data => Charts.horizontalStackedBar('chartAsns', data, null));
}

loadAsnCharts();
initRefresh(loadAsnCharts);
