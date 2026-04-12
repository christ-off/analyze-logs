import { Charts } from './charts.js';
import { readMeta, buildBaseParams, initToggleBots } from './utils.js';

const country = readMeta('cf-country');

const CHART_IDS = ['chartUaNames', 'chartResultTypes', 'chartUriStems', 'chartRequestsPerDay'];

function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({ country });

    Charts.loadChart(`country-detail/ua-split?${p}`,          d => Charts.horizontalStackedBar('chartUaNames',      d));
    Charts.loadChart(`country-detail/result-types?${p}`,     d => Charts.pie('chartResultTypes',          d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`country-detail/url-split?${p}`,         d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`country-detail/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));
}

initToggleBots(loadAllCharts);
