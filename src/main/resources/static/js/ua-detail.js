(function () {
    'use strict';

    const from = document.querySelector('meta[name="cf-from"]').content;
    const to   = document.querySelector('meta[name="cf-to"]').content;
    const ua   = document.querySelector('meta[name="cf-ua"]').content;

    const uaParams = new URLSearchParams({ ua, from: Charts.toDateParam(from), to: Charts.toDateParam(to) });
    const p = uaParams.toString();

    Charts.loadChart(`ua-detail/result-types?${p}`,     d => Charts.pie('chartResultTypes',          d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`ua-detail/countries?${p}`,        d => Charts.pie('chartCountries',             d, null));
    Charts.loadChart(`ua-detail/uri-stems?${p}`,        d => Charts.horizontalBar('chartUriStems',    d));
    Charts.loadChart(`ua-detail/requests-per-day?${p}`, d => Charts.linePerDay('chartRequestsPerDay', d));
})();