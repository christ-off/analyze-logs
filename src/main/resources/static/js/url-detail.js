import { Charts } from './charts.js';
import { readMeta, escapeHtml, buildBaseParams, detailUrl, resultTotal, stackedBar } from './utils.js';
import { loadUaChart } from './dashboard-charts.js';
import { initRefresh } from './refresh.js';

const urlName = readMeta('cf-url');

function loadUrlsTable() {
    const tbody = document.getElementById('tbodyUrls');
    const p = buildBaseParams({ url: urlName });
    fetch(`/api/url-detail/urls?${p}`)
        .then(r => {
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            return r.json();
        })
        .then(data => {
            if (!data.length) {
                tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">No data</td></tr>';
            } else {
                const maxTotal = Math.max(...data.map(resultTotal));
                tbody.innerHTML = data.map((d, i) => `
                <tr>
                    <td class="text-muted">${i + 1}</td>
                    <td><code>${escapeHtml(d.name ?? '')}</code></td>
                    <td class="text-end">${resultTotal(d).toLocaleString()}</td>
                    <td class="align-middle px-2">${stackedBar(d, maxTotal)}</td>
                </tr>`).join('');
            }
        })
        .catch(e => {
            console.error('Failed to load URLs table:', e);
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-danger py-3">Error loading data</td></tr>';
        });
}

export function loadAllCharts() {
    const p = buildBaseParams({ url: urlName });

    Charts.loadChart(`url-detail/countries?${p}`,
        data => Charts.horizontalStackedBar('chartCountries', data,
            item => detailUrl('/country-detail', { country: item.code })));

    loadUaChart('chartUserAgents', `url-detail/user-agents?${p}`);

    Charts.loadChart(`url-detail/requests-per-day?${p}`,
        data => Charts.stackedBarByDay('chartRequestsPerDay', data));

    loadUrlsTable();
}

loadAllCharts();
initRefresh(loadAllCharts);
