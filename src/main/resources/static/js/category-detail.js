import { Charts } from './charts.js';
import { readMeta, buildBaseParams, initToggleBots, detailUrl } from './utils.js';

const category = readMeta('cf-category');

function loadAllCharts() {
    const p = buildBaseParams({ category });

    Charts.loadChart(`category-detail/url-split?${p}`,   d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`category-detail/user-agents?${p}`, d => Charts.horizontalStackedBar('chartUaNames',  d,
        item => detailUrl('/ua-detail', { ua: item.name })));
}

initToggleBots(loadAllCharts);
