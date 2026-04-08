(function () {
    'use strict';

    const from = document.querySelector('meta[name="cf-from"]').content;
    const to   = document.querySelector('meta[name="cf-to"]').content;

    const params = new URLSearchParams({ from: Charts.toDateParam(from), to: Charts.toDateParam(to) });

    function stackedBar(canvasId, data) {
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
                scales: {
                    x: { stacked: true },
                    y: { stacked: true, beginAtZero: true }
                }
            }
        });
    }

    const p = params.toString();
    Charts.loadChart(`ua-groups?${p}`,        data => Charts.pie('chartUaGroups',     data, null));
    Charts.loadChart(`ua-names-split?${p}`,   data => Charts.horizontalStackedBar('chartUaNames', data,
        d => `/ua-detail?ua=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`countries?${p}`,        data => Charts.horizontalStackedBar('chartCountries', data,
        item => `/country-detail?country=${encodeURIComponent(item.code)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`top-urls-split?${p}`,   data => Charts.horizontalStackedBar('chartTopUrls',  data));
    Charts.loadChart(`referers?${p}`,         data => Charts.horizontalBar('chartReferers',         data));
    Charts.loadChart(`requests-per-day?${p}`, data => stackedBar(          'chartRequestsPerDay',   data));
})();
