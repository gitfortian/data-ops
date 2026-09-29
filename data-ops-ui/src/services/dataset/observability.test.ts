import HttpUtils from '@/utils/HttpUtils';
import { listDatasetQueryPerformance } from './api';

describe('Dataset query observability API', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('serializes Dataset, status and slow-query filters into one request', async () => {
    const get = jest.spyOn(HttpUtils, 'get').mockResolvedValue({
      code: 200,
      data: [],
    } as any);

    await listDatasetQueryPerformance({
      datasetIds: ['7'],
      queryIds: ['q-1'],
      statuses: ['FAILED', 'TIMEOUT'],
      minTotalMillis: 3_000,
      limit: 500,
    });

    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls[0][0]).toBe(
      '/api/v1/datasets/query-performance?datasetIds=7&queryIds=q-1&statuses=FAILED,TIMEOUT&minTotalMillis=3000&limit=200',
    );
  });

  it('uses the default diagnostics endpoint when no filters are supplied', async () => {
    const get = jest.spyOn(HttpUtils, 'get').mockResolvedValue({
      code: 200,
      data: [],
    } as any);

    await listDatasetQueryPerformance();

    expect(get.mock.calls[0][0]).toBe('/api/v1/datasets/query-performance');
  });
});
