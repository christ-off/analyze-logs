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

    function stackedBar(canvasId, data) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.day),
                datasets: [
                    {
                        label: 'Success (2xx/3xx)',
                        data: data.map(d => d.success),
                        backgroundColor: COLORS.green,
                    },
                    {
                        label: 'Client error (4xx)',
                        data: data.map(d => d.clientError),
                        backgroundColor: COLORS.orange,
                    },
                    {
                        label: 'Server error (5xx)',
                        data: data.map(d => d.serverError),
                        backgroundColor: COLORS.red,
                    },
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

    loadChart('ua-names',         data => horizontalBar('chartUaNames',        data));
    loadChart('countries',        data => horizontalBar('chartCountries',       data));
    loadChart('uri-stems',        data => horizontalBar('chartUriStems',        data));
    loadChart('requests-per-day', data => stackedBar(   'chartRequestsPerDay',  data));
})();