import { Charts } from './charts.js';
import { readMeta, escapeHtml, buildBaseParams, initToggleBots, detailUrl } from './utils.js';

const urlName = readMeta('cf-url');

async function loadUrlsTable() {
    const tbody = document.getElementById('tbodyUrls');
    const p = buildBaseParams({ url: urlName });
    try {
        const resp = await fetch(`/api/url-detail/urls?${p}`);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        if (!data.length) {
            tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-3">No data</td></tr>';
        } else {
            tbody.innerHTML = data.map((d, i) => `
                <tr>
                    <td class="text-muted">${i + 1}</td>
                    <td><code>${escapeHtml(d.name ?? '')}</code></td>
                    <td class="text-end">${d.count.toLocaleString()}</td>
                </tr>`).join('');
        }
    } catch (e) {
        console.error('Failed to load URLs table:', e);
        tbody.innerHTML = '<tr><td colspan="3" class="text-center text-danger py-3">Error loading data</td></tr>';
    }
}

function loadAllCharts() {
    const p = buildBaseParams({ url: urlName });

    Charts.loadChart(`url-detail/countries?${p}`,
        data => Charts.horizontalStackedBar('chartCountries', data,
            item => detailUrl('/country-detail', { country: item.code })));

    Charts.loadChart(`url-detail/user-agents?${p}`,
        data => Charts.horizontalStackedBar('chartUserAgents', data,
            d => detailUrl('/ua-detail', { ua: d.name })));

    Charts.loadChart(`url-detail/requests-per-day?${p}`,
        data => Charts.stackedBarByDay('chartRequestsPerDay', data));

    loadUrlsTable();
}

initToggleBots(loadAllCharts);
