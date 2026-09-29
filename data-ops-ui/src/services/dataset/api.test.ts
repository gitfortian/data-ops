import HttpUtils from '@/utils/HttpUtils';
import {
  getPublishedDataset,
  listPublishedDatasets,
  resolvePublishedDatasetsByIds,
} from './api';
import type { DatasetCatalogWire, DatasetSourceType } from './types';

const catalogEntry = (
  id: string,
  status: 'ONLINE' | 'OFFLINE' = 'ONLINE',
  sourceType: DatasetSourceType = 'SQL_QUERY',
): DatasetCatalogWire => ({
  dataset: {
    id,
    name: `dataset-${id}`,
    status,
    currentVersionId: `${id}01`,
    updateTime: '2026-08-28T00:00:00Z',
  },
  currentVersion: {
    id: `${id}01`,
    datasetId: id,
    versionNo: 1,
    sourceType,
    sourceTaskAssetId: '0',
    sourceTaskRevisionId: '0',
    sourceTaskRevisionNo: 0,
    dataSourceId: 'ds-1',
  },
  fields: [
    {
      fieldId: `field-${id}`,
      versionId: `${id}01`,
      physicalName: 'amount',
      displayName: 'amount',
      dataType: 'NUMBER',
      nullable: true,
      defaultRole: 'MEASURE',
      sortOrder: 1,
    },
  ],
});

describe('Dataset catalog API', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('loads the published Dataset catalog with one HTTP request', async () => {
    const get = jest.spyOn(HttpUtils, 'get').mockResolvedValue({
      code: 200,
      data: [catalogEntry('1')],
    } as any);

    const result = await listPublishedDatasets();

    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls[0][0]).toBe('/api/v1/datasets/catalog?onlineOnly=true');
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('1');
    expect(result[0].fields).toHaveLength(1);
  });

  it('loads one Dataset through the catalog without requesting version history', async () => {
    const get = jest.spyOn(HttpUtils, 'get').mockResolvedValue({
      code: 200,
      data: [catalogEntry('7')],
    } as any);

    const result = await getPublishedDataset('7');

    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls[0][0]).toBe('/api/v1/datasets/catalog?datasetIds=7');
    expect(result.id).toBe('7');
  });

  it('resolves requested ids in one batch and preserves per-id availability errors', async () => {
    const get = jest.spyOn(HttpUtils, 'get').mockResolvedValue({
      code: 200,
      data: [
        catalogEntry('1'),
        catalogEntry('2', 'OFFLINE'),
        catalogEntry('3', 'ONLINE', 'TABLE'),
      ],
    } as any);

    const result = await resolvePublishedDatasetsByIds(['1', '2', '3', '4', '1']);

    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls[0][0]).toBe('/api/v1/datasets/catalog?datasetIds=1,2,3,4');
    expect(result.datasets.map((dataset) => dataset.id)).toEqual(['1']);
    expect(result.errors['2']).toContain('当前未发布');
    expect(result.errors['3']).toContain('尚未接入查询运行时');
    expect(result.errors['4']).toContain('不存在或当前项目不可见');
  });
});
