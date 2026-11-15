'use strict';

import { buildBaseParams, detailUrl, escapeHtml, stackedBar } from '../utils.js';
import { initRefresh } from '../refresh.js';

const COLS = 7;

function message(text) {
    document.getElementById('asnTable').innerHTML =
        `<tr><td colspan="${COLS}" class="text-center text-muted py-3">${text}</td></tr>`;
}

export function loadAsnCharts() {
    fetch('/api/asns?' + buildBaseParams({}))
        .then(r => r.json())
        .then(data => {
            if (data.length === 0) {
                message('No requests found for the selected date range.');
                return;
            }
            const maxTotal = Math.max(...data.map(r => r.total));
            document.getElementById('asnTable').innerHTML = data.map(a => `<tr>
        <td><a href="${detailUrl('/asn-detail', { asn: a.asn })}">AS${escapeHtml(a.asn)}</a></td>
        <td>${escapeHtml(a.name ?? '')}</td>
        <td>${escapeHtml(a.category ?? '')}</td>
        <td class="text-end">${a.humanPercentage.toFixed(1)}%</td>
        <td class="text-end">${a.feeds.toLocaleString()}</td>
        <td class="text-end">${a.total.toLocaleString()}</td>
        <td class="align-middle px-2">${stackedBar(a, maxTotal)}</td>
    </tr>`).join('');
            document.getElementById('asnLegend')?.style.removeProperty('display');
        })
        .catch(() => message('Failed to load data.'));
}

function loadBlocklist() {
    const params = buildBaseParams({});
    document.getElementById('asnKvsExport').href = '/api/asn-blocklist/kvs.json?' + params;
    fetch('/api/asn-blocklist?' + params)
        .then(r => r.json())
        .then(data => {
            document.getElementById('asnBlocklistTable').innerHTML = data.length === 0
                ? '<tr><td colspan="6" class="text-center text-muted py-3">No candidate in the selected date range.</td></tr>'
                : data.map(a => `<tr>
        <td><a href="${detailUrl('/asn-detail', { asn: a.asn })}">AS${escapeHtml(a.asn)}</a></td>
        <td>${escapeHtml(a.name ?? '')}</td>
        <td>${a.blocked ? '<span class="badge text-bg-danger">block</span>' : '<span class="badge text-bg-success">allow</span>'}</td>
        <td>${escapeHtml(a.reason)}</td>
        <td class="text-end">${a.total.toLocaleString()}</td>
        <td class="text-end">${a.humanRequests.toLocaleString()}</td>
    </tr>`).join('');
        })
        .catch(() => {
            document.getElementById('asnBlocklistTable').innerHTML =
                '<tr><td colspan="6" class="text-center text-muted py-3">Failed to load data.</td></tr>';
        });
}

function initNamesDownload() {
    const btn = document.getElementById('asnNamesBtn');
    const status = document.getElementById('asnNamesStatus');
    if (!btn) return;
    // CSRF token injected by Thymeleaf in the toolbar's hidden refresh form
    const csrfToken = document.querySelector('#refreshForm input[name="_csrf"]')?.value;

    btn.addEventListener('click', () => {
        btn.disabled = true;
        status.textContent = 'Downloading…';
        fetch('/asn/names/refresh', { method: 'POST', headers: csrfToken ? { 'X-CSRF-TOKEN': csrfToken } : {} })
            .then(r => r.json().then(body => ({ ok: r.ok, body })))
            .then(({ ok, body }) => {
                if (ok) {
                    status.textContent = `${body.count.toLocaleString()} ASN names loaded`;
                    loadAsnCharts();
                    loadBlocklist();
                } else {
                    status.textContent = body.error ?? 'Download failed';
                }
            })
            .catch(() => { status.textContent = 'Download failed'; })
            .finally(() => { btn.disabled = false; });
    });
}

loadAsnCharts();
loadBlocklist();
initNamesDownload();
initRefresh(() => { loadAsnCharts(); loadBlocklist(); });
