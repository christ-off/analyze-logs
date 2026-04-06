(function () {
    'use strict';

    const from = document.querySelector('meta[name="cf-from"]').content;
    const to   = document.querySelector('meta[name="cf-to"]').content;
    const ua   = document.querySelector('meta[name="cf-ua"]').content;

    function toDateParam(iso) { return iso.substring(0, 10); }

    const COLORS = {
        blue:   'rgba(54, 162, 235, 0.8)',
        red:    'rgba(220, 53, 69, 0.8)',
        orange: 'rgba(253, 126, 20, 0.8)',
        green:  'rgba(40, 167, 69, 0.8)',
        purple: 'rgba(111, 66, 193, 0.8)',
    };

    const RESULT_TYPE_COLORS = {
        'Hit':                       COLORS.green,
        'Miss':                      COLORS.blue,
        'FunctionGeneratedResponse': COLORS.orange,
        'FunctionExecutionError':    COLORS.orange,
        'FunctionThrottledError':    COLORS.orange,
        'Error':                     COLORS.red,
        'Redirect':                  COLORS.purple,
    };

    // Distinct palette for arbitrary-label pie charts (e.g. countries)
    const PALETTE = [
        'rgba(54, 162, 235, 0.8)',   // blue
        'rgba(255, 193, 7, 0.8)',    // amber
        'rgba(40, 167, 69, 0.8)',    // green
        'rgba(220, 53, 69, 0.8)',    // red
        'rgba(111, 66, 193, 0.8)',   // purple
        'rgba(253, 126, 20, 0.8)',   // orange
        'rgba(32, 201, 151, 0.8)',   // teal
        'rgba(232, 62, 140, 0.8)',   // pink
        'rgba(102, 16, 242, 0.8)',   // indigo
        'rgba(13, 202, 240, 0.8)',   // cyan
    ];

    function pie(canvasId, data, colorMap) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        new Chart(ctx, {
            type: 'pie',
            data: {
                labels: data.map(d => d.name ?? '(unknown)'),
                datasets: [{
                    data: data.map(d => d.count),
                    backgroundColor: data.map((d, i) =>
                        colorMap ? (colorMap[d.name] ?? COLORS.blue) : PALETTE[i % PALETTE.length]
                    ),
                }]
            },
            options: { responsive: true }
        });
    }

    function horizontalBar(canvasId, data) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.name ?? '(unknown)'),
                datasets: [{ label: 'Requests', data: data.map(d => d.count),
                             backgroundColor: COLORS.blue }]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                plugins: { legend: { display: false } },
                scales: { x: { beginAtZero: true } }
            }
        });
    }

    function linePerDay(canvasId, data) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        new Chart(ctx, {
            type: 'line',
            data: {
                labels: data.map(d => d.day),
                datasets: [
                    { label: 'Hit',      data: data.map(d => d.hit),      borderColor: COLORS.green,  backgroundColor: 'transparent', tension: 0.3 },
                    { label: 'Miss',     data: data.map(d => d.miss),     borderColor: COLORS.blue,   backgroundColor: 'transparent', tension: 0.3 },
                    { label: 'Function', data: data.map(d => d.function), borderColor: COLORS.orange, backgroundColor: 'transparent', tension: 0.3 },
                    { label: 'Redirect', data: data.map(d => d.redirect), borderColor: COLORS.purple, backgroundColor: 'transparent', tension: 0.3 },
                    { label: 'Error',    data: data.map(d => d.error),    borderColor: COLORS.red,    backgroundColor: 'transparent', tension: 0.3 },
                ]
            },
            options: {
                responsive: true,
                scales: { y: { beginAtZero: true } }
            }
        });
    }

    async function loadChart(endpoint, render) {
        try {
            const resp = await fetch(`/api/${endpoint}`);
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            render(await resp.json());
        } catch (e) {
            console.error(`Failed to load ${endpoint}:`, e);
        }
    }

    const uaParams = new URLSearchParams({ ua, from: toDateParam(from), to: toDateParam(to) });

    loadChart(`ua-detail/result-types?${uaParams}`,    d => pie('chartResultTypes',         d, RESULT_TYPE_COLORS));
    loadChart(`ua-detail/countries?${uaParams}`,       d => pie('chartCountries',            d, null));
    loadChart(`ua-detail/uri-stems?${uaParams}`,       d => horizontalBar('chartUriStems',   d));
    loadChart(`ua-detail/requests-per-day?${uaParams}`, d => linePerDay('chartRequestsPerDay', d));
})();
