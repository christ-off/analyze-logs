import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        environment: 'jsdom',
        globals: true,
        include: ['src/test/js/**/*.test.js'],
        coverage: {
            provider: 'v8',
            include: ['src/main/resources/static/js/charts.js'],
        },
    },
});