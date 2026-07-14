import { Charts } from './charts.js';
import { readMeta, buildBaseParams, initToggleBots } from './utils.js';

const country = readMeta('cf-country');
const from    = readMeta('cf-from');
const to      = readMeta('cf-to');

const CHART_IDS = ['chartUaNames', 'chartResultTypes', 'chartUriStems', 'chartTrafficCategories', 'chartRequestsPerDay'];

function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({ country });

    Charts.loadChart(`country-detail/ua-split?${p}`,          d => Charts.horizontalStackedBar('chartUaNames',      d));
    Charts.loadChart(`country-detail/result-types?${p}`,     d => Charts.pie('chartResultTypes',          d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`country-detail/url-split?${p}`,         d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`country-detail/traffic-categories?${p}`, d => Charts.horizontalStackedBar('chartTrafficCategories', d,
        item => `/category-detail?category=${encodeURIComponent(item.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`country-detail/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));
}

initToggleBots(loadAllCharts);
