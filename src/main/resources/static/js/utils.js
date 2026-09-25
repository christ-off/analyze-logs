'use strict';

import { Charts } from './charts.js';

export function readMeta(name) {
    return document.querySelector(`meta[name="${name}"]`).content;
}

export function escapeHtml(s) {
    return s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
}

export function formatTimestamp(iso) {
    return (iso || '?').replace('T', ' ').replace(/\.\d+Z?$/, '').replace(/Z$/, '');
}

export function buildBaseParams(extra) {
    const from = readMeta('cf-from');
    const to   = readMeta('cf-to');
    const p = new URLSearchParams({ ...extra, from: Charts.toDateParam(from), to: Charts.toDateParam(to) });
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

export function detailUrl(path, params) {
    const from = Charts.toDateParam(readMeta('cf-from'));
    const to   = Charts.toDateParam(readMeta('cf-to'));
    return path + '?' + new URLSearchParams({ ...params, from, to }).toString();
}

// Fetches `url`, renders one `<tr>` per row (via rowFn) into the tbody `tbodyId`,
// falling back to a colspan-wide message on an empty result or a fetch/parse failure.
// `onRendered(tbody)` runs only after a non-empty render — e.g. to update a row-count label.
export function loadSimpleTable(url, tbodyId, cols, rowFn, emptyMsg, onRendered) {
    fetch(url)
        .then(r => r.json())
        .then(data => {
            const tbody = document.getElementById(tbodyId);
            if (!tbody) return;
            tbody.innerHTML = data.length === 0
                ? `<tr><td colspan="${cols}" class="text-center text-muted">${emptyMsg}</td></tr>`
                : data.map(rowFn).join('');
            if (data.length > 0 && onRendered) onRendered(tbody, data);
        })
        .catch(() => {
            const tbody = document.getElementById(tbodyId);
            if (tbody) tbody.innerHTML = `<tr><td colspan="${cols}" class="text-center text-muted py-3">Failed to load data.</td></tr>`;
        });
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

// Lowest <browser> major version, across raw UA strings sharing that version, with
// any requests from "Probable human" IPs — versions below it look spoofed (bots
// declaring an old/fake browser version never show human evidence). `excludedVersions`
// skips versions that are expected to linger below the rest (e.g. Firefox ESR), which
// would otherwise pull the minimum down without indicating spoofing.
export function minVersionWithHumanTraffic(humanStats, browser, uaToken = browser, excludedVersions = []) {
    const versionPattern = new RegExp(String.raw`${uaToken}/(\d+)`);
    const excluded = new Set(excludedVersions);
    const totalsByVersion = new Map();
    for (const h of humanStats) {
        const m = h.name.match(versionPattern);
        if (!m) continue;
        const version = Number(m[1]);
        if (excluded.has(version)) continue;
        const entry = totalsByVersion.get(version) ?? { human: 0, total: 0 };
        entry.human += h.humanRequests;
        entry.total += h.totalRequests;
        totalsByVersion.set(version, entry);
    }
    let min = null;
    for (const [version, { human }] of totalsByVersion) {
        if (human > 0 && (min === null || version < min)) min = version;
    }
    return min;
}

// Show/hide the "Min <browser> version with requests from human IPs" banner. `browser` may be
// null (e.g. a raw UA not in the tracked desktop set) — treated the same as "no version found".
// `uaToken` is the token the version number actually follows in the raw UA string (e.g. Edge's
// raw UA carries "Edg/144", not "Edge/144") — defaults to `browser` when they're the same.
// `excludedVersions` — see minVersionWithHumanTraffic.
export function renderMinVersionBanner(elementId, browser, humanStats, uaToken = browser, excludedVersions = []) {
    const banner = document.getElementById(elementId);
    if (!banner) return;
    const minVersion = browser ? minVersionWithHumanTraffic(humanStats, browser, uaToken, excludedVersions) : null;
    if (minVersion === null) {
        banner.classList.add('d-none');
    } else {
        banner.textContent = `Min ${browser} version with requests from human IPs: ${minVersion}`;
        banner.classList.remove('d-none');
    }
}
