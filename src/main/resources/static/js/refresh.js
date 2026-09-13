'use strict';

// Refresh-from-S3 button + progress bar, shared by every dashboard page's toolbar
// (fragments/toolbar.html). `onSuccess` re-loads that page's own charts once the fetch completes.
export function initRefresh(onSuccess) {
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
                        onSuccess?.();
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
