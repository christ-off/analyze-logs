import { beforeEach, describe, it, expect, vi } from 'vitest';
import { Charts } from '../../main/resources/static/js/charts.js';

// Stub the Chart.js constructor (loaded via CDN/webjars in browser, not available in tests)
globalThis.Chart = vi.fn();

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
        { hit: 10, miss: 5, function: 2, error: 3 },
        { hit: 20, miss: 8, function: 0, error: 1 },
    ];

    it('returns 4 datasets in order Hit/Miss/Function/Error', () => {
        const ds = Charts.resultTypeDatasets(data);
        expect(ds).toHaveLength(4);
        expect(ds.map(d => d.label)).toEqual(['Hit', 'Miss', 'Function', 'Error']);
    });

    it('maps each field correctly', () => {
        const ds = Charts.resultTypeDatasets(data);
        expect(ds[0].data).toEqual([10, 20]); // hit
        expect(ds[1].data).toEqual([5, 8]);   // miss
        expect(ds[2].data).toEqual([2, 0]);   // function
        expect(ds[3].data).toEqual([3, 1]);   // error
    });

    it('uses the correct background colors', () => {
        const ds = Charts.resultTypeDatasets(data);
        expect(ds[0].backgroundColor).toBe(Charts.COLORS.green);   // Hit
        expect(ds[1].backgroundColor).toBe(Charts.COLORS.blue);    // Miss
        expect(ds[2].backgroundColor).toBe(Charts.COLORS.orange);  // Function
        expect(ds[3].backgroundColor).toBe(Charts.COLORS.red);     // Error
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
        const data = [{ name: '/feed.xml', hit: 40, miss: 10, function: 0, error: 2 }];

        Charts.horizontalStackedBar('stackedBar', data, null);

        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.type).toBe('bar');
        expect(config.options.scales.x.stacked).toBe(true);
        expect(config.options.scales.y.stacked).toBe(true);
        expect(config.data.datasets).toHaveLength(4);
        expect(config.options.onClick).toBeUndefined();
    });

    it('attaches onClick and onHover when urlFn is provided', () => {
        document.body.innerHTML = '<canvas id="stackedBar2"></canvas>';
        const urlFn = vi.fn(item => `/url?name=${item.name}`);

        Charts.horizontalStackedBar('stackedBar2', [{ name: '/index', hit: 1, miss: 0, function: 0, error: 0 }], urlFn);

        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.options.onClick).toBeDefined();
        expect(config.options.onHover).toBeDefined();
    });
});

// ---------------------------------------------------------------------------
// Charts.stackedBarByDay
// ---------------------------------------------------------------------------

describe('Charts.stackedBarByDay', () => {
    it('does nothing when canvasId is not found', () => {
        Charts.stackedBarByDay('missing', []);
        expect(globalThis.Chart).not.toHaveBeenCalled();
    });

    it('creates a stacked bar-by-day chart with correct config', () => {
        document.body.innerHTML = '<canvas id="dayChart"></canvas>';
        const data = [
            { day: '2026-01-01', hit: 100, miss: 20, function: 5, error: 1 },
            { day: '2026-01-02', hit: 80,  miss: 15, function: 3, error: 0 },
        ];

        Charts.stackedBarByDay('dayChart', data);

        expect(globalThis.Chart).toHaveBeenCalledOnce();
        const [, config] = globalThis.Chart.mock.calls[0];
        expect(config.type).toBe('bar');
        expect(config.data.labels).toEqual(['2026-01-01', '2026-01-02']);
        expect(config.data.datasets).toHaveLength(4);
        expect(config.options.scales.x.stacked).toBe(true);
        expect(config.options.scales.y.stacked).toBe(true);
        expect(config.options.scales.y.beginAtZero).toBe(true);
    });
});

// ---------------------------------------------------------------------------
// Pie chart plugin callbacks
// ---------------------------------------------------------------------------

describe('Charts.pie — generateLabels callback', () => {
    it('computes percentage labels correctly', () => {
        document.body.innerHTML = '<canvas id="pieLabels"></canvas>';
        Charts.pie('pieLabels', [{ name: 'Hit', count: 300 }, { name: 'Miss', count: 100 }], null);

        const [, config] = globalThis.Chart.mock.calls[0];
        const labels = config.options.plugins.legend.labels.generateLabels({ data: config.data });

        expect(labels).toHaveLength(2);
        expect(labels[0].text).toBe('Hit (75.0%)');
        expect(labels[1].text).toBe('Miss (25.0%)');
        expect(labels[0].hidden).toBe(false);
    });

    it('shows 0.0% when total is zero', () => {
        document.body.innerHTML = '<canvas id="pieLabelsZero"></canvas>';
        Charts.pie('pieLabelsZero', [{ name: 'X', count: 0 }], null);

        const [, config] = globalThis.Chart.mock.calls[0];
        const labels = config.options.plugins.legend.labels.generateLabels({ data: config.data });

        expect(labels[0].text).toBe('X (0.0%)');
    });
});

