'use strict';

const Charts = {};

Charts.COLORS = {
    blue:   'rgba(54, 162, 235, 0.8)',
    red:    'rgba(220, 53, 69, 0.8)',
    orange: 'rgba(253, 126, 20, 0.8)',
    green:  'rgba(40, 167, 69, 0.8)',
    purple: 'rgba(111, 66, 193, 0.8)',
};

Charts.RESULT_TYPE_COLORS = {
    'Hit':                       Charts.COLORS.green,
    'Miss':                      Charts.COLORS.blue,
    'FunctionGeneratedResponse': Charts.COLORS.orange,
    'FunctionExecutionError':    Charts.COLORS.orange,
    'FunctionThrottledError':    Charts.COLORS.orange,
    'Error':                     Charts.COLORS.red,
    'Redirect':                  Charts.COLORS.purple,
};

Charts.PALETTE = [
    'rgba(54, 162, 235, 0.8)',
    'rgba(255, 193, 7, 0.8)',
    'rgba(40, 167, 69, 0.8)',
    'rgba(220, 53, 69, 0.8)',
    'rgba(111, 66, 193, 0.8)',
    'rgba(253, 126, 20, 0.8)',
    'rgba(32, 201, 151, 0.8)',
    'rgba(232, 62, 140, 0.8)',
    'rgba(102, 16, 242, 0.8)',
    'rgba(13, 202, 240, 0.8)',
];

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
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => d.name ?? '(unknown)'),
            datasets: [{
                label: 'Requests',
                data: data.map(d => d.count),
                backgroundColor: Charts.COLORS.blue,
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
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
        options: { responsive: true }
    });
};

Charts.resultTypeDatasets = function (data) {
    return [
        { label: 'Hit',      data: data.map(d => d.hit),      backgroundColor: Charts.COLORS.green  },
        { label: 'Miss',     data: data.map(d => d.miss),     backgroundColor: Charts.COLORS.blue   },
        { label: 'Function', data: data.map(d => d.function), backgroundColor: Charts.COLORS.orange },
        { label: 'Redirect', data: data.map(d => d.redirect), backgroundColor: Charts.COLORS.purple },
        { label: 'Error',    data: data.map(d => d.error),    backgroundColor: Charts.COLORS.red    },
    ];
};

Charts.horizontalStackedBar = function (canvasId, data, urlFn) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => d.name),
            datasets: Charts.resultTypeDatasets(data),
        },
        options: {
            indexAxis: 'y',
            responsive: true,
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

export { Charts };

Charts.linePerDay = function (canvasId, data) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.map(d => d.day),
            datasets: [
                { label: 'Hit',      data: data.map(d => d.hit),      borderColor: Charts.COLORS.green,  backgroundColor: 'transparent', tension: 0.3 },
                { label: 'Miss',     data: data.map(d => d.miss),     borderColor: Charts.COLORS.blue,   backgroundColor: 'transparent', tension: 0.3 },
                { label: 'Function', data: data.map(d => d.function), borderColor: Charts.COLORS.orange, backgroundColor: 'transparent', tension: 0.3 },
                { label: 'Redirect', data: data.map(d => d.redirect), borderColor: Charts.COLORS.purple, backgroundColor: 'transparent', tension: 0.3 },
                { label: 'Error',    data: data.map(d => d.error),    borderColor: Charts.COLORS.red,    backgroundColor: 'transparent', tension: 0.3 },
            ]
        },
        options: {
            responsive: true,
            scales: { y: { beginAtZero: true } }
        }
    });
};