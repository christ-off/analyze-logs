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

import { maxChromeVersionWithZeroHuman } from '../../main/resources/static/js/ua-detail.js';

function stat(name, humanRequests, totalRequests) {
    return { name, humanRequests, totalRequests };
}

describe('maxChromeVersionWithZeroHuman', () => {
    it('returns the highest major version whose aggregated human proportion is exactly 0%', () => {
        const humanStats = [
            stat('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36', 0, 243),
            stat('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 0, 183),
            stat('Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0', 0, 20),
            stat('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.81 Safari/537.36', 0, 9),
            stat('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36', 9, 9),
            stat('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36', 5, 7),
            stat('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.70 Safari/537.36', 0, 5),
            stat('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36', 4, 4),
            stat('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6400.44 Safari/537.36', 0, 4),
        ];

        expect(maxChromeVersionWithZeroHuman(humanStats)).toBe(148);
    });

    it('aggregates multiple raw UA strings sharing the same major version', () => {
        const humanStats = [
            stat('...Chrome/120.0.0.0...', 0, 10),
            stat('...Chrome/120.5.1.2...', 0, 5), // same major version, still 0% combined
        ];

        expect(maxChromeVersionWithZeroHuman(humanStats)).toBe(120);
    });

    it('excludes a version with any human evidence, even if mostly 0%', () => {
        const humanStats = [
            stat('...Chrome/120...', 0, 100),
            stat('...Chrome/120...', 1, 1), // combined: 1/101 human — not 0%
        ];

        expect(maxChromeVersionWithZeroHuman(humanStats)).toBeNull();
    });

    it('returns null when no version has 0% human proportion', () => {
        const humanStats = [stat('...Chrome/151...', 9, 9)];
        expect(maxChromeVersionWithZeroHuman(humanStats)).toBeNull();
    });

    it('returns null for an empty list', () => {
        expect(maxChromeVersionWithZeroHuman([])).toBeNull();
    });

    it('ignores raw UA strings without a Chrome version', () => {
        const humanStats = [stat('Mozilla/5.0 (compatible; Googlebot/2.1)', 0, 5)];
        expect(maxChromeVersionWithZeroHuman(humanStats)).toBeNull();
    });
});
