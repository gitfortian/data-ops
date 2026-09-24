import {
  dataServiceDeliveryState,
  dataServicePublishOutcome,
} from './dataServiceDeliveryExperience';

import type { DevelopmentDataServiceNodeContext } from '../../data-service-node-service';
import type { DataServicePublicationState } from '../../data-service-runtime-publication';

const context = (revisionNo?: number): DevelopmentDataServiceNodeContext => ({
  nodeId: '7',
  nodeName: 'Orders API',
  configured: true,
  draft: {
    nodeId: '7',
    draftRevision: 12,
    definition: {
      dataSourceId: '9',
      sql: 'select * from orders',
      serviceName: 'Orders API',
      path: '/orders',
      method: 'GET',
      parameters: [],
      responseFields: [{ name: 'id', type: 'INTEGER', nullable: false }],
      maxRows: 1000,
      timeoutSeconds: 30,
      paginationEnabled: false,
      autoParseParameters: true,
    },
  },
  latestPublishedRevision: revisionNo
    ? {
        id: `r-${revisionNo}`,
        nodeId: '7',
        revisionNo,
        sourceDraftRevision: 12,
        checksum: 'abc',
      }
    : null,
  revisions: [],
});

const publication = (
  revisionNo: number,
  enabled: boolean,
  updateAvailable: boolean,
): DataServicePublicationState => ({
  published: true,
  updateAvailable,
  source: {
    sourceType: 'DATA_DEVELOPMENT_DATA_SERVICE',
    sourceRef: '7',
    name: 'Orders API',
    sourceKind: 'DATA_SERVICE',
    status: 'PUBLISHED',
    sourceRevisionId: `r-${revisionNo}`,
    sourceRevisionNo: revisionNo,
    dataSourceId: '9',
    defaultPath: '/orders',
  },
  detail: {
    id: '88',
    name: 'Orders API',
    path: '/orders',
    runtimePath: '/api/orders',
    enabled,
    sourceRevisionNo: revisionNo,
  },
});

describe('data service delivery experience', () => {
  it('keeps published revision independent from runtime publication', () => {
    expect(dataServiceDeliveryState(context(3), undefined)).toEqual({
      draftRevision: 12,
      latestPublishedRevisionNo: 3,
      runtimeRevisionNo: undefined,
      runtimeEnabled: undefined,
      updateAvailable: false,
      runtimeState: 'NOT_ONLINE',
    });
  });

  it('distinguishes current online runtime from an outdated runtime', () => {
    expect(dataServiceDeliveryState(context(3), publication(3, true, false))).toMatchObject({
      latestPublishedRevisionNo: 3,
      runtimeRevisionNo: 3,
      runtimeEnabled: true,
      runtimeState: 'ONLINE_CURRENT',
    });
    expect(dataServiceDeliveryState(context(4), publication(3, true, true))).toMatchObject({
      latestPublishedRevisionNo: 4,
      runtimeRevisionNo: 3,
      runtimeEnabled: true,
      updateAvailable: true,
      runtimeState: 'ONLINE_OUTDATED',
    });
  });

  it('distinguishes runtime read failure from not online', () => {
    expect(dataServiceDeliveryState(context(3), undefined, true).runtimeState).toBe(
      'PUBLICATION_UNAVAILABLE',
    );
  });

  it('distinguishes immutable revision creation from publish no-op', () => {
    expect(dataServicePublishOutcome(2, { revisionNo: 3 })).toEqual({
      kind: 'PUBLISHED',
      revisionNo: 3,
    });
    expect(dataServicePublishOutcome(3, { revisionNo: 3 })).toEqual({
      kind: 'UNCHANGED',
      revisionNo: 3,
    });
  });
});
