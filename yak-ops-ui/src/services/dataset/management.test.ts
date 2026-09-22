import HttpUtils from '@/utils/HttpUtils';
import {
  getDatasetForManagement,
  listDatasetsForManagement,
  queryDataset,
} from './api';
import type { DatasetCatalogWire, DatasetDetailWire } from './types';

const currentVersion = {
  id: '701',
  datasetId: '7',
  versionNo: 2,
  sourceType: 'QUERY_REVISION' as const,
  sourceTaskAssetId: '101',
  sourceTaskRevisionId: '202',
  sourceTaskRevisionNo: 5,
  createTime: '2026-08-28T01:00:00Z',
};

const catalogEntry: DatasetCatalogWire = {
  dataset: {
    id: '7',
    name: 'orders',
    description: '订单 Dataset',
    status: 'ONLINE',
    currentVersionId: '701',
    createTime: '2026-08-27T01:00:00Z',
    updateTime: '2026-08-28T01:00:00Z',
  },
  currentVersion,
  fields: [
    {
      fieldId: 'amount',
      versionId: '701',
      physicalName: 'amount',
      displayName: '订单金额',
      dataType: 'NUMBER',
      nullable: false,
      defaultRole: 'MEASURE',
      sortOrder: 1,
    },
  ],
};

const detail: DatasetDetailWire = {
  ...catalogEntry,
  versions: [
    {
      ...currentVersion,
      id: '700',
      versionNo: 1,
      sourceTaskRevisionId: '201',
      sourceTaskRevisionNo: 4,
      createTime: '2026-08-27T01:00:00Z',
    },
    currentVersion,
  ],
};

describe('Dataset management API', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('loads management rows with one catalog request', async () => {
    const get = jest.spyOn(HttpUtils, 'get').mockResolvedValue({
      code: 200,
      data: [catalogEntry],
    } as any);

    const result = await listDatasetsForManagement();

    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls[0][0]).toBe('/api/v1/datasets/catalog');
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      id: '7',
      name: 'orders',
      currentVersion: {
        versionNo: 2,
        sourceTaskAssetId: '101',
        sourceTaskRevisionNo: 5,
      },
    });
    expect(result[0].fields[0]).toMatchObject({
      fieldId: 'amount',
      displayName: '订单金额',
      defaultRole: 'MEASURE',
    });
  });

  it('loads version history only from the Dataset detail endpoint', async () => {
    const get = jest.spyOn(HttpUtils, 'get').mockResolvedValue({
      code: 200,
      data: detail,
    } as any);

    const result = await getDatasetForManagement('7');

    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls[0][0]).toBe('/api/v1/datasets/7');
    expect(result.versions.map((version) => version.versionNo)).toEqual([1, 2]);
  });

  it('keeps versionNo in Query Runtime payload for immutable-version preview', async () => {
    const post = jest.spyOn(HttpUtils, 'post').mockResolvedValue({
      code: 200,
      data: {
        datasetId: '7',
        datasetVersionId: '701',
        datasetVersionNo: 2,
        bindings: [],
        columns: [],
        rows: [],
        returnedRows: 0,
        truncated: false,
        elapsedMillis: 3,
      },
    } as any);

    await queryDataset('7', {
      versionNo: 2,
      dimensions: [],
      metrics: [],
      filters: [],
      sorts: [],
      limit: 50,
      timeoutSeconds: 30,
    });

    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][0]).toBe('/api/v1/datasets/7/query');
    expect(post.mock.calls[0][1]).toMatchObject({ versionNo: 2, limit: 50 });
  });
});
