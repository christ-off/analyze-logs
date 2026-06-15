'use strict';

const Charts = {};

Charts.COLORS = {
    blue:   'rgba(56, 189, 248, 0.85)',
    red:    'rgba(225, 29, 72, 0.85)',
    orange: 'rgba(245, 158, 11, 0.85)',
    green:  'rgba(16, 185, 129, 0.85)',
};

Charts.ACCENT = 'rgba(13, 148, 136, 0.85)';

Charts.RESULT_TYPE_COLORS = {
    'Hit':                       Charts.COLORS.green,
    'Miss':                      Charts.COLORS.blue,
    'Filtered':                  Charts.COLORS.orange,
    'Error':                     Charts.COLORS.red,
};

Charts.PALETTE = [
    'rgba(13, 148, 136, 0.85)',
    'rgba(99, 102, 241, 0.85)',
    'rgba(245, 158, 11, 0.85)',
    'rgba(225, 29, 72, 0.85)',
    'rgba(14, 165, 233, 0.85)',
    'rgba(132, 204, 22, 0.85)',
    'rgba(168, 85, 247, 0.85)',
    'rgba(249, 115, 22, 0.85)',
    'rgba(100, 116, 139, 0.85)',
    'rgba(45, 212, 191, 0.85)',
];

/* Global Chart.js theme — typography, grid, tooltips, legends */
Charts.applyTheme = function () {
    if (typeof Chart === 'undefined' || !Chart.defaults?.font) return;
    Chart.defaults.font.family = "'IBM Plex Sans', -apple-system, 'Segoe UI', sans-serif";
    Chart.defaults.font.size = 11.5;
    Chart.defaults.color = '#64748b';
    Chart.defaults.scale.grid.color = 'rgba(100, 116, 139, 0.12)';
    Chart.defaults.scale.border = { display: false };
    Chart.defaults.scale.ticks.padding = 6;
    Chart.defaults.elements.bar.borderRadius = 3;
    Chart.defaults.elements.bar.borderSkipped = false;
    Chart.defaults.maxBarThickness = 18;
    Chart.defaults.plugins.legend.labels.usePointStyle = true;
    Chart.defaults.plugins.legend.labels.pointStyle = 'rectRounded';
    Chart.defaults.plugins.legend.labels.boxWidth = 9;
    Chart.defaults.plugins.legend.labels.boxHeight = 9;
    Chart.defaults.plugins.tooltip.backgroundColor = 'rgba(11, 17, 32, 0.92)';
    Chart.defaults.plugins.tooltip.titleFont = { family: "'IBM Plex Mono', monospace", size: 11 };
    Chart.defaults.plugins.tooltip.bodyFont = { family: "'IBM Plex Mono', monospace", size: 11 };
    Chart.defaults.plugins.tooltip.padding = 10;
    Chart.defaults.plugins.tooltip.cornerRadius = 6;
    Chart.defaults.plugins.tooltip.boxPadding = 4;
};

Charts.applyTheme();

Charts.toDateParam = function (iso) {
    return iso.substring(0, 10);
};

Charts.loadChart = async function (endpoint, render) {
    try {
        const resp = await fetch(`/api/${endpoint}`);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        render(await resp.json());
    } catch (e) {
        console.error(`Failed to load ${endpoint}:`, e);
    }
};

Charts.horizontalBar = function (canvasId, data, urlFn) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    const MAX = 35;
    const truncate = s => s.length > MAX ? s.slice(0, MAX) + '…' : s;
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => truncate(d.name ?? '(unknown)')),
            datasets: [{
                label: 'Requests',
                data: data.map(d => d.count),
                backgroundColor: Charts.ACCENT,
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { x: { beginAtZero: true } },
            ...(urlFn ? {
                onClick: (event, elements) => {
                    if (!elements.length) return;
                    globalThis.location.href = urlFn(data[elements[0].index]);
                },
                onHover: (event, elements) => {
                    event.native.target.style.cursor = elements.length ? 'pointer' : 'default';
                },
            } : {})
        }
    });
};

