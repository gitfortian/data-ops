import {
  datasetDeliveryState,
  datasetPublishOutcome,
} from './datasetDeliveryExperience';

import type { DevelopmentDatasetNodeContext } from '../../dataset-service';

const context = (
  versionNo?: number,
): DevelopmentDatasetNodeContext => ({
  nodeId: '7',
  nodeName: 'Orders Dataset',
  configured: true,
  dataset: {
    developmentNodeId: '7',
    datasetId: '55',
    name: 'Orders Dataset',
    status: 'ONLINE',
    currentVersion: versionNo
      ? {
          versionId: `dv-${versionNo}`,
          versionNo,
          sourceType: 'SQL_QUERY',
          dataSourceId: '9',
          sql: 'select * from orders',
        }
      : null,
    versions: [],
    fields: [],
    draftDataSourceId: '9',
    draftSql: 'select * from orders',
    draftFields: [],
  },
});

describe('dataset delivery experience', () => {
  it('does not invent an immutable DV from a saved Dataset draft', () => {
    expect(datasetDeliveryState(context(), false)).toEqual({
      datasetId: '55',
      draftState: 'SAVED',
      publishedVersionNo: undefined,
      published: false,
      deliveryStatus: 'ONLINE',
    });
  });

  it('keeps local unsaved state independent from the last published DV', () => {
    expect(datasetDeliveryState(context(3), true)).toMatchObject({
      datasetId: '55',
      draftState: 'LOCAL_UNSAVED',
      publishedVersionNo: 3,
      published: true,
    });
  });

  it('distinguishes a new immutable version from a publish no-op', () => {
    expect(datasetPublishOutcome(3, 4)).toEqual({
      kind: 'PUBLISHED',
      versionNo: 4,
    });
    expect(datasetPublishOutcome(4, 4)).toEqual({
      kind: 'UNCHANGED',
      versionNo: 4,
    });
  });
});
