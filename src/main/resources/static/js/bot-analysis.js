'use strict';

import { Charts } from './charts.js';
import { buildBaseParams, initToggleBots } from './utils.js';

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

export function loadAllCharts() {
    const chart = Chart.getChart('chartBotHumanDaily');
    if (chart) chart.destroy();

    const p = buildBaseParams({});
    Charts.loadChart(`bot-human-daily?${p}`, loadBotHumanDaily);
}

initToggleBots(loadAllCharts);
