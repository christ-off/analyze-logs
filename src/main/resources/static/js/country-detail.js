import { Charts } from './charts.js';
import { readMeta, buildBaseParams } from './utils.js';
import { initRefresh } from './refresh.js';

const country = readMeta('cf-country');

function loadAllCharts() {
    const p = buildBaseParams({ country });

    Charts.loadChart(`country-detail/ua-split?${p}`,          d => Charts.horizontalStackedBar('chartUaNames',      d));
    Charts.loadChart(`country-detail/result-types?${p}`,     d => Charts.pie('chartResultTypes',          d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`country-detail/url-split?${p}`,         d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`country-detail/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));
}

loadAllCharts();
initRefresh(loadAllCharts);
