import { describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/charts.js', () => ({
    Charts: {
        loadChart:            vi.fn(),
        pie:                  vi.fn(),
        horizontalStackedBar: vi.fn(),
        stackedBarByDay:      vi.fn(),
        toDateParam:          vi.fn(d => (d ? d.substring(0, 10) : '')),
    },
}));

vi.mock('../../main/resources/static/js/utils.js', () => ({
    readMeta:        vi.fn(() => 'Chrome / Windows'),
    escapeHtml:      vi.fn(s => s),
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    initToggleBots:  vi.fn(),   // no-op: don't call loadAllCharts on module load
    resultTotal:     vi.fn(),
    stackedBar:      vi.fn(),
    uaRequestsUrl:   vi.fn(),
}));

import { minChromeVersionWithHumanTraffic } from '../../main/resources/static/js/ua-detail.js';

function stat(name, humanRequests, totalRequests) {
    return { name, humanRequests, totalRequests };
}

describe('minChromeVersionWithHumanTraffic', () => {
    it('returns the lowest major version with any aggregated human evidence', () => {
        const humanStats = [
            stat('...Chrome/120.0.0.0...', 0, 2082),
            stat('...Chrome/148.0.0.0...', 6, 1036),   // 0.6% human
            stat('...Chrome/120.0.0.0 (no webkit)', 0, 387),
            stat('...Chrome/151.0.0.0...', 52, 67),    // 77.6% human
            stat('...Chrome/128.0.0.0...', 0, 67),
            stat('...Chrome/152.0.0.0...', 12, 20),    // 60% human
            stat('...Chrome/150.0.0.0...', 7, 7),      // 100% human
            stat('...Chrome/154.0.5702.73...QIHU 360EE', 0, 1),
        ];

        expect(minChromeVersionWithHumanTraffic(humanStats)).toBe(148);
    });

    it('aggregates multiple raw UA strings sharing the same major version', () => {
        const humanStats = [
            stat('...Chrome/120.0.0.0...', 0, 10),
            stat('...Chrome/120.5.1.2...', 3, 5), // same major version — combined human > 0
        ];

        expect(minChromeVersionWithHumanTraffic(humanStats)).toBe(120);
    });

    it('ignores versions with zero human requests', () => {
        const humanStats = [
            stat('...Chrome/120...', 0, 100),
            stat('...Chrome/151...', 5, 10),
        ];

        expect(minChromeVersionWithHumanTraffic(humanStats)).toBe(151);
    });

    it('returns null when no version has any human traffic', () => {
        const humanStats = [stat('...Chrome/120...', 0, 100), stat('...Chrome/151...', 0, 10)];
        expect(minChromeVersionWithHumanTraffic(humanStats)).toBeNull();
    });

    it('returns null for an empty list', () => {
        expect(minChromeVersionWithHumanTraffic([])).toBeNull();
    });

    it('ignores raw UA strings without a Chrome version', () => {
        const humanStats = [stat('Mozilla/5.0 (compatible; Googlebot/2.1)', 5, 5)];
        expect(minChromeVersionWithHumanTraffic(humanStats)).toBeNull();
    });
});
