'use strict';

import { Charts } from './charts.js';
import { readMeta, buildBaseParams, initToggleBots } from './utils.js';

const GROUP_COLORS = {
    'AI Bots':        'rgba(111, 66, 193, 0.8)',
    'Search Bots':    'rgba(253, 126, 20, 0.8)',
    'Other Bots':     'rgba(220, 53, 69, 0.8)',
    'Apps':           'rgba(32, 201, 151, 0.8)',
    'Feed Readers':   'rgba(54, 162, 235, 0.8)',
    'Browsers':       'rgba(40, 167, 69, 0.8)',
    'Fediverse':      'rgba(13, 202, 240, 0.8)',
    'Unknown':        'rgba(108, 117, 125, 0.8)',
};

function loadBotHumanSplit(data) {
    Charts.pie('chartBotHumanSplit', data, GROUP_COLORS);
}

function loadBotTypeSplit(data) {
    Charts.pie('chartBotTypeSplit', data, GROUP_COLORS);
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

function loadResponseCodes(data) {
    const ctx = document.getElementById('chartResponseCodes');
    if (!ctx) return;
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => d.codeType),
            datasets: [
                {
                    label: 'Bots',
                    data: data.map(d => d.bots),
                    backgroundColor: 'rgba(253, 126, 20, 0.8)',
                },
                {
                    label: 'Humans',
                    data: data.map(d => d.humans),
                    backgroundColor: 'rgba(40, 167, 69, 0.8)',
                }
            ]
        },
        options: {
            responsive: true,
            indexAxis: 'y',
            scales: {
                x: { stacked: true, beginAtZero: true },
                y: { stacked: true }
            }
        }
    });
}

function loadHourlyActivity(data) {
    const ctx = document.getElementById('chartHourlyActivity');
    if (!ctx) return;
    const hours = Array.from({ length: 24 }, (_, i) => i);
    const bots = hours.map(h => {
        const entry = data.find(d => d.hour === h);
        return entry ? entry.bots : 0;
    });
    const humans = hours.map(h => {
        const entry = data.find(d => d.hour === h);
        return entry ? entry.humans : 0;
    });
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: hours.map(h => h.toString().padStart(2, '0') + ':00'),
            datasets: [
                {
                    label: 'Bots',
                    data: bots,
                    backgroundColor: 'rgba(253, 126, 20, 0.8)',
                },
                {
                    label: 'Humans',
                    data: humans,
                    backgroundColor: 'rgba(40, 167, 69, 0.8)',
                }
            ]
        },
        options: {
            responsive: true,
            scales: {
                x: { stacked: true },
                y: { stacked: true, beginAtZero: true }
            }
        }
    });
}

function loadTopBotIps(data) {
    const tbody = document.getElementById('topBotIpsTable');
    if (!tbody) return;
    if (data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="2" class="text-center text-muted py-3">No bot IPs found.</td></tr>';
        return;
    }
    tbody.innerHTML = data.map(d =>
        `<tr><td class="font-monospace">${d.name}</td>` +
        `<td class="text-end">${d.count.toLocaleString()}</td></tr>`
    ).join('');
}

export function loadAllCharts() {
    ['chartBotHumanSplit', 'chartBotTypeSplit', 'chartBotHumanDaily',
     'chartHourlyActivity', 'chartResponseCodes', 'topBotIpsTable'
    ].forEach(id => {
        const chart = Chart.getChart(id);
        if (chart) chart.destroy();
    });

    const p = buildBaseParams({});

    Charts.loadChart(`bot-human-split?${p}`,        loadBotHumanSplit);
    Charts.loadChart(`bot-type-split?${p}`,         loadBotTypeSplit);
    Charts.loadChart(`bot-human-daily?${p}`,        loadBotHumanDaily);
    Charts.loadChart(`response-codes-bot-human?${p}`, loadResponseCodes);
    Charts.loadChart(`bot-human-hourly?${p}`,       loadHourlyActivity);
    Charts.loadChart(`top-bot-ips?${p}`,            loadTopBotIps);
}

initToggleBots(loadAllCharts);
