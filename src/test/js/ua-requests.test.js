import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/utils.js', () => ({
    readMeta:        vi.fn(() => 'TestAgent/1.0'),
    buildBaseParams: vi.fn(() => 'ua=TestAgent%2F1.0&from=2026-01-01&to=2026-01-31'),
}));

vi.mock('../../main/resources/static/js/charts.js', () => ({
    Charts: {
        loadChart:       vi.fn(),
        stackedBarByDay: vi.fn(),
    },
}));

let init;
let Charts;
beforeEach(async () => {
    vi.clearAllMocks();
    ({ Charts } = await import('../../main/resources/static/js/charts.js'));
    ({ init } = await import('../../main/resources/static/js/pages/ua-requests.js'));
    init();
});

describe('ua-requests requests-per-day chart', () => {
    it('loads the requests-per-day chart for the page UA', () => {
        expect(Charts.loadChart).toHaveBeenCalledWith(
            expect.stringContaining('ua-requests/requests-per-day?'),
            expect.any(Function));
    });
});
