import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/utils.js', () => ({
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    escapeHtml:      vi.fn((s) => s),
    uaRequestsUrl:   vi.fn((ua) => `/ua-requests?ua=${ua}`),
}));

vi.mock('../../main/resources/static/js/refresh.js', () => ({
    initRefresh: vi.fn(),
}));

import { loadAllCharts } from '../../main/resources/static/js/automated.js';

async function flushPromises() {
    for (let i = 0; i < 10; i++) await Promise.resolve();
}

const HTML = `
    <span id="automatedUasCount"></span>
    <table><tbody id="automatedUas"><tr><td colspan="2">Loading...</td></tr></tbody></table>
`;

const SAMPLE_RESPONSE = [
    { name: 'curl/8.0', count: 300 },
    { name: 'python-requests/2.31', count: 50 },
];

describe('automated page user agents', () => {
    beforeEach(() => {
        document.body.innerHTML = HTML;
        vi.clearAllMocks();
    });

    it('renders one row per fully-automated user agent, in the order received', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve(SAMPLE_RESPONSE),
        }));
        loadAllCharts();
        await flushPromises();

        const tbody = document.getElementById('automatedUas');
        expect(tbody.textContent).toContain('curl/8.0');
        expect(tbody.textContent).toContain('python-requests/2.31');
        expect(tbody.querySelector('a').getAttribute('href')).toBe('/ua-requests?ua=curl/8.0');
        expect(document.getElementById('automatedUasCount').textContent).toBe('(2 rows)');
    });

    it('shows an empty state when every user agent has human evidence', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([]),
        }));
        loadAllCharts();
        await flushPromises();

        expect(document.getElementById('automatedUas').textContent).toContain('No fully automated user agents');
    });

    it('shows a failure message when the fetch rejects', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')));
        loadAllCharts();
        await flushPromises();

        expect(document.getElementById('automatedUas').textContent).toContain('Failed to load data.');
    });
});
