export async function flushPromises() {
    for (let i = 0; i < 10; i++) await Promise.resolve();
}
