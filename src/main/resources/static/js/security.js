import { Charts } from './charts.js';
import { buildBaseParams, detailUrl } from './utils.js';

function loadAllCharts() {
    const p = buildBaseParams({});

    Charts.loadChart(`security/traffic-categories?${p}`, data =>
        Charts.horizontalBar('chartTrafficCategories', data,
            d => detailUrl('/url-detail', { url: d.name }),
            data.map(d => Charts.SECURITY_CATEGORY_COLORS[d.name] ?? Charts.ACCENT)));

    Charts.loadChart(`security/top-countries?${p}`, data =>
        Charts.horizontalStackedBar('chartCountries', data,
            item => detailUrl('/country-detail', { country: item.code })));

    Charts.loadChart(`security/requests-per-day?${p}`, data =>
        Charts.stackedBarByDayCategory('chartRequestsPerDay', data, Charts.SECURITY_CATEGORY_COLORS));
}

loadAllCharts();
