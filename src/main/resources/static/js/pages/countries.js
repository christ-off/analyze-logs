'use strict';

import { buildBaseParams, detailUrl, escapeHtml, stackedBar } from '../utils.js';
import { initRefresh } from '../refresh.js';

const tbody = document.getElementById('countriesTable');
const COLS = 7;
let rows = [];
let sort = { key: 'total', dir: -1 };

function marker(title, icon) {
    return ` <span title="${title}" aria-label="${title}">${icon}</span>`;
}

// Stop sign: every request errored. Warning: no human and no Mastodon requests.
function blockCandidate(c) {
    if (c.error > 0 && c.error === c.total) return marker('All requests are errors', '🛑');
    if (c.humanRequests === 0 && c.mastodon === 0) return marker('No human or Mastodon requests', '⚠️');
    return '';
}

function render() {
    const sorted = [...rows].sort((a, b) => {
        const x = a[sort.key], y = b[sort.key];
        return (typeof x === 'string' ? x.localeCompare(y) : x - y) * sort.dir;
    });
    const maxTotal = Math.max(...rows.map(r => r.total));
    tbody.innerHTML = sorted.map(c => `<tr>
        <td><a href="${detailUrl('/country-detail', { country: c.code })}">${escapeHtml(c.name)}</a>${blockCandidate(c)}</td>
        <td class="text-end">${c.total.toLocaleString()}</td>
        <td class="text-end">${c.humanRequests.toLocaleString()}</td>
        <td class="text-end">${c.mastodon.toLocaleString()}</td>
        <td class="text-end">${c.searchBots.toLocaleString()}</td>
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

document.querySelectorAll('button.cf-sort').forEach(btn => btn.addEventListener('click', () => {
    const key = btn.dataset.key;
    let dir = key === 'name' ? 1 : -1;
    if (sort.key === key) dir = -sort.dir;
    sort = { key, dir };
    if (rows.length) render();
}));

load();
initRefresh(load);
