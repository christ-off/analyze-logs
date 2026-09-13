import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/charts.js', () => ({
    Charts: {
        loadChart:             vi.fn(),
        pie:                   vi.fn(),
        horizontalStackedBar:  vi.fn(),
        horizontalBar:         vi.fn(),
        stackedBarByDay:       vi.fn(),
        toDateParam:           vi.fn(d => (d ? d.substring(0, 10) : '')),
    },
}));

vi.mock('../../main/resources/static/js/utils.js', () => ({
    readMeta:       vi.fn(() => '2026-01-01T00:00:00Z'),
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    detailUrl:      vi.fn((path, params) => `${path}?${new URLSearchParams(params).toString()}`),
}));

import { Charts } from '../../main/resources/static/js/charts.js';
import { loadAllCharts } from '../../main/resources/static/js/dashboard.js';

// URL-builder wiring for the shared ua-names/countries/top-urls/referers/requests-per-day
// charts is exercised in dashboard-charts.test.js, next to loadCoreCharts() itself. This file
// only covers what's specific to the main dashboard: the two extra pies plus delegation to it.
describe('loadAllCharts', () => {
    beforeEach(() => vi.clearAllMocks());

    it('calls Charts.loadChart exactly 7 times', () => {
        loadAllCharts();
        expect(Charts.loadChart).toHaveBeenCalledTimes(7);
    });

    it('loads the UA groups and platforms pies', () => {
        loadAllCharts();
        const endpoints = Charts.loadChart.mock.calls.map(([endpoint]) => endpoint.split('?')[0]);
        expect(endpoints.slice(0, 2)).toEqual(['ua-groups', 'platforms']);
    });
});