describe('Charts.pie — tooltip label callback', () => {
    it('returns formatted count and percentage', () => {
        document.body.innerHTML = '<canvas id="pieTooltip"></canvas>';
        Charts.pie('pieTooltip', [{ name: 'Hit', count: 300 }, { name: 'Miss', count: 100 }], null);

        const [, config] = globalThis.Chart.mock.calls[0];
        const labelCb = config.options.plugins.tooltip.callbacks.label;
        const result = labelCb({ dataset: { data: [300, 100] }, parsed: 300 });

        expect(result).toContain('75.0%');
    });

    it('returns 0.0% when total is zero', () => {
        document.body.innerHTML = '<canvas id="pieTooltipZero"></canvas>';
        Charts.pie('pieTooltipZero', [{ name: 'X', count: 0 }], null);

        const [, config] = globalThis.Chart.mock.calls[0];
        const labelCb = config.options.plugins.tooltip.callbacks.label;
        const result = labelCb({ dataset: { data: [0] }, parsed: 0 });

        expect(result).toContain('0.0%');
    });
});

// ---------------------------------------------------------------------------
// horizontalBar onClick / onHover
// ---------------------------------------------------------------------------

describe('Charts.horizontalBar — onClick / onHover', () => {
    it('onClick navigates when an element is clicked', () => {
        document.body.innerHTML = '<canvas id="navBar"></canvas>';
        const locationMock = { href: '' };
        vi.stubGlobal('location', locationMock);
        Charts.horizontalBar('navBar', [{ name: 'example.com', count: 5 }], item => `/ref?name=${item.name}`);

        const [, config] = globalThis.Chart.mock.calls[0];
        config.options.onClick({}, [{ index: 0 }]);

        expect(locationMock.href).toBe('/ref?name=example.com');
    });

    it('onClick does nothing when elements array is empty', () => {
        document.body.innerHTML = '<canvas id="navBar2"></canvas>';
        const urlFn = vi.fn();
        Charts.horizontalBar('navBar2', [{ name: 'example.com', count: 5 }], urlFn);

        const [, config] = globalThis.Chart.mock.calls[0];
        config.options.onClick({}, []);

        expect(urlFn).not.toHaveBeenCalled();
    });

    it('onHover sets pointer cursor when elements present', () => {
        document.body.innerHTML = '<canvas id="hoverBar"></canvas>';
        Charts.horizontalBar('hoverBar', [{ name: 'example.com', count: 5 }], () => '/foo');

        const [, config] = globalThis.Chart.mock.calls[0];
        const target = { style: { cursor: '' } };
        config.options.onHover({ native: { target } }, [{ index: 0 }]);

        expect(target.style.cursor).toBe('pointer');
    });

    it('onHover sets default cursor when no elements', () => {
        document.body.innerHTML = '<canvas id="hoverBar2"></canvas>';
        Charts.horizontalBar('hoverBar2', [{ name: 'example.com', count: 5 }], () => '/foo');

        const [, config] = globalThis.Chart.mock.calls[0];
        const target = { style: { cursor: 'pointer' } };
        config.options.onHover({ native: { target } }, []);

        expect(target.style.cursor).toBe('default');
    });
});

// ---------------------------------------------------------------------------
// horizontalStackedBar onClick / onHover
// ---------------------------------------------------------------------------

describe('Charts.horizontalStackedBar — onClick / onHover', () => {
    it('onClick navigates when an element is clicked', () => {
        document.body.innerHTML = '<div><canvas id="stackedNavBar"></canvas></div>';
        const locationMock = { href: '' };
        vi.stubGlobal('location', locationMock);
        const data = [{ name: '/index', hit: 1, miss: 0, function: 0, error: 0 }];
        Charts.horizontalStackedBar('stackedNavBar', data, item => `/url?name=${item.name}`);

        const [, config] = globalThis.Chart.mock.calls[0];
        config.options.onClick({}, [{ index: 0 }]);

        expect(locationMock.href).toBe('/url?name=/index');
    });

    it('onClick does nothing when elements array is empty', () => {
        document.body.innerHTML = '<div><canvas id="stackedNavBar2"></canvas></div>';
        const urlFn = vi.fn();
        Charts.horizontalStackedBar('stackedNavBar2', [{ name: '/x', hit: 1, miss: 0, function: 0, error: 0 }], urlFn);

        const [, config] = globalThis.Chart.mock.calls[0];
        config.options.onClick({}, []);

        expect(urlFn).not.toHaveBeenCalled();
    });

    it('onHover sets pointer cursor when elements present', () => {
        document.body.innerHTML = '<div><canvas id="stackedHoverBar"></canvas></div>';
        Charts.horizontalStackedBar('stackedHoverBar', [{ name: '/x', hit: 1, miss: 0, function: 0, error: 0 }], () => '/foo');

        const [, config] = globalThis.Chart.mock.calls[0];
        const target = { style: { cursor: '' } };
        config.options.onHover({ native: { target } }, [{ index: 0 }]);

        expect(target.style.cursor).toBe('pointer');
    });

    it('onHover sets default cursor when no elements', () => {
        document.body.innerHTML = '<div><canvas id="stackedHoverBar2"></canvas></div>';
        Charts.horizontalStackedBar('stackedHoverBar2', [{ name: '/x', hit: 1, miss: 0, function: 0, error: 0 }], () => '/foo');

        const [, config] = globalThis.Chart.mock.calls[0];
        const target = { style: { cursor: 'pointer' } };
        config.options.onHover({ native: { target } }, []);

        expect(target.style.cursor).toBe('default');
    });
});