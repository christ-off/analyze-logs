import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/charts.js', () => ({
    Charts: {
        loadChart:             vi.fn(),
        pie:                   vi.fn(),
        horizontalStackedBar:  vi.fn(),
        stackedBarByDay:       vi.fn(),
    },
}));

vi.mock('../../main/resources/static/js/utils.js', () => ({
    readMeta:        vi.fn(name => (name === 'cf-country' ? 'FR' : '2026-01-01T00:00:00Z')),
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    detailUrl:       vi.fn((path, params) => `${path}?${new URLSearchParams(params).toString()}`),
}));

import { Charts } from '../../main/resources/static/js/charts.js';
import { loadAllCharts } from '../../main/resources/static/js/country-detail.js';

describe('country detail charts', () => {
    beforeEach(() => vi.clearAllMocks());

    it('loads the four charts from the country-detail endpoints', () => {
        loadAllCharts();
        const endpoints = Charts.loadChart.mock.calls.map(([endpoint]) => endpoint.split('?')[0]);
        expect(endpoints).toEqual([
            'country-detail/ua-split', 'country-detail/result-types',
            'country-detail/url-split', 'country-detail/requests-per-day',
        ]);
    });

    it('links the top user agents bars to /ua-detail, like the dashboard', () => {
        loadAllCharts();
        const [, renderFn] = Charts.loadChart.mock.calls[0]; // ua-split
        renderFn([{ name: 'Chrome / Windows', hit: 1, miss: 0, function: 0, error: 0 }]);
        const [canvasId, , urlFn] = Charts.horizontalStackedBar.mock.calls[0];
        expect(canvasId).toBe('chartUaNames');
        expect(urlFn({ name: 'Chrome / Windows' })).toMatch(/\/ua-detail\?ua=Chrome/);
    });
});
