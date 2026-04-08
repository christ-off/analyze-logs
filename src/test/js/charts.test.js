import { readFileSync } from 'fs';
import { resolve } from 'path';
import { beforeAll, beforeEach, describe, it, expect, vi } from 'vitest';

// charts.js is a plain browser script that sets a global Charts object.
// We load it via eval into the jsdom globalThis so it works without modification.
const chartsPath = resolve('src/main/resources/static/js/charts.js');

let Charts;

beforeAll(() => {
    // Stub Chart.js constructor before loading charts.js
    globalThis.Chart = vi.fn();
    // charts.js uses `const Charts = {}` in strict mode, so const doesn't
    // attach to globalThis via indirect eval. We append an explicit assignment.
    const code = readFileSync(chartsPath, 'utf8') + '\nglobalThis.Charts = Charts;';
    // eslint-disable-next-line no-eval
    globalThis.eval(code);
    Charts = globalThis.Charts;
});

beforeEach(() => {
    vi.clearAllMocks();
    document.body.innerHTML = '';
});

// ---------------------------------------------------------------------------
// Pure functions
// ---------------------------------------------------------------------------

describe('Charts.toDateParam', () => {
    it('extracts the date part from an ISO string', () => {
        expect(Charts.toDateParam('2026-04-08T10:30:00Z')).toBe('2026-04-08');
    });

    it('works when the string is already 10 characters', () => {
        expect(Charts.toDateParam('2026-01-01')).toBe('2026-01-01');
    });
});

describe('Charts.resultTypeDatasets', () => {
    const data = [
        { hit: 10, miss: 5, function: 2, redirect: 1, error: 3 },
        { hit: 20, miss: 8, function: 0, redirect: 0, error: 1 },
    ];

    it('returns 5 datasets in order Hit/Miss/Function/Redirect/Error', () => {
        const ds = Charts.resultTypeDatasets(data);
        expect(ds).toHaveLength(5);
        expect(ds.map(d => d.label)).toEqual(['Hit', 'Miss', 'Function', 'Redirect', 'Error']);
    });

    it('maps each field correctly', () => {
        const ds = Charts.resultTypeDatasets(data);
        expect(ds[0].data).toEqual([10, 20]); // hit
        expect(ds[1].data).toEqual([5, 8]);   // miss
        expect(ds[2].data).toEqual([2, 0]);   // function
        expect(ds[3].data).toEqual([1, 0]);   // redirect
        expect(ds[4].data).toEqual([3, 1]);   // error
    });

    it('uses the correct background colors', () => {
        const ds = Charts.resultTypeDatasets(data);
        expect(ds[0].backgroundColor).toBe(Charts.COLORS.green);   // Hit
        expect(ds[1].backgroundColor).toBe(Charts.COLORS.blue);    // Miss
        expect(ds[2].backgroundColor).toBe(Charts.COLORS.orange);  // Function
        expect(ds[3].backgroundColor).toBe(Charts.COLORS.purple);  // Redirect
        expect(ds[4].backgroundColor).toBe(Charts.COLORS.red);     // Error
    });
});

// ---------------------------------------------------------------------------
// loadChart
// ---------------------------------------------------------------------------

describe('Charts.loadChart', () => {
    it('fetches the endpoint and calls render with parsed JSON', async () => {
        const payload = [{ name: 'Chrome', count: 42 }];
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: true,
            json: async () => payload,
        }));
        const render = vi.fn();

        await Charts.loadChart('ua-groups?from=2026-01-01&to=2026-01-31', render);

        expect(fetch).toHaveBeenCalledWith('/api/ua-groups?from=2026-01-01&to=2026-01-31');
        expect(render).toHaveBeenCalledWith(payload);
    });

    it('does not call render when HTTP response is not ok', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }));
        const render = vi.fn();
        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

        await Charts.loadChart('bad-endpoint', render);

        expect(render).not.toHaveBeenCalled();
        expect(consoleSpy).toHaveBeenCalled();
    });

    it('does not throw and logs error on network failure', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')));
        const render = vi.fn();
        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

        await expect(Charts.loadChart('any', render)).resolves.toBeUndefined();
        expect(render).not.toHaveBeenCalled();
        expect(consoleSpy).toHaveBeenCalled();
    });
});

