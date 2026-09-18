'use strict';

import { loadUriCountTable } from '../utils.js';

loadUriCountTable('/api/errors-404/uris', 'errors404Table', 'errors404Count',
    'No 404s or errors found for the selected date range.');
