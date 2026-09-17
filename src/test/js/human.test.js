import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/utils.js', () => ({
    buildBaseParams:  vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    escapeHtml:       vi.fn((s) => s),
    formatTimestamp:  vi.fn((iso) => iso),
    stackedBar:       vi.fn(() => '<div class="bar"></div>'),
}));

vi.mock('../../main/resources/static/js/dashboard-charts.js', () => ({
    loadCoreCharts: vi.fn(),
}));

vi.mock('../../main/resources/static/js/refresh.js', () => ({
    initRefresh: vi.fn(),
}));

import { loadAllCharts } from '../../main/resources/static/js/human.js';
import { flushPromises } from './test-helpers.js';

const HTML = `
    <span id="humanUnknownUasCount"></span>
    <table><tbody id="humanUnknownUas"><tr><td colspan="4">Loading...</td></tr></tbody></table>
`;

const SAMPLE_RESPONSE = [
    { timestamp: '2026-08-24T11:06:46Z', userAgent: 'SomeUnknownClient/9.9', uriStem: '/', hit: 1, miss: 0, function: 0, error: 0 },
];

describe('human page unknown user agents', () => {
    beforeEach(() => {
        document.body.innerHTML = HTML;
        vi.clearAllMocks();
    });

    it('renders one row per unknown-UA request', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve(SAMPLE_RESPONSE),
        }));
        loadAllCharts();
        await flushPromises();

        const tbody = document.getElementById('humanUnknownUas');
        expect(tbody.textContent).toContain('SomeUnknownClient/9.9');
        expect(tbody.textContent).toContain('/');
        expect(tbody.querySelector('.bar')).not.toBeNull();
        expect(document.getElementById('humanUnknownUasCount').textContent).toBe('(1 rows)');
    });

    it('shows an empty state when there are no unknown user agents', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([]),
        }));
        loadAllCharts();
        await flushPromises();

        expect(document.getElementById('humanUnknownUas').textContent).toContain('No unknown user agents');
    });

    it('shows a failure message when the fetch rejects', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')));
        loadAllCharts();
        await flushPromises();

        expect(document.getElementById('humanUnknownUas').textContent).toContain('Failed to load data.');
    });
});
