import { beforeEach, describe, it, expect, vi } from 'vitest';
import { readMeta, escapeHtml, buildBaseParams, initToggleBots, minVersionWithHumanTraffic } from '../../main/resources/static/js/utils.js';

// charts.js (imported transitively) references Chart via globalThis
globalThis.Chart = vi.fn();

beforeEach(() => {
    vi.clearAllMocks();
    document.head.innerHTML = '';
    document.body.innerHTML = '';
    localStorage.clear();
});

// ---------------------------------------------------------------------------
// escapeHtml
// ---------------------------------------------------------------------------

describe('escapeHtml', () => {
    it('escapes ampersands', () => {
        expect(escapeHtml('a & b')).toBe('a &amp; b');
    });

    it('escapes less-than and greater-than', () => {
        expect(escapeHtml('<script>')).toBe('&lt;script&gt;');
    });

    it('escapes double quotes', () => {
        expect(escapeHtml('"hello"')).toBe('&quot;hello&quot;');
    });

    it('leaves plain strings unchanged', () => {
        expect(escapeHtml('hello world')).toBe('hello world');
    });

    it('escapes multiple special characters in one string', () => {
        expect(escapeHtml('<a href="test&value">')).toBe('&lt;a href=&quot;test&amp;value&quot;&gt;');
    });
});

// ---------------------------------------------------------------------------
// readMeta
// ---------------------------------------------------------------------------

describe('readMeta', () => {
    it('returns the content of a named meta tag', () => {
        document.head.innerHTML = '<meta name="cf-from" content="2026-01-01T00:00:00Z">';
        expect(readMeta('cf-from')).toBe('2026-01-01T00:00:00Z');
    });
});

// ---------------------------------------------------------------------------
// buildBaseParams
// ---------------------------------------------------------------------------

describe('buildBaseParams', () => {
    beforeEach(() => {
        document.head.innerHTML = `
            <meta name="cf-from" content="2026-01-01T00:00:00Z">
            <meta name="cf-to"   content="2026-01-31T00:00:00Z">
        `;
    });

    it('includes from, to, and extra params', () => {
        const params = new URLSearchParams(buildBaseParams({ ua: 'Chrome' }));
        expect(params.get('ua')).toBe('Chrome');
        expect(params.get('from')).toBe('2026-01-01');
        expect(params.get('to')).toBe('2026-01-31');
    });

    it('adds excludeBots=true when toggleBots checkbox is checked', () => {
        document.body.innerHTML = '<input type="checkbox" id="toggleBots" checked>';
        const params = new URLSearchParams(buildBaseParams({}));
        expect(params.get('excludeBots')).toBe('true');
    });

    it('omits excludeBots when toggleBots checkbox is unchecked', () => {
        document.body.innerHTML = '<input type="checkbox" id="toggleBots">';
        const params = new URLSearchParams(buildBaseParams({}));
        expect(params.get('excludeBots')).toBeNull();
    });

    it('omits excludeBots when toggleBots element is absent', () => {
        const params = new URLSearchParams(buildBaseParams({}));
        expect(params.get('excludeBots')).toBeNull();
    });
});

// ---------------------------------------------------------------------------
// initToggleBots
// ---------------------------------------------------------------------------

