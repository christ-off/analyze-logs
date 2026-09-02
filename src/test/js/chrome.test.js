import { describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/charts.js', () => ({
    Charts: {
        loadChart: vi.fn(),
        pie:       vi.fn(),
        horizontalStackedBar: vi.fn(),
        stackedBarByDay:      vi.fn(),
    },
}));

vi.mock('../../main/resources/static/js/utils.js', () => ({
    buildBaseParams:       vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    initToggleBots:        vi.fn(),   // no-op: don't run loadAllCharts on module load
    resultTotal:           row => row.hit + row.miss + row['function'] + row.error,
    stackedBar:             vi.fn(),
    renderMinVersionBanner: vi.fn(),
}));

import { chromeMajorVersion, aggregateByVersion, sortVersions } from '../../main/resources/static/js/chrome.js';

function raw(name, hit, miss, fn, error) {
    return { name, hit, miss, function: fn, error };
}

function human(name, humanRequests, totalRequests) {
    return { name, humanRequests, totalRequests };
}

describe('chromeMajorVersion', () => {
    it('extracts the major version from a raw UA string', () => {
        expect(chromeMajorVersion('Mozilla/5.0 ... Chrome/120.0.0.0 Safari/537.36')).toBe(120);
    });

    it('returns null when the UA has no Chrome token', () => {
        expect(chromeMajorVersion('Mozilla/5.0 (compatible; Googlebot/2.1)')).toBeNull();
    });
});

describe('aggregateByVersion', () => {
    it('sums result-type counts across raw UA strings sharing the same major version, whatever the OS', () => {
        const rawUserAgents = [
            raw('Mozilla/5.0 (Windows NT 10.0) ... Chrome/120.0.0.0 Safari/537.36', 10, 2, 0, 1),
            raw('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ... Chrome/120.0.0.0 Safari/537.36', 5, 0, 0, 0),
            raw('Mozilla/5.0 (Linux; Android 10) ... Chrome/120.0.0.0 Mobile Safari/537.36', 3, 1, 0, 0),
            raw('Mozilla/5.0 (X11; Linux x86_64) ... Chrome/119.0.0.0 Safari/537.36', 1, 0, 0, 0),
        ];

        const result = aggregateByVersion(rawUserAgents, []);

        const v120 = result.find(r => r.version === 120);
        expect(v120.hit).toBe(18);
        expect(v120.miss).toBe(3);
        expect(v120.error).toBe(1);

        const v119 = result.find(r => r.version === 119);
        expect(v119.hit).toBe(1);
    });

    it('joins human-traffic stats onto the matching raw UA string and sums them per version', () => {
        const uaWin = 'Mozilla/5.0 (Windows NT 10.0) ... Chrome/120.0.0.0 Safari/537.36';
        const uaMac = 'Mozilla/5.0 (Macintosh) ... Chrome/120.0.0.0 Safari/537.36';
        const rawUserAgents = [raw(uaWin, 10, 0, 0, 0), raw(uaMac, 5, 0, 0, 0)];
        const humanStats = [human(uaWin, 8, 10), human(uaMac, 0, 5)];

        const [row] = aggregateByVersion(rawUserAgents, humanStats);

        expect(row.human).toBe(8);
        expect(row.total).toBe(15);
    });

    it('ignores raw UA strings without a matching Chrome version', () => {
        const rawUserAgents = [raw('Mozilla/5.0 (compatible; Googlebot/2.1)', 5, 0, 0, 0)];
        expect(aggregateByVersion(rawUserAgents, [])).toEqual([]);
    });
});

function versionRow(version, { hit = 0, miss = 0, fn = 0, error = 0, human = 0, total = 0 } = {}) {
    return { version, hit, miss, function: fn, error, human, total };
}

describe('sortVersions', () => {
    it('sorts the "version" column numerically, not lexicographically on the label text', () => {
        // Lexicographic order would read "10" before "9" — must sort as numbers.
        const versions = [versionRow(152), versionRow(9), versionRow(10), versionRow(2)];

        const result = sortVersions(versions, 'version', 'asc');

        expect(result.map(r => r.version)).toEqual([2, 9, 10, 152]);
    });

    it('sorts by requests (hit+miss+filtered+error total)', () => {
        const versions = [
            versionRow(120, { hit: 5 }),
            versionRow(119, { hit: 100 }),
            versionRow(118, { hit: 20 }),
        ];

        const result = sortVersions(versions, 'requests', 'desc');

        expect(result.map(r => r.version)).toEqual([119, 118, 120]);
    });

    it('sorts by proportion of human traffic, treating rows with no traffic as lowest', () => {
        const versions = [
            versionRow(120, { human: 8, total: 10 }),  // 80%
            versionRow(119, { human: 1, total: 10 }),  // 10%
            versionRow(118),                            // no traffic at all
        ];

        const result = sortVersions(versions, 'human', 'desc');

        expect(result.map(r => r.version)).toEqual([120, 119, 118]);
    });

    it('reverses order when direction is asc', () => {
        const versions = [versionRow(120, { hit: 5 }), versionRow(119, { hit: 100 })];

        expect(sortVersions(versions, 'requests', 'asc').map(r => r.version)).toEqual([120, 119]);
        expect(sortVersions(versions, 'requests', 'desc').map(r => r.version)).toEqual([119, 120]);
    });

    it('does not mutate the input array', () => {
        const versions = [versionRow(120, { hit: 5 }), versionRow(119, { hit: 100 })];
        const original = [...versions];

        sortVersions(versions, 'requests', 'desc');

        expect(versions).toEqual(original);
    });
});
