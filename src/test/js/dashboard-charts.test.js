import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/charts.js', () => ({
    Charts: {
        loadChart:            vi.fn(),
        horizontalStackedBar: vi.fn(),
        horizontalBar:        vi.fn(),
        stackedBarByDay:      vi.fn(),
    },
}));

vi.mock('../../main/resources/static/js/utils.js', () => ({
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    detailUrl:       vi.fn((path, params) => `${path}?${new URLSearchParams(params).toString()}`),
}));

import { Charts } from '../../main/resources/static/js/charts.js';
import { loadCoreCharts } from '../../main/resources/static/js/dashboard-charts.js';

describe('loadCoreCharts', () => {
    beforeEach(() => vi.clearAllMocks());

    it('hits the unprefixed endpoints by default', () => {
        loadCoreCharts();
        const endpoints = Charts.loadChart.mock.calls.map(([endpoint]) => endpoint.split('?')[0]);
        expect(endpoints).toEqual(['ua-names-split', 'countries', 'top-urls-split', 'referers', 'requests-per-day']);
    });

    it('prefixes every endpoint when a prefix is given', () => {
        loadCoreCharts('human-');
        const endpoints = Charts.loadChart.mock.calls.map(([endpoint]) => endpoint.split('?')[0]);
        expect(endpoints).toEqual([
            'human-ua-names-split', 'human-countries', 'human-top-urls-split', 'human-referers', 'human-requests-per-day',
        ]);
    });

    it('ua-names URL builder encodes the UA name', () => {
        loadCoreCharts();
        // loadChart is mocked so its callback never runs — call it to trigger horizontalStackedBar
        const [, renderFn] = Charts.loadChart.mock.calls[0]; // ua-names-split
        renderFn([]);
        const urlBuilder = Charts.horizontalStackedBar.mock.calls[0][2];
        expect(urlBuilder({ name: 'Chrome / Windows' }))
            .toMatch(/\/ua-detail\?ua=Chrome/);
    });

    it('countries URL builder encodes the country code', () => {
        loadCoreCharts();
        const [, renderFn] = Charts.loadChart.mock.calls[1]; // countries
        renderFn([]);
        const urlBuilder = Charts.horizontalStackedBar.mock.calls[0][2];
        expect(urlBuilder({ code: 'FR' }))
            .toMatch(/\/country-detail\?country=FR/);
    });

    it('top-urls URL builder encodes the URL path', () => {
        loadCoreCharts();
        const [, renderFn] = Charts.loadChart.mock.calls[2]; // top-urls-split
        renderFn([]);
        const urlBuilder = Charts.horizontalStackedBar.mock.calls[0][2];
        const result = urlBuilder({ name: '/my page' });
        expect(result).toMatch(/\/url-detail\?url=%2Fmy/);
        expect(result).toMatch(/page/);
    });
});