// ---------------------------------------------------------------------------
// DOM-dependent chart functions
// ---------------------------------------------------------------------------

describe('Charts.pie', () => {
    it('does nothing when canvasId is not found', () => {
        Charts.pie('no-such-canvas', [], null);
        expect(globalThis.Chart).not.toHaveBeenCalled();
    });

    it('creates a pie chart with palette colors when colorMap is null', () => {
        document.body.innerHTML = '<canvas id="pieChart"></canvas>';
        const data = [{ name: 'Browsers', count: 300 }, { name: 'Bots', count: 100 }];

        Charts.pie('pieChart', data, null);

        expect(globalThis.Chart).toHaveBeenCalledOnce();
        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.type).toBe('pie');
        expect(config.data.labels).toEqual(['Browsers', 'Bots']);
        expect(config.data.datasets[0].data).toEqual([300, 100]);
        // Uses palette (not colorMap)
        expect(config.data.datasets[0].backgroundColor[0]).toBe(Charts.PALETTE[0]);
        expect(config.data.datasets[0].backgroundColor[1]).toBe(Charts.PALETTE[1]);
    });

    it('uses colorMap when provided, falling back to blue for unknown names', () => {
        document.body.innerHTML = '<canvas id="pieChart2"></canvas>';
        const colorMap = { 'Hit': 'green', 'Miss': 'blue' };
        const data = [{ name: 'Hit', count: 10 }, { name: 'Unknown', count: 5 }];

        Charts.pie('pieChart2', data, colorMap);

        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.data.datasets[0].backgroundColor[0]).toBe('green');
        expect(config.data.datasets[0].backgroundColor[1]).toBe(Charts.COLORS.blue); // fallback
    });

    it('uses (unknown) label for null name', () => {
        document.body.innerHTML = '<canvas id="pieChart3"></canvas>';
        Charts.pie('pieChart3', [{ name: null, count: 5 }], null);
        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.data.labels[0]).toBe('(unknown)');
    });
});

describe('Charts.horizontalBar', () => {
    it('does nothing when canvasId is not found', () => {
        Charts.horizontalBar('missing', [], null);
        expect(globalThis.Chart).not.toHaveBeenCalled();
    });

    it('creates a horizontal bar chart without click handler when urlFn is null', () => {
        document.body.innerHTML = '<canvas id="barChart"></canvas>';
        const data = [{ name: 'example.com', count: 42 }];

        Charts.horizontalBar('barChart', data, null);

        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.type).toBe('bar');
        expect(config.options.indexAxis).toBe('y');
        expect(config.options.onClick).toBeUndefined();
    });

    it('attaches onClick and onHover when urlFn is provided', () => {
        document.body.innerHTML = '<canvas id="barChart2"></canvas>';
        const urlFn = vi.fn(item => `/detail?ref=${item.name}`);

        Charts.horizontalBar('barChart2', [{ name: 'example.com', count: 5 }], urlFn);

        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.options.onClick).toBeDefined();
        expect(config.options.onHover).toBeDefined();
    });
});

describe('Charts.horizontalStackedBar', () => {
    it('does nothing when canvasId is not found', () => {
        Charts.horizontalStackedBar('missing', [], null);
        expect(globalThis.Chart).not.toHaveBeenCalled();
    });

    it('creates a stacked bar with resultType datasets', () => {
        document.body.innerHTML = '<canvas id="stackedBar"></canvas>';
        const data = [{ name: '/feed.xml', hit: 40, miss: 10, function: 0, redirect: 1, error: 2 }];

        Charts.horizontalStackedBar('stackedBar', data, null);

        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.type).toBe('bar');
        expect(config.options.scales.x.stacked).toBe(true);
        expect(config.options.scales.y.stacked).toBe(true);
        expect(config.data.datasets).toHaveLength(5);
        expect(config.options.onClick).toBeUndefined();
    });

    it('attaches onClick and onHover when urlFn is provided', () => {
        document.body.innerHTML = '<canvas id="stackedBar2"></canvas>';
        const urlFn = vi.fn(item => `/url?name=${item.name}`);

        Charts.horizontalStackedBar('stackedBar2', [{ name: '/index', hit: 1, miss: 0, function: 0, redirect: 0, error: 0 }], urlFn);

        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.options.onClick).toBeDefined();
        expect(config.options.onHover).toBeDefined();
    });
});