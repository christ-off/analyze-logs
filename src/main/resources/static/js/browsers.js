import { aggregateByVersion as aggregateByVersionGeneric } from './version-table.js';

export { sortVersions } from './version-table.js';

// label: display name. token: the UA token carrying the browser's major version — "Edg/" rather
// than "Chrome/" (Edge can lag its Chromium base) and "Version/" rather than "Safari/" (which is
// the WebKit build number).
export const BROWSERS = {
    chrome:  { label: 'Chrome',  token: 'Chrome' },
    edge:    { label: 'Edge',    token: 'Edg' },
    firefox: { label: 'Firefox', token: 'Firefox' },
    safari:  { label: 'Safari',  token: 'Version' },
};

// Extract the major version from a raw user_agent string, e.g. ("chrome", "...Chrome/120.0.0.0...") -> 120.
export function majorVersion(browser, rawUa) {
    const m = rawUa.match(new RegExp(`${BROWSERS[browser].token}/(\\d+)`));
    return m ? Number(m[1]) : null;
}

export function aggregateByVersion(browser, rawUserAgents, humanStats) {
    return aggregateByVersionGeneric(rawUserAgents, humanStats, rawUa => majorVersion(browser, rawUa));
}
