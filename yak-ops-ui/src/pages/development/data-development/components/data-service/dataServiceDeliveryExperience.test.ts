import type { DevelopmentDataServiceNodeContext } from '../../data-service-node-service';
import type { DataServicePublicationState } from '../../data-service-runtime-publication';
import {
  dataServiceDeliveryState,
  dataServicePublishCreatesRevision,
} from './dataServiceDeliveryExperience';

const context = (revisionNo?: number): DevelopmentDataServiceNodeContext => ({
  nodeId: '7',
  nodeName: 'Orders API',
  configured: true,
  draft: {
    nodeId: '7',
    draftRevision: 4,
    definition: {
      dataSourceId: '9',
      sql: 'select * from orders where id = :id',
      serviceName: 'Orders API',
      path: '/orders',
      method: 'GET',
      parameters: [],
      responseFields: [],
      maxRows: 1000,
      timeoutSeconds: 30,
      paginationEnabled: false,
      autoParseParameters: true,
    },
  },
  latestPublishedRevision: revisionNo
    ? {
        id: `rev-${revisionNo}`,
        nodeId: '7',
        revisionNo,
        sourceDraftRevision: 4,
        checksum: `checksum-${revisionNo}`,
      }
    : null,
  revisions: [],
});

const publication = (
  sourceRevisionNo: number,
  enabled: boolean,
  updateAvailable = false,
): DataServicePublicationState => ({
  published: true,
  updateAvailable,
  source: {
    sourceType: 'DATA_DEVELOPMENT_DATA_SERVICE',
    sourceRef: '7',
    name: 'Orders API',
    sourceKind: 'DATA_SERVICE',
    status: 'PUBLISHED',
    sourceRevisionId: `rev-${sourceRevisionNo}`,
    sourceRevisionNo,
    dataSourceId: '9',
    defaultPath: '/orders',
  },
  detail: {
    id: 'api-55',
    name: 'Orders API',
    path: '/orders',
    runtimePath: '/api/orders',
    enabled,
    sourceRevisionId: `rev-${sourceRevisionNo}`,
    sourceRevisionNo,
  },
});

describe('data service delivery experience', () => {
  it('keeps saved Draft identity separate from immutable publication identity', () => {
    expect(dataServiceDeliveryState(context(), false, undefined)).toEqual({
      draftRevision: 4,
      draftState: 'SAVED',
      publishedRevisionNo: undefined,
      publishedRevisionId: undefined,
      servingState: 'NO_REVISION',
      runtimeRevisionNo: undefined,
      runtimeEnabled: undefined,
    });
  });

  it('keeps local unsaved edits separate from the published and serving revisions', () => {
    expect(dataServiceDeliveryState(context(5), true, publication(5, true))).toMatchObject({
      draftRevision: 4,
      draftState: 'LOCAL_UNSAVED',
      publishedRevisionNo: 5,
      servingState: 'ONLINE',
      runtimeRevisionNo: 5,
      runtimeEnabled: true,
    });
  });

  it('shows update available when runtime still serves an older immutable revision', () => {
    expect(
      dataServiceDeliveryState(context(6), false, publication(5, true, true)),
    ).toMatchObject({
      publishedRevisionNo: 6,
      servingState: 'UPDATE_AVAILABLE',
      runtimeRevisionNo: 5,
    });
  });

  it('distinguishes offline runtime from publication-state unavailability', () => {
    expect(dataServiceDeliveryState(context(5), false, publication(5, false))).toMatchObject({
      servingState: 'OFFLINE',
      runtimeEnabled: false,
    });
    expect(dataServiceDeliveryState(context(5), false, undefined, true)).toMatchObject({
      servingState: 'UNAVAILABLE',
    });
  });

  it('treats a new immutable revision as a publish result rather than a runtime online action', () => {
    expect(dataServicePublishCreatesRevision(4, 5)).toBe(true);
    expect(dataServicePublishCreatesRevision(5, 5)).toBe(false);
  });
});
