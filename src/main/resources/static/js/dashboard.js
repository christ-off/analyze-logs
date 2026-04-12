import { Charts } from './charts.js';
import { readMeta, buildBaseParams, initToggleBots } from './utils.js';

const from = readMeta('cf-from');
const to   = readMeta('cf-to');

const CHART_IDS = [
    'chartUaGroups', 'chartUaNames', 'chartCountries',
    'chartTopUrls', 'chartReferers', 'chartRequestsPerDay',
];

function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({});
    Charts.loadChart(`ua-groups?${p}`,        data => Charts.pie('chartUaGroups',     data, null));
    Charts.loadChart(`ua-names-split?${p}`,   data => Charts.horizontalStackedBar('chartUaNames', data,
        d => `/ua-detail?ua=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`countries?${p}`,        data => Charts.horizontalStackedBar('chartCountries', data,
        item => `/country-detail?country=${encodeURIComponent(item.code)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`top-urls-split?${p}`,   data => Charts.horizontalStackedBar('chartTopUrls',  data,
        d => `/url-detail?url=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`referers?${p}`,         data => Charts.horizontalBar('chartReferers',         data));
    Charts.loadChart(`requests-per-day?${p}`, data => Charts.stackedBarByDay('chartRequestsPerDay',   data));
}

initToggleBots(loadAllCharts);
