(function () {
    'use strict';

    const from = document.querySelector('meta[name="cf-from"]').content;
    const to   = document.querySelector('meta[name="cf-to"]').content;

    // Convert ISO instant string to "yyyy-MM-dd" for API params (date portion only)
    function toDateParam(iso) {
        return iso.substring(0, 10);
    }

    const params = new URLSearchParams({ from: toDateParam(from), to: toDateParam(to) });

    const COLORS = {
        blue:   'rgba(54, 162, 235, 0.8)',
        red:    'rgba(220, 53, 69, 0.8)',
        orange: 'rgba(253, 126, 20, 0.8)',
        green:  'rgba(40, 167, 69, 0.8)',
        purple: 'rgba(111, 66, 193, 0.8)',
    };

    function horizontalBar(canvasId, data) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.name ?? '(unknown)'),
                datasets: [{
                    label: 'Requests',
                    data: data.map(d => d.count),
                    backgroundColor: COLORS.blue,
                }]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                plugins: { legend: { display: false } },
                scales: { x: { beginAtZero: true } }
            }
        });
    }

    function resultTypeDatasets(data) {
        return [
            { label: 'Hit',      data: data.map(d => d.hit),      backgroundColor: COLORS.green  },
            { label: 'Miss',     data: data.map(d => d.miss),     backgroundColor: COLORS.blue   },
            { label: 'Function', data: data.map(d => d.function), backgroundColor: COLORS.orange },
            { label: 'Redirect', data: data.map(d => d.redirect), backgroundColor: COLORS.purple },
            { label: 'Error',    data: data.map(d => d.error),    backgroundColor: COLORS.red    },
        ];
    }

    function stackedBar(canvasId, data) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.day),
                datasets: resultTypeDatasets(data),
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

    function horizontalStackedBar(canvasId, data) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.name),
                datasets: resultTypeDatasets(data),
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                scales: {
                    x: { stacked: true, beginAtZero: true },
                    y: { stacked: true }
                }
            }
        });
    }

    async function loadChart(endpoint, render) {
        try {
            const resp = await fetch(`/api/${endpoint}?${params}`);
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const data = await resp.json();
            render(data);
        } catch (e) {
            console.error(`Failed to load ${endpoint}:`, e);
        }
    }

    loadChart('ua-names-split',   data => horizontalStackedBar('chartUaNames', data));
    loadChart('countries',        data => horizontalBar('chartCountries',       data));
    loadChart('uri-stems',        data => horizontalBar('chartUriStems',        data));
    loadChart('requests-per-day', data => stackedBar(   'chartRequestsPerDay',  data));
})();