import { Charts } from './charts.js';
import { readMeta, buildBaseParams } from './utils.js';

const from = readMeta('cf-from');
const to   = readMeta('cf-to');

const CHART_IDS = ['chartUaNames', 'chartTopUrls', 'chartRequestsPerDay'];

function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({});

    Charts.loadChart(`ai-bots/user-agents?${p}`, data => Charts.horizontalStackedBar('chartUaNames', data,
        d => `/ua-detail?ua=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`ai-bots/urls?${p}`, data => Charts.horizontalStackedBar('chartTopUrls', data,
        d => `/url-detail?url=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`ai-bots/requests-per-day?${p}`, data => Charts.stackedBarByDay('chartRequestsPerDay', data));
}

loadAllCharts();
