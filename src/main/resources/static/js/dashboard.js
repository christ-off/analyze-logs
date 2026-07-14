import { Charts } from './charts.js';
import { readMeta, buildBaseParams, initToggleBots } from './utils.js';

const from = readMeta('cf-from');
const to   = readMeta('cf-to');

const CHART_IDS = [
    'chartUaGroups', 'chartPlatforms', 'chartUaNames', 'chartCountries',
    'chartTopUrls', 'chartReferers', 'chartRequestsPerDay', 'chartTrafficCategories',
];

export function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({});
    Charts.loadChart(`ua-groups?${p}`,        data => Charts.pie('chartUaGroups',     data, null));
    Charts.loadChart(`platforms?${p}`,        data => Charts.pie('chartPlatforms',    data, null));
    Charts.loadChart(`ua-names-split?${p}`,   data => Charts.horizontalStackedBar('chartUaNames', data,
        d => `/ua-detail?ua=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`countries?${p}`,        data => Charts.horizontalStackedBar('chartCountries', data,
        item => `/country-detail?country=${encodeURIComponent(item.code)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`top-urls-split?${p}`,   data => Charts.horizontalStackedBar('chartTopUrls',  data,
        d => `/url-detail?url=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`referers?${p}`,         data => Charts.horizontalBar('chartReferers', data,
        d => `/referer-detail?referer=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
    Charts.loadChart(`requests-per-day?${p}`, data => Charts.stackedBarByDay('chartRequestsPerDay',   data));
    Charts.loadChart(`traffic-categories?${p}`, data => Charts.horizontalStackedBar('chartTrafficCategories', data,
        d => `/category-detail?category=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`));
}

initToggleBots(loadAllCharts);

// ── Refresh from S3 with progress bar ──────────────────────────────────────
export function initRefresh() {
    const btn        = document.getElementById('refreshBtn');
    const progressEl = document.getElementById('refreshProgress');
    const bar        = document.getElementById('refreshBar');
    const status     = document.getElementById('refreshStatus');
    // Read CSRF token from the hidden input Thymeleaf injects via th:action
    const csrfInput  = document.querySelector('#refreshForm input[name="_csrf"]');
    const csrfToken  = csrfInput?.value;
    const csrfHeader = 'X-CSRF-TOKEN';

    if (!btn) return;

    let pollTimer = null;

    function setWidth(pct) {
        bar.style.width = pct + '%';
        bar.setAttribute('aria-valuenow', pct);
    }

    function showProgress() {
        btn.disabled = true;
        progressEl.classList.remove('d-none');
        setWidth(0);
        status.textContent = 'Connecting…';
    }

    function hideProgress() {
        btn.disabled = false;
        progressEl.classList.add('d-none');
        bar.className = 'progress-bar progress-bar-striped progress-bar-animated';
        setWidth(0);
    }

    function poll() {
        fetch('/refresh/progress')
            .then(r => r.json())
            .then(p => {
                if (p.total > 0) {
                    const pct = Math.round(p.processed / p.total * 100);
                    setWidth(pct);
                    status.textContent = `${p.processed} / ${p.total} files…`;
                } else {
                    status.textContent = 'Listing S3 keys…';
                }

                if (p.done) {
                    clearInterval(pollTimer);
                    pollTimer = null;
                    setWidth(100);
                    if (p.error) {
                        bar.classList.remove('progress-bar-striped', 'progress-bar-animated');
                        bar.classList.add('bg-danger');
                        status.textContent = 'Error: ' + p.error;
                        setTimeout(hideProgress, 5000);
                    } else {
                        bar.classList.remove('progress-bar-striped', 'progress-bar-animated');
                        bar.classList.add('bg-success');
                        status.textContent =
                            `Done — fetched: ${p.fetched}, skipped: ${p.skipped}, failed: ${p.failed}`;
                        loadAllCharts();
                        setTimeout(hideProgress, 5000);
                    }
                }
            })
            .catch(() => { status.textContent = 'Could not reach server…'; });
    }

    btn.addEventListener('click', () => {
        const headers = {};
        if (csrfToken) headers[csrfHeader] = csrfToken;

        fetch('/refresh', { method: 'POST', headers })
            .then(r => {
                if (r.status === 202 || r.status === 409) {
                    showProgress();
                    pollTimer = setInterval(poll, 500);
                } else {
                    status.textContent = 'Start failed (HTTP ' + r.status + ')';
                }
            })
            .catch(() => { status.textContent = 'Network error'; });
    });
}

initRefresh();
