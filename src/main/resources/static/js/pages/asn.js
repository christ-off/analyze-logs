'use strict';

import { Charts } from '../charts.js';
import { buildBaseParams, detailUrl } from '../utils.js';
import { initRefresh } from '../refresh.js';

const MAX_LABEL = 40;

function shorten(name) {
    return name.length > MAX_LABEL ? name.slice(0, MAX_LABEL - 1) + '…' : name;
}

export function loadAsnCharts() {
    const p = buildBaseParams({});
    Charts.loadChart(`asns?${p}`, data => Charts.horizontalStackedBar('chartAsns',
        data.map(d => ({ ...d, name: `${shorten(d.name)} · ${d.humanPercentage.toFixed(1)}% human` })),
        d => detailUrl('/asn-detail', { asn: d.name.match(/^AS(\d+)/)[1] })));
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
