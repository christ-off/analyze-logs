import { Charts } from './charts.js';
import { readMeta, buildBaseParams } from './utils.js';

const from = readMeta('cf-from');
const to   = readMeta('cf-to');

const CHART_IDS = ['chartTrafficCategories', 'chartCountries', 'chartRequestsPerDay'];

function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({});

    Charts.loadChart(`security/traffic-categories?${p}`, data =>
        Charts.horizontalBar('chartTrafficCategories', data,
            d => `/url-detail?url=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`,
            data.map(d => Charts.SECURITY_CATEGORY_COLORS[d.name] ?? Charts.ACCENT)));

    Charts.loadChart(`security/top-countries?${p}`, data =>
        Charts.horizontalBar('chartCountries', data,
            item => `/country-detail?country=${encodeURIComponent(item.code)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`,
            Charts.SECURITY_TOTAL_COLOR));

    Charts.loadChart(`security/requests-per-day?${p}`, data =>
        Charts.stackedBarByDay('chartRequestsPerDay', data, Charts.SECURITY_CATEGORY_COLORS));
}

loadAllCharts();