describe('initToggleBots', () => {
    it('calls loadFn immediately on init', () => {
        const loadFn = vi.fn();
        initToggleBots(loadFn);
        expect(loadFn).toHaveBeenCalledOnce();
    });

    it('restores checked state from localStorage', () => {
        localStorage.setItem('excludeBots', 'true');
        document.body.innerHTML = '<input type="checkbox" id="toggleBots">';
        initToggleBots(vi.fn());
        expect(document.getElementById('toggleBots').checked).toBe(true);
    });

    it('leaves checkbox unchecked when localStorage value is not "true"', () => {
        localStorage.setItem('excludeBots', 'false');
        document.body.innerHTML = '<input type="checkbox" id="toggleBots">';
        initToggleBots(vi.fn());
        expect(document.getElementById('toggleBots').checked).toBe(false);
    });

    it('calls loadFn again and saves state on toggle change', () => {
        document.body.innerHTML = '<input type="checkbox" id="toggleBots">';
        const loadFn = vi.fn();
        initToggleBots(loadFn);

        const toggleEl = document.getElementById('toggleBots');
        toggleEl.checked = true;
        toggleEl.dispatchEvent(new Event('change'));

        expect(loadFn).toHaveBeenCalledTimes(2);
        expect(localStorage.getItem('excludeBots')).toBe('true');
    });

    it('persists false to localStorage when unchecked', () => {
        document.body.innerHTML = '<input type="checkbox" id="toggleBots" checked>';
        initToggleBots(vi.fn());

        const toggleEl = document.getElementById('toggleBots');
        toggleEl.checked = false;
        toggleEl.dispatchEvent(new Event('change'));

        expect(localStorage.getItem('excludeBots')).toBe('false');
    });

    it('works without a toggleBots element in the DOM', () => {
        const loadFn = vi.fn();
        expect(() => initToggleBots(loadFn)).not.toThrow();
        expect(loadFn).toHaveBeenCalledOnce();
    });
});

// ---------------------------------------------------------------------------
// minVersionWithHumanTraffic
// ---------------------------------------------------------------------------

function stat(name, humanRequests, totalRequests) {
    return { name, humanRequests, totalRequests };
}

describe('minVersionWithHumanTraffic', () => {
    it('returns the lowest major version with any aggregated human evidence (Chrome)', () => {
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

        expect(minVersionWithHumanTraffic(humanStats, 'Chrome')).toBe(148);
    });

    it('works the same way for Firefox', () => {
        const humanStats = [
            stat('Mozilla/5.0 (X11; Linux x86_64; rv:110.0) Gecko/20100101 Firefox/110.0', 0, 40),
            stat('Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0', 3, 15),
            stat('Mozilla/5.0 (X11; Linux x86_64; rv:147.0) Gecko/20100101 Firefox/147.0', 20, 20),
        ];

        expect(minVersionWithHumanTraffic(humanStats, 'Firefox')).toBe(130);
    });

    it('does not confuse Chrome and Firefox versions in the same UA string', () => {
        // A UA declaring both tokens (e.g. a spoofed/composite string) — must only match its own browser.
        const humanStats = [stat('...Chrome/99.0 ...Firefox/40.0...', 5, 5)];

        expect(minVersionWithHumanTraffic(humanStats, 'Chrome')).toBe(99);
        expect(minVersionWithHumanTraffic(humanStats, 'Firefox')).toBe(40);
    });

    it('aggregates multiple raw UA strings sharing the same major version', () => {
        const humanStats = [
            stat('...Chrome/120.0.0.0...', 0, 10),
            stat('...Chrome/120.5.1.2...', 3, 5), // same major version — combined human > 0
        ];

        expect(minVersionWithHumanTraffic(humanStats, 'Chrome')).toBe(120);
    });

    it('ignores versions with zero human requests', () => {
        const humanStats = [
            stat('...Chrome/120...', 0, 100),
            stat('...Chrome/151...', 5, 10),
        ];

        expect(minVersionWithHumanTraffic(humanStats, 'Chrome')).toBe(151);
    });

    it('returns null when no version has any human traffic', () => {
        const humanStats = [stat('...Chrome/120...', 0, 100), stat('...Chrome/151...', 0, 10)];
        expect(minVersionWithHumanTraffic(humanStats, 'Chrome')).toBeNull();
    });

    it('returns null for an empty list', () => {
        expect(minVersionWithHumanTraffic([], 'Chrome')).toBeNull();
    });

    it('ignores raw UA strings without a matching browser version', () => {
        const humanStats = [stat('Mozilla/5.0 (compatible; Googlebot/2.1)', 5, 5)];
        expect(minVersionWithHumanTraffic(humanStats, 'Chrome')).toBeNull();
    });
});