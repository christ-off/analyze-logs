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
    resultTotal:           row => row.hit + row.miss + row['function'] + row.error,
    stackedBar:             vi.fn(),
    renderMinVersionBanner: vi.fn(),
}));

import { safariMajorVersion, aggregateByVersion, sortVersions } from '../../main/resources/static/js/safari.js';

function raw(name, hit, miss, fn, error) {
    return { name, hit, miss, function: fn, error };
}

function human(name, humanRequests, totalRequests) {
    return { name, humanRequests, totalRequests };
}

function versionRow(version, { hit = 0, miss = 0, fn = 0, error = 0, human = 0, total = 0 } = {}) {
    return { version, hit, miss, function: fn, error, human, total };
}

describe('safariMajorVersion', () => {
    it('extracts the major version from the Version/ token, not the trailing Safari/ build number', () => {
        expect(safariMajorVersion('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.4 Safari/605.1.15')).toBe(26);
    });

    it('reads the Version/ token on mobile UAs too', () => {
        expect(safariMajorVersion('Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 Version/26.4 Mobile/15E148 Safari/604.1')).toBe(26);
    });

    it('returns null when the UA has no Version/ token', () => {
        expect(safariMajorVersion('Mozilla/5.0 ... Chrome/120.0.0.0 Safari/537.36')).toBeNull();
    });
});

describe('aggregateByVersion', () => {
    it('sums result-type counts across raw UA strings sharing the same major version, whatever the OS', () => {
        const rawUserAgents = [
            raw('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.4 Safari/605.1.15', 10, 2, 0, 1),
            raw('Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 Version/26.4 Mobile/15E148 Safari/604.1', 5, 0, 0, 0),
            raw('Mozilla/5.0 (iPad; CPU OS 18_7 like Mac OS X) AppleWebKit/605.1.15 Version/26.4 Mobile/15E148 Safari/604.1', 3, 1, 0, 0),
            raw('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/25.0 Safari/605.1.15', 1, 0, 0, 0),
        ];

        const result = aggregateByVersion(rawUserAgents, []);

        const v26 = result.find(r => r.version === 26);
        expect(v26.hit).toBe(18);
        expect(v26.miss).toBe(3);
        expect(v26.error).toBe(1);

        const v25 = result.find(r => r.version === 25);
        expect(v25.hit).toBe(1);
    });

    it('joins human-traffic stats onto the matching raw UA string and sums them per version', () => {
        const uaMac = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.4 Safari/605.1.15';
        const uaIphone = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 Version/26.4 Mobile/15E148 Safari/604.1';
        const rawUserAgents = [raw(uaMac, 10, 0, 0, 0), raw(uaIphone, 5, 0, 0, 0)];
        const humanStats = [human(uaMac, 8, 10), human(uaIphone, 0, 5)];

        const [row] = aggregateByVersion(rawUserAgents, humanStats);

        expect(row.human).toBe(8);
        expect(row.total).toBe(15);
    });

    it('ignores raw UA strings without a matching Version/ token', () => {
        const rawUserAgents = [raw('Mozilla/5.0 (compatible; Googlebot/2.1)', 5, 0, 0, 0)];
        expect(aggregateByVersion(rawUserAgents, [])).toEqual([]);
    });
});

describe('sortVersions', () => {
    it('sorts the "version" column numerically, not lexicographically on the label text', () => {
        const versions = [versionRow(26), versionRow(9), versionRow(10), versionRow(2)];

        const result = sortVersions(versions, 'version', 'asc');

        expect(result.map(r => r.version)).toEqual([2, 9, 10, 26]);
    });

    it('sorts by requests (hit+miss+filtered+error total)', () => {
        const versions = [
            versionRow(26, { hit: 5 }),
            versionRow(25, { hit: 100 }),
            versionRow(24, { hit: 20 }),
        ];

        const result = sortVersions(versions, 'requests', 'desc');

        expect(result.map(r => r.version)).toEqual([25, 24, 26]);
    });

    it('sorts by proportion of human traffic, treating rows with no traffic as lowest', () => {
        const versions = [
            versionRow(26, { human: 8, total: 10 }),  // 80%
            versionRow(25, { human: 1, total: 10 }),  // 10%
            versionRow(24),                            // no traffic at all
        ];

        const result = sortVersions(versions, 'human', 'desc');

        expect(result.map(r => r.version)).toEqual([26, 25, 24]);
    });

    it('reverses order when direction is asc', () => {
        const versions = [versionRow(26, { hit: 5 }), versionRow(25, { hit: 100 })];

        expect(sortVersions(versions, 'requests', 'asc').map(r => r.version)).toEqual([26, 25]);
        expect(sortVersions(versions, 'requests', 'desc').map(r => r.version)).toEqual([25, 26]);
    });

    it('does not mutate the input array', () => {
        const versions = [versionRow(26, { hit: 5 }), versionRow(25, { hit: 100 })];
        const original = [...versions];

        sortVersions(versions, 'requests', 'desc');

        expect(versions).toEqual(original);
    });
});
