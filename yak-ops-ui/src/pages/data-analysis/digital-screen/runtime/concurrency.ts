export const createScreenRuntimeAbortError = () => {
  const error = new Error('Screen runtime query aborted');
  error.name = 'AbortError';
  return error;
};

export const isScreenRuntimeAbortError = (error: unknown) => Boolean(
  error
  && typeof error === 'object'
  && (
    (error as { name?: string }).name === 'AbortError'
    || (error as { type?: string }).type === 'aborted'
  )
);

/** Executes work with a bounded number of workers while preserving result order. */
export async function mapWithConcurrency<T, R>(
  items: T[],
  limit: number,
  worker: (item: T, index: number) => Promise<R>,
  signal?: AbortSignal,
): Promise<R[]> {
  if (!items.length) return [];
  const results = new Array<R>(items.length);
  let cursor = 0;
  const workerCount = Math.min(Math.max(1, Math.floor(limit)), items.length);

  const runWorker = async () => {
    while (true) {
      if (signal?.aborted) throw createScreenRuntimeAbortError();
      const index = cursor;
      cursor += 1;
      if (index >= items.length) return;
      results[index] = await worker(items[index], index);
    }
  };

  await Promise.all(Array.from({ length: workerCount }, () => runWorker()));
  return results;
}
