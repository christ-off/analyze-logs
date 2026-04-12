import { Charts } from './charts.js';
import { readMeta, escapeHtml } from './utils.js';

const from    = readMeta('cf-from');
const to      = readMeta('cf-to');
const urlName = readMeta('cf-url');

const urlParams = new URLSearchParams({
    url:  urlName,
    from: Charts.toDateParam(from),
    to:   Charts.toDateParam(to),
});

const tbody = document.getElementById('tbodyUrls');
try {
    const resp = await fetch(`/api/url-detail/urls?${urlParams}`);
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

Charts.loadChart(`url-detail/countries?${urlParams}`,
    data => Charts.horizontalStackedBar('chartCountries', data,
        item => `/country-detail?country=${encodeURIComponent(item.code)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));

Charts.loadChart(`url-detail/user-agents?${urlParams}`,
    data => Charts.horizontalStackedBar('chartUserAgents', data,
        d => `/ua-detail?ua=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));

Charts.loadChart(`url-detail/requests-per-day?${urlParams}`,
    data => Charts.stackedBarByDay('chartRequestsPerDay', data));
