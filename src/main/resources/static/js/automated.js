import { initRefresh } from './refresh.js';
import { buildBaseParams, escapeHtml, uaRequestsUrl } from './utils.js';

function renderRow(ua) {
    return `<tr>
        <td class="text-break small"><a href="${uaRequestsUrl(ua.name)}">${escapeHtml(ua.name)}</a></td>
        <td class="text-end">${ua.count.toLocaleString()}</td>
    </tr>`;
}

function loadAutomatedUas() {
    const tbody = document.getElementById('automatedUas');
    if (!tbody) return;
    const p = buildBaseParams({});
    fetch('/api/automated-user-agents?' + p)
        .then(r => r.json())
        .then(uas => {
            document.getElementById('automatedUasCount').textContent = `(${uas.length} rows)`;
            tbody.classList.remove('text-muted');
            tbody.innerHTML = uas.length === 0
                ? '<tr><td colspan="2" class="text-center text-muted py-3">No fully automated user agents in the selected date range.</td></tr>'
                : uas.map(renderRow).join('');
        })
        .catch(() => {
            tbody.innerHTML = '<tr><td colspan="2" class="text-center text-muted py-3">Failed to load data.</td></tr>';
        });
}

export function loadAllCharts() {
    loadAutomatedUas();
}

loadAllCharts();
initRefresh(loadAllCharts);
