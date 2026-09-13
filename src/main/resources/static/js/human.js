import { loadCoreCharts } from './dashboard-charts.js';
import { initRefresh } from './refresh.js';

export function loadAllCharts() {
    loadCoreCharts('human-');
}

loadAllCharts();
initRefresh(loadAllCharts);
