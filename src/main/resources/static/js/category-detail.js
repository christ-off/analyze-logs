import { Charts } from './charts.js';
import { readMeta, buildBaseParams, initToggleBots } from './utils.js';

const category = readMeta('cf-category');

const CHART_IDS = ['chartUriStems', 'chartUaNames'];

function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({ category });

    Charts.loadChart(`category-detail/url-split?${p}`,   d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`category-detail/user-agents?${p}`, d => Charts.horizontalStackedBar('chartUaNames',  d));
}

initToggleBots(loadAllCharts);
