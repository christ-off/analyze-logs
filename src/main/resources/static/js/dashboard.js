(function () {
    'use strict';

    const from = document.querySelector('meta[name="cf-from"]').content;
    const to   = document.querySelector('meta[name="cf-to"]').content;

    const params = new URLSearchParams({ from: Charts.toDateParam(from), to: Charts.toDateParam(to) });

    function resultTypeDatasets(data) {
        return [
            { label: 'Hit',      data: data.map(d => d.hit),      backgroundColor: Charts.COLORS.green  },
            { label: 'Miss',     data: data.map(d => d.miss),     backgroundColor: Charts.COLORS.blue   },
            { label: 'Function', data: data.map(d => d.function), backgroundColor: Charts.COLORS.orange },
            { label: 'Redirect', data: data.map(d => d.redirect), backgroundColor: Charts.COLORS.purple },
            { label: 'Error',    data: data.map(d => d.error),    backgroundColor: Charts.COLORS.red    },
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
                onClick: (event, elements) => {
                    if (!elements.length) return;
                    const uaName = data[elements[0].index].name;
                    globalThis.location.href =
                        `/ua-detail?ua=${encodeURIComponent(uaName)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`;
                },
                onHover: (event, elements) => {
                    event.native.target.style.cursor = elements.length ? 'pointer' : 'default';
                },
                scales: {
                    x: { stacked: true, beginAtZero: true },
                    y: { stacked: true }
                }
            }
        });
    }

    const p = params.toString();
    Charts.loadChart(`ua-names-split?${p}`,   data => horizontalStackedBar('chartUaNames',      data));
    Charts.loadChart(`countries?${p}`,        data => Charts.horizontalBar('chartCountries',    data,
        item => `/country-detail?country=${encodeURIComponent(item.code)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`uri-stems?${p}`,        data => Charts.horizontalBar('chartUriStems',     data));
    Charts.loadChart(`top-uri-stems?${p}`,    data => Charts.horizontalBar('chartTopUriStems',  data));
    Charts.loadChart(`referers?${p}`,         data => Charts.horizontalBar('chartReferers',     data));
    Charts.loadChart(`requests-per-day?${p}`, data => stackedBar(          'chartRequestsPerDay', data));
})();