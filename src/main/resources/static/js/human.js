import { loadCoreCharts } from './dashboard-charts.js';
import { initRefresh } from './refresh.js';
import { buildBaseParams, escapeHtml, formatTimestamp, stackedBar } from './utils.js';

function renderRow(req) {
    return `<tr>
        <td class="text-nowrap">${escapeHtml(formatTimestamp(req.timestamp))}</td>
        <td class="text-break small" title="${escapeHtml(req.userAgent)}">${escapeHtml(req.userAgent)}</td>
        <td class="font-monospace small text-truncate" style="max-width:320px" title="${escapeHtml(req.uriStem)}">${escapeHtml(req.uriStem)}</td>
        <td class="align-middle">${stackedBar(req, null)}</td>
    </tr>`;
}

function loadUnknownUas() {
    const tbody = document.getElementById('humanUnknownUas');
    if (!tbody) return;
    const p = buildBaseParams({});
    fetch('/api/human-unknown-uas?' + p)
        .then(r => r.json())
        .then(requests => {
            document.getElementById('humanUnknownUasCount').textContent = `(${requests.length} rows)`;
            tbody.classList.remove('text-muted');
            tbody.innerHTML = requests.length === 0
                ? '<tr><td colspan="4" class="text-center text-muted py-3">No unknown user agents in the selected date range.</td></tr>'
                : requests.map(renderRow).join('');
        })
        .catch(() => {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Failed to load data.</td></tr>';
        });
}

export function loadAllCharts() {
    loadCoreCharts('human-');
    loadUnknownUas();
}

loadAllCharts();
initRefresh(loadAllCharts);
