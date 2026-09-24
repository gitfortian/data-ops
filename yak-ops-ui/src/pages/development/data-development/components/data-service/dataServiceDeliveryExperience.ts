import type { DevelopmentDataServiceNodeContext } from '../../data-service-node-service';
import type { DataServicePublicationState } from '../../data-service-runtime-publication';

export type DevelopmentDataServiceDraftState =
  | 'LOCAL_UNSAVED'
  | 'SAVED';

export type DevelopmentDataServiceServingState =
  | 'NO_REVISION'
  | 'NOT_PUBLISHED'
  | 'ONLINE'
  | 'OFFLINE'
  | 'UPDATE_AVAILABLE'
  | 'UNAVAILABLE';

export interface DevelopmentDataServiceDeliveryState {
  draftRevision: number;
  draftState: DevelopmentDataServiceDraftState;
  publishedRevisionNo?: number;
  publishedRevisionId?: string;
  servingState: DevelopmentDataServiceServingState;
  runtimeRevisionNo?: number;
  runtimeEnabled?: boolean;
}

/**
 * Data Service keeps four identities separate:
 * editor-local state, persisted Draft revision, immutable published Revision,
 * and the runtime publication currently serving traffic.
 */
export const dataServiceDeliveryState = (
  context: DevelopmentDataServiceNodeContext | undefined,
  localDirty: boolean,
  publication: DataServicePublicationState | undefined,
  publicationUnavailable = false,
): DevelopmentDataServiceDeliveryState => {
  const latest = context?.latestPublishedRevision || undefined;
  const runtimeRevisionNo = publication?.detail?.sourceRevisionNo ?? undefined;
  const runtimeEnabled = publication?.detail?.enabled;

  let servingState: DevelopmentDataServiceServingState;
  if (!latest) {
    servingState = 'NO_REVISION';
  } else if (publicationUnavailable) {
    servingState = 'UNAVAILABLE';
  } else if (!publication?.published || !publication.detail) {
    servingState = 'NOT_PUBLISHED';
  } else if (publication.updateAvailable) {
    servingState = 'UPDATE_AVAILABLE';
  } else if (runtimeEnabled) {
    servingState = 'ONLINE';
  } else {
    servingState = 'OFFLINE';
  }

  return {
    draftRevision: Number(context?.draft?.draftRevision || 0),
    draftState: localDirty ? 'LOCAL_UNSAVED' : 'SAVED',
    publishedRevisionNo: latest?.revisionNo,
    publishedRevisionId: latest?.id ? String(latest.id) : undefined,
    servingState,
    runtimeRevisionNo,
    runtimeEnabled,
  };
};

export const dataServicePublishCreatesRevision = (
  beforeRevisionNo?: number,
  afterRevisionNo?: number,
) => Boolean(afterRevisionNo && afterRevisionNo !== beforeRevisionNo);
