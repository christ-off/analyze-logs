'use strict';

import { Charts } from './charts.js';
import { initIpLookup } from './ip-info.js';
import { buildBaseParams, escapeHtml, initToggleBots, readMeta, resultTotal, stackedBar, uaRequestsUrl } from './utils.js';

const cfFrom = readMeta('cf-from');
const cfTo   = readMeta('cf-to');

function uaDetailUrl(uaName) {
    return `/ua-detail?ua=${encodeURIComponent(uaName)}&from=${Charts.toDateParam(cfFrom)}&to=${Charts.toDateParam(cfTo)}`;
}

function loadBotTable(url, tbodyId, emptyMsg) {
    fetch(url)
        .then(r => r.json())
        .then(data => {
            const tbody = document.getElementById(tbodyId);
            if (!tbody) return;
            if (data.length === 0) {
                tbody.innerHTML = `<tr><td colspan="3" class="text-center text-muted">${emptyMsg}</td></tr>`;
                return;
            }
            const maxTotal = Math.max(...data.map(resultTotal));
            tbody.innerHTML = data.map(bot => `<tr>
                <td><a href="${uaRequestsUrl(bot.name)}">${escapeHtml(bot.name)}</a></td>
                <td class="text-end">${resultTotal(bot).toLocaleString()}</td>
                <td class="align-middle px-2">${stackedBar(bot, maxTotal)}</td>
            </tr>`).join('');
        })
        .catch(() => {
            const tbody = document.getElementById(tbodyId);
            if (tbody) tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-3">Failed to load data.</td></tr>';
        });
}

function loadProbableBots() {
    const p = buildBaseParams({});
    loadBotTable('/api/probable-bots?' + p, 'probableBotsTable', 'No extless-only bots found for the selected date range.');
}

function loadBotHumanDaily(data) {
    const ctx = document.getElementById('chartBotHumanDaily');
    if (!ctx) return;
    new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.map(d => d.day),
            datasets: [
                {
                    label: 'Bots',
                    data: data.map(d => d.bots),
                    borderColor: 'rgba(253, 126, 20, 1)',
                    backgroundColor: 'rgba(253, 126, 20, 0.1)',
                    fill: true,
                    tension: 0.3,
                },
                {
                    label: 'Humans',
                    data: data.map(d => d.humans),
                    borderColor: 'rgba(40, 167, 69, 1)',
                    backgroundColor: 'rgba(40, 167, 69, 0.1)',
                    fill: true,
                    tension: 0.3,
                }
            ]
        },
        options: {
            responsive: true,
            scales: {
                x: { type: 'category' },
                y: { stacked: true, beginAtZero: true }
            }
        }
    });
}

function loadSimpleTable(url, tbodyId, cols, rowFn, emptyMsg, onRendered) {
    fetch(url)
        .then(r => r.json())
        .then(data => {
            const tbody = document.getElementById(tbodyId);
            if (!tbody) return;
            tbody.innerHTML = data.length === 0
                ? `<tr><td colspan="${cols}" class="text-center text-muted">${emptyMsg}</td></tr>`
                : data.map(rowFn).join('');
            if (data.length > 0 && onRendered) onRendered(tbody);
        })
        .catch(() => {
            const tbody = document.getElementById(tbodyId);
            if (tbody) tbody.innerHTML = `<tr><td colspan="${cols}" class="text-center text-muted py-3">Failed to load data.</td></tr>`;
        });
}

export function loadFakeBrowsers() {
    const p = buildBaseParams({});
    loadSimpleTable('/api/fake-browsers?' + p, 'fakeBrowsersTable', 4, b => `<tr>
        <td><a href="${uaRequestsUrl(b.userAgent)}">${escapeHtml(b.userAgent)}</a></td>
        <td class="text-end">${b.count.toLocaleString()}</td>
        <td class="text-end">${b.activeHours} / 24</td>
        <td class="text-end">${b.days}</td>
    </tr>`, 'No round-the-clock browser UAs found for the selected date range.');
}

