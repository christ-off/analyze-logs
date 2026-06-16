'use strict';

import { Charts } from './charts.js';

export function readMeta(name) {
    return document.querySelector(`meta[name="${name}"]`).content;
}

export function escapeHtml(s) {
    return s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
}

export function buildBaseParams(extra) {
    const from = readMeta('cf-from');
    const to   = readMeta('cf-to');
    const p = new URLSearchParams({ ...extra, from: Charts.toDateParam(from), to: Charts.toDateParam(to) });
    const toggleEl = document.getElementById('toggleBots');
    if (toggleEl?.checked) p.set('excludeBots', 'true');
    return p.toString();
}

export function resultTotal(row) {
    return row.hit + row.miss + row['function'] + row.error;
}

export function uaRequestsUrl(ua) {
    const from = readMeta('cf-from').slice(0, 10);
    const to   = readMeta('cf-to-date');
    return '/ua-requests?' + new URLSearchParams({ ua, from, to }).toString();
}

const SEGMENTS = [
    { key: 'hit',      label: 'Hit',      color: Charts.COLORS.green  },
    { key: 'miss',     label: 'Miss',     color: Charts.COLORS.blue   },
    { key: 'function', label: 'Filtered', color: Charts.COLORS.orange },
    { key: 'error',    label: 'Error',    color: Charts.COLORS.red    },
];

export function stackedBar(row, maxTotal) {
    const total = resultTotal(row);
    if (total === 0) return '';
    const scale = maxTotal === null ? 100 : (total / maxTotal * 100);
    const segments = SEGMENTS
        .filter(s => row[s.key] > 0)
        .map(s => {
            const w = (row[s.key] / total * scale).toFixed(2);
            return `<div style="width:${w}%;background:${s.color};height:100%" title="${s.label}: ${row[s.key].toLocaleString()}"></div>`;
        })
        .join('');
    return `<div style="display:flex;height:1.1em;width:100%;border-radius:2px;overflow:hidden">${segments}</div>`;
}

export function initToggleBots(loadFn) {
    const toggleEl = document.getElementById('toggleBots');
    if (toggleEl) {
        toggleEl.checked = localStorage.getItem('excludeBots') === 'true';
        toggleEl.addEventListener('change', () => {
            localStorage.setItem('excludeBots', String(toggleEl.checked));
            loadFn();
        });
    }
    loadFn();
}
