'use strict';

import { buildBaseParams, escapeHtml, resultTotal, stackedBar } from '../utils.js';

const tbody = document.getElementById('zipRequestsTable');
const COLS = 3;

function render(rows) {
    const maxTotal = Math.max(...rows.map(resultTotal));
    tbody.innerHTML = rows.map(u => `<tr>
        <td><code>${escapeHtml(u.name)}</code></td>
        <td class="text-end">${resultTotal(u).toLocaleString()}</td>
        <td class="align-middle px-2">${stackedBar(u, maxTotal)}</td>
    </tr>`).join('');
}

fetch('/api/zip-requests/uris?' + buildBaseParams({}))
    .then(r => r.json())
    .then(data => {
        if (data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="${COLS}" class="text-center text-muted py-3">No archive requests found for the selected date range.</td></tr>`;
            return;
        }
        render(data);
        document.getElementById('zipRequestsCount').textContent = `(${data.length.toLocaleString()} URIs, sorted by count)`;
        document.getElementById('zipRequestsLegend')?.style.removeProperty('display');
    })
    .catch(() => {
        tbody.innerHTML = `<tr><td colspan="${COLS}" class="text-center text-muted py-3">Failed to load data.</td></tr>`;
    });
