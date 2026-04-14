import { beforeEach, describe, it, expect, vi } from 'vitest';
import { readMeta, escapeHtml, buildBaseParams, initToggleBots } from '../../main/resources/static/js/utils.js';

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