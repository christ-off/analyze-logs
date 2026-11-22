import { Charts } from './charts.js';
import { readMeta, buildBaseParams, detailUrl } from './utils.js';
import { loadUaChart } from './dashboard-charts.js';
import { initRefresh } from './refresh.js';

const asn = readMeta('cf-asn');

function loadAllCharts() {
    const p = buildBaseParams({ asn });

    loadUaChart('chartUaNames', `asn-detail/ua-split?${p}`);
    Charts.loadChart(`asn-detail/url-split?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d,
        item => detailUrl('/url-detail', { url: item.name })));
    Charts.loadChart(`asn-detail/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));
}

loadAllCharts();
initRefresh(loadAllCharts);
