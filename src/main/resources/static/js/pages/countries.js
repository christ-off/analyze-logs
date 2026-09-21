'use strict';

import { buildBaseParams, detailUrl, escapeHtml, stackedBar } from '../utils.js';
import { initRefresh } from '../refresh.js';

const tbody = document.getElementById('countriesTable');
const COLS = 6;
let rows = [];
let sort = { key: 'total', dir: -1 };

function render() {
    const sorted = [...rows].sort((a, b) => {
        const x = a[sort.key], y = b[sort.key];
        return (typeof x === 'string' ? x.localeCompare(y) : x - y) * sort.dir;
    });
    const maxTotal = Math.max(...rows.map(r => r.total));
    tbody.innerHTML = sorted.map(c => `<tr>
        <td><a href="${detailUrl('/country-detail', { country: c.code })}">${escapeHtml(c.name)}</a></td>
        <td class="text-end">${c.total.toLocaleString()}</td>
        <td class="text-end">${c.humanRequests.toLocaleString()}</td>
        <td class="text-end">${c.mastodon.toLocaleString()}</td>
        <td class="text-end">${c.humanPercentage.toFixed(1)}%</td>
        <td class="align-middle px-2">${stackedBar(c, maxTotal)}</td>
    </tr>`).join('');
}

function load() {
    fetch('/api/country-stats?' + buildBaseParams({}))
        .then(r => r.json())
        .then(data => {
            if (data.length === 0) {
                tbody.innerHTML = `<tr><td colspan="${COLS}" class="text-center text-muted py-3">No requests found for the selected date range.</td></tr>`;
                return;
            }
            rows = data;
            render();
            document.getElementById('countriesLegend')?.style.removeProperty('display');
        })
        .catch(() => {
            tbody.innerHTML = `<tr><td colspan="${COLS}" class="text-center text-muted py-3">Failed to load data.</td></tr>`;
        });
}

document.querySelectorAll('th.cf-sort').forEach(th => th.addEventListener('click', () => {
    const key = th.dataset.key;
    sort = { key, dir: sort.key === key ? -sort.dir : (key === 'name' ? 1 : -1) };
    if (rows.length) render();
}));

load();
initRefresh(load);
