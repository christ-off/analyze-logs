'use strict';

import { Charts } from './charts.js';
import { buildBaseParams, initToggleBots } from './utils.js';

function loadAllCharts() {
    const chart = Chart.getChart('chartProtocolVersions');
    if (chart) chart.destroy();
    const p = buildBaseParams({});
    Charts.loadChart(`protocol-versions-daily?${p}`, data => Charts.lineByDay('chartProtocolVersions', data));
}

initToggleBots(loadAllCharts);
