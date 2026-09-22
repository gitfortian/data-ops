import { mapWithConcurrency } from './concurrency';

describe('mapWithConcurrency', () => {
  it('never exceeds the configured active worker count', async () => {
    let active = 0;
    let maxActive = 0;

    const results = await mapWithConcurrency([1, 2, 3, 4], 2, async (value) => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      await Promise.resolve();
      active -= 1;
      return value * 2;
    });

    expect(results).toEqual([2, 4, 6, 8]);
    expect(maxActive).toBe(2);
  });

  it('stops before starting work when already aborted', async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(mapWithConcurrency(
      [1],
      1,
      async (value) => value,
      controller.signal,
    )).rejects.toMatchObject({ name: 'AbortError' });
  });
});