Charts.pie = function (canvasId, data, colorMap) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    new Chart(ctx, {
        type: 'pie',
        data: {
            labels: data.map(d => d.name ?? '(unknown)'),
            datasets: [{
                data: data.map(d => d.count),
                backgroundColor: data.map((d, i) =>
                    colorMap ? (colorMap[d.name] ?? Charts.COLORS.blue) : Charts.PALETTE[i % Charts.PALETTE.length]
                ),
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '58%',
            plugins: {
                legend: {
                    position: 'right',
                    labels: {
                        generateLabels: function (chart) {
                            const dataset = chart.data.datasets[0];
                            const total = dataset.data.reduce((a, b) => a + b, 0);
                            return chart.data.labels.map((label, i) => {
                                const pct = total > 0 ? ((dataset.data[i] / total) * 100).toFixed(1) : '0.0';
                                return {
                                    text: `${label} (${pct}%)`,
                                    fillStyle: dataset.backgroundColor[i],
                                    strokeStyle: dataset.backgroundColor[i],
                                    hidden: false,
                                    index: i,
                                };
                            });
                        }
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function (context) {
                            const total = context.dataset.data.reduce((a, b) => a + b, 0);
                            const pct = total > 0 ? ((context.parsed / total) * 100).toFixed(1) : '0.0';
                            return ` ${context.parsed.toLocaleString()} (${pct}%)`;
                        }
                    }
                }
            }
        }
    });
};

Charts.resultTypeDatasets = function (data) {
    return [
        { label: 'Hit',      data: data.map(d => d.hit),      backgroundColor: Charts.COLORS.green  },
        { label: 'Miss',     data: data.map(d => d.miss),     backgroundColor: Charts.COLORS.blue   },
        { label: 'Filtered', data: data.map(d => d.function), backgroundColor: Charts.COLORS.orange },
        { label: 'Error',    data: data.map(d => d.error),    backgroundColor: Charts.COLORS.red    },
    ];
};

Charts.horizontalStackedBar = function (canvasId, data, urlFn) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    const container = ctx.parentElement;
    container.style.position = 'relative';
    container.style.height = Math.max(200, data.length * 32) + 'px';
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => d.name),
            datasets: Charts.resultTypeDatasets(data),
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            ...(urlFn ? {
                onClick: (event, elements) => {
                    if (!elements.length) return;
                    globalThis.location.href = urlFn(data[elements[0].index]);
                },
                onHover: (event, elements) => {
                    event.native.target.style.cursor = elements.length ? 'pointer' : 'default';
                },
            } : {}),
            scales: {
                x: { stacked: true, beginAtZero: true },
                y: { stacked: true }
            }
        }
    });
};

Charts.stackedBarByDay = function (canvasId, data) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => d.day),
            datasets: Charts.resultTypeDatasets(data),
        },
        options: {
            responsive: true,
            datasets: { bar: { maxBarThickness: 44 } },
            scales: {
                x: { stacked: true },
                y: { stacked: true, beginAtZero: true }
            }
        }
    });
};

Charts.lineByDay = function (canvasId, data) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;

    const days = [...new Set(data.map(d => d.day))].sort();
    const versions = [...new Set(data.map(d => d.protocolVersion))];

    const datasets = versions.map((version, i) => ({
        label: version,
        data: days.map(day => {
            const match = data.find(d => d.day === day && d.protocolVersion === version);
            return match ? match.count : 0;
        }),
        borderColor: Charts.PALETTE[i % Charts.PALETTE.length],
        backgroundColor: 'transparent',
        tension: 0.3,
    }));

    new Chart(ctx, {
        type: 'line',
        data: {
            labels: days,
            datasets,
        },
        options: {
            responsive: true,
            scales: {
                x: { type: 'category' },
                y: { beginAtZero: true }
            }
        }
    });
};

export { Charts };
