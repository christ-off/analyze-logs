import { Charts } from './charts.js';
import { readMeta, escapeHtml, buildBaseParams, initToggleBots, resultTotal, stackedBar } from './utils.js';

const refererName = readMeta('cf-referer');

const CHART_IDS = ['chartRequestsPerDay'];

async function loadUrlsTable() {
    const tbody = document.getElementById('tbodyUrls');
    const p = buildBaseParams({ referer: refererName });
    try {
        const resp = await fetch(`/api/referer-detail/urls?${p}`);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        if (!data.length) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">No data</td></tr>';
            return;
        }
        const maxTotal = Math.max(...data.map(resultTotal));
        tbody.innerHTML = data.map((d, i) => `
            <tr>
                <td class="text-muted">${i + 1}</td>
                <td><code>${escapeHtml(d.name ?? '')}</code></td>
                <td class="text-end">${resultTotal(d).toLocaleString()}</td>
                <td class="align-middle px-2">${stackedBar(d, maxTotal)}</td>
            </tr>`).join('');
    } catch (e) {
        console.error('Failed to load URLs table:', e);
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-danger py-3">Error loading data</td></tr>';
    }
}

function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({ referer: refererName });
    Charts.loadChart(`referer-detail/requests-per-day?${p}`,
        data => Charts.stackedBarByDay('chartRequestsPerDay', data));
    loadUrlsTable();
}

initToggleBots(loadAllCharts);
