import {
  analysisQueryRuntimeSize,
  clearAnalysisQueryRuntime,
  queryAnalysisDatasetShared,
} from './query-runtime';
import type { DatasetQueryPayload, DatasetQueryResult } from './model';

const payload: DatasetQueryPayload = {
  dimensions: ['region'],
  metrics: [{ fieldId: 'sales', aggregation: 'SUM' }],
  filters: [],
  sorts: [],
  limit: 500,
  timeoutSeconds: 30,
};

const result: DatasetQueryResult = {
  datasetId: '1',
  datasetVersionId: 'version-3',
  datasetVersionNo: 3,
  columns: [],
  bindings: [],
  rows: [],
  returnedRows: 0,
  truncated: false,
  elapsedMillis: 8,
};

describe('analysis query runtime', () => {
  beforeEach(() => clearAnalysisQueryRuntime());

  it('deduplicates identical in-flight requests', async () => {
    let resolveRequest: (value: DatasetQueryResult) => void = () => undefined;
    const loader = jest.fn(() => new Promise<DatasetQueryResult>((resolve) => {
      resolveRequest = resolve;
    }));
    const dataset = { id: '1', currentVersionNo: 3 };

    const first = queryAnalysisDatasetShared(dataset, payload, loader);
    const second = queryAnalysisDatasetShared(dataset, payload, loader);

    expect(first).toBe(second);
    expect(loader).toHaveBeenCalledTimes(1);
    expect(analysisQueryRuntimeSize()).toBe(1);

    resolveRequest(result);
    await expect(first).resolves.toBe(result);
  });

  it('reuses a recently settled result', async () => {
    const loader = jest.fn(async () => result);
    const dataset = { id: '1', currentVersionNo: 3 };

    await expect(queryAnalysisDatasetShared(dataset, payload, loader)).resolves.toBe(result);
    await expect(queryAnalysisDatasetShared(dataset, payload, loader)).resolves.toBe(result);

    expect(loader).toHaveBeenCalledTimes(1);
  });

  it('separates cache entries by Dataset version', async () => {
    const loader = jest.fn(async () => result);

    await queryAnalysisDatasetShared({ id: '1', currentVersionNo: 3 }, payload, loader);
    await queryAnalysisDatasetShared({ id: '1', currentVersionNo: 4 }, payload, loader);

    expect(loader).toHaveBeenCalledTimes(2);
  });

  it('does not cache failed requests', async () => {
    const loader = jest.fn()
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(result);
    const dataset = { id: '1', currentVersionNo: 3 };

    await expect(queryAnalysisDatasetShared(dataset, payload, loader)).rejects.toThrow('boom');
    await expect(queryAnalysisDatasetShared(dataset, payload, loader)).resolves.toBe(result);

    expect(loader).toHaveBeenCalledTimes(2);
  });
});