export function loadBrowserConfigFetches() {
    const p = buildBaseParams({});
    loadSimpleTable('/api/browser-config?' + p, 'browserConfigTable', 2, b => `<tr>
        <td><a href="${uaRequestsUrl(b.name)}">${escapeHtml(b.name)}</a></td>
        <td class="text-end">${b.count.toLocaleString()}</td>
    </tr>`, 'No browser UAs fetched site config files in the selected date range.');
}

export function loadBurstIps() {
    const p = buildBaseParams({});
    loadSimpleTable('/api/burst-ips?' + p, 'burstIpsTable', 3, b => `<tr>
        <td class="ip-cell" data-ip="${escapeHtml(b.clientIp)}"><code>${escapeHtml(b.clientIp)}</code></td>
        <td class="text-end">${b.maxPerMinute.toLocaleString()}</td>
        <td class="text-end">${b.total.toLocaleString()}</td>
    </tr>`, 'No burst IPs found for the selected date range.', initIpLookup);
}

function resultBar(b) {
    const total = resultTotal(b) || 1;
    const seg = (val, color, label) =>
        val > 0
            ? `<div style="width:${(val / total * 100).toFixed(1)}%;background:${color};height:16px;display:inline-block" title="${label}: ${val}"></div>`
            : '';
    return `<div class="d-flex" style="background:#dee2e6;border-radius:3px;overflow:hidden">
        ${seg(b.hit, Charts.COLORS.green, 'Hit')}${seg(b.miss, Charts.COLORS.blue, 'Miss')}${seg(b['function'], Charts.COLORS.orange, 'Filtered')}${seg(b.error, Charts.COLORS.red, 'Error')}
    </div>`;
}

function loadDisobedientBots(data) {
    const tbody = document.getElementById('disobedientBotsTable');
    if (!tbody) return;
    if (data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-3">No disobedient bots found. Try refreshing robots.txt first.</td></tr>';
        return;
    }
    tbody.innerHTML = data.map(b => `<tr>
        <td><a href="${uaRequestsUrl(b.userAgent)}">${escapeHtml(b.userAgent)}</a></td>
        <td class="text-end">${b.count.toLocaleString()}</td>
        <td class="align-middle px-2">${resultBar(b)}</td>
    </tr>`).join('');
}

export function loadDisobedientSection() {
    const p = buildBaseParams({});
    fetch('/api/robots-disobedient?' + p)
        .then(r => r.json())
        .then(loadDisobedientBots)
        .catch(() => {
            const tbody = document.getElementById('disobedientBotsTable');
            if (tbody) tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-3">Failed to load data.</td></tr>';
        });
}

export function initRobotsRefresh() {
    const btn = document.getElementById('refreshRobotsBtn');
    if (!btn) return;
    btn.addEventListener('click', () => {
        btn.disabled = true;
        btn.textContent = 'Refreshing…';
        fetch('/api/robots-refresh')
            .then(r => r.text())
            .then(msg => {
                const el = document.getElementById('robotsRefreshedAt');
                if (el) el.textContent = msg;
                loadDisobedientSection();
            })
            .catch(() => {
                const el = document.getElementById('robotsRefreshedAt');
                if (el) el.textContent = 'Refresh failed';
            })
            .finally(() => {
                btn.disabled = false;
                btn.textContent = 'Refresh Robots';
            });
    });
}

export function loadAllCharts() {
    const chart = Chart.getChart('chartBotHumanDaily');
    if (chart) chart.destroy();
    const topBotsChart = Chart.getChart('chartTopBots');
    if (topBotsChart) topBotsChart.destroy();

    const p = buildBaseParams({});
    Charts.loadChart(`top-bots?${p}`, data => Charts.horizontalStackedBar('chartTopBots', data, d => uaDetailUrl(d.name)));
    loadProbableBots();
    loadFakeBrowsers();
    loadBrowserConfigFetches();
    loadBurstIps();
    Charts.loadChart(`bot-human-daily?${p}`, loadBotHumanDaily);
    loadDisobedientSection();
}

initToggleBots(loadAllCharts);
initRobotsRefresh();