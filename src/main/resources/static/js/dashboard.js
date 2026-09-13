import { Charts } from './charts.js';
import { buildBaseParams } from './utils.js';
import { loadCoreCharts } from './dashboard-charts.js';
import { initRefresh } from './refresh.js';

export function loadAllCharts() {
    const p = buildBaseParams({});
    Charts.loadChart(`ua-groups?${p}`, data => Charts.pie('chartUaGroups',  data, null));
    Charts.loadChart(`platforms?${p}`, data => Charts.pie('chartPlatforms', data, null));
    loadCoreCharts('');
}

loadAllCharts();
initRefresh(loadAllCharts);
