'use strict';

import { loadUriCountTable } from '../utils.js';

loadUriCountTable('/api/zip-requests/uris', 'zipRequestsTable', 'zipRequestsCount',
    'No archive requests found for the selected date range.');
