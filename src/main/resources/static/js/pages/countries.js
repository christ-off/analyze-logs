'use strict';

import { buildBaseParams, detailUrl, escapeHtml, stackedBar } from '../utils.js';
import { initRefresh } from '../refresh.js';

const tbody = document.getElementById('countriesTable');
const COLS = 8;
const filterWarning = document.getElementById('filterWarning');
const filterBlocked = document.getElementById('filterBlocked');
let rows = [];
let sort = { key: 'total', dir: -1 };

function marker(title, icon) {
    return ` <span title="${title}" aria-label="${title}">${icon}</span>`;
}

// Blocked: every request errored. Warning: no human, no Mastodon, and no feed requests.
function status(c) {
    if (c.error > 0 && c.error === c.total) return 'blocked';
    if (c.humanRequests === 0 && c.mastodon === 0 && c.feeds === 0) return 'warning';
    return null;
}

function blockCandidate(s) {
    if (s === 'blocked') return marker('All requests are errors', '🛑');
    if (s === 'warning') return marker('No human, Mastodon, or feed requests', '⚠️');
    return '';
}

function humanMarker(c) {
    return c.humanRequests > 0 ? marker('Has human requests', '🧑') : '';
}

function render() {
    const filtered = rows
        .map(c => [c, status(c)])
        .filter(([, s]) => (s !== 'warning' || filterWarning.checked) && (s !== 'blocked' || filterBlocked.checked));
    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="${COLS}" class="text-center text-muted py-3">No countries match the selected filters.</td></tr>`;
        return;
    }
    const sorted = filtered.sort(([a], [b]) => {
        const x = a[sort.key], y = b[sort.key];
        return (typeof x === 'string' ? x.localeCompare(y) : x - y) * sort.dir;
    });
    const maxTotal = Math.max(...rows.map(r => r.total));
    tbody.innerHTML = sorted.map(([c, s]) => `<tr>
        <td><a href="${detailUrl('/country-detail', { country: c.code })}">${escapeHtml(c.name)}</a>${humanMarker(c)}${blockCandidate(s)}</td>
        <td class="text-end">${c.total.toLocaleString()}</td>
        <td class="text-end">${c.humanRequests.toLocaleString()}</td>
        <td class="text-end">${c.mastodon.toLocaleString()}</td>
        <td class="text-end">${c.searchBots.toLocaleString()}</td>
        <td class="text-end">${c.feeds.toLocaleString()}</td>
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

[filterWarning, filterBlocked].forEach(cb => cb.addEventListener('change', () => {
    if (rows.length) render();
}));

load();
initRefresh(load);
