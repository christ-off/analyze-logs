import { Charts } from './charts.js';

const from    = document.querySelector('meta[name="cf-from"]').content;
const to      = document.querySelector('meta[name="cf-to"]').content;
const country = document.querySelector('meta[name="cf-country"]').content;

const countryParams = new URLSearchParams({ country, from: Charts.toDateParam(from), to: Charts.toDateParam(to) });
const p = countryParams.toString();

Charts.loadChart(`country-detail/result-types?${p}`,     d => Charts.pie('chartResultTypes',          d, Charts.RESULT_TYPE_COLORS));
Charts.loadChart(`country-detail/url-split?${p}`,         d => Charts.horizontalStackedBar('chartUriStems', d));
Charts.loadChart(`country-detail/requests-per-day?${p}`, d => Charts.linePerDay('chartRequestsPerDay', d));