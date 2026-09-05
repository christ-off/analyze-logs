package com.example.analyzelog.model;

// goodManners: this UA only ever requested /robots.txt (never ads.txt/sitemap.xml) and is
// explicitly named in robots.txt — checking politely before doing anything else.
public record SiteConfigFetcher(String name, long hit, long miss, long function, long error, boolean goodManners) {
    public long total() { return hit + miss + function + error; }
}
