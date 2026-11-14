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
                } else {
                    status.textContent = body.error ?? 'Download failed';
                }
            })
            .catch(() => { status.textContent = 'Download failed'; })
            .finally(() => { btn.disabled = false; });
    });
}

loadAsnCharts();
initNamesDownload();
initRefresh(loadAsnCharts);
