'use strict';

let cache = new Map();

export function getIpInfo(ip) {
    if (!cache.has(ip)) {
        cache.set(ip, fetch('/api/ip-info/' + encodeURIComponent(ip)).then(r => r.json()));
    }
    return cache.get(ip);
}
