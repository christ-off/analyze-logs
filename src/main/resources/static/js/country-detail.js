import { Charts } from './charts.js';

const from    = document.querySelector('meta[name="cf-from"]').content;
const to      = document.querySelector('meta[name="cf-to"]').content;
const country = document.querySelector('meta[name="cf-country"]').content;

const CHART_IDS = ['chartUaNames', 'chartResultTypes', 'chartUriStems', 'chartRequestsPerDay'];

function buildParams() {
    const p = new URLSearchParams({ country, from: Charts.toDateParam(from), to: Charts.toDateParam(to) });
    const toggleEl = document.getElementById('toggleBots');
    if (toggleEl?.checked) p.set('excludeBots', 'true');
    return p.toString();
}

function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildParams();

    Charts.loadChart(`country-detail/ua-split?${p}`,          d => Charts.horizontalStackedBar('chartUaNames',      d));
    Charts.loadChart(`country-detail/result-types?${p}`,     d => Charts.pie('chartResultTypes',          d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`country-detail/url-split?${p}`,         d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`country-detail/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));
}

const toggleEl = document.getElementById('toggleBots');
if (toggleEl) {
    toggleEl.checked = localStorage.getItem('excludeBots') === 'true';
    toggleEl.addEventListener('change', () => {
        localStorage.setItem('excludeBots', String(toggleEl.checked));
        loadAllCharts();
    });
}

loadAllCharts();
