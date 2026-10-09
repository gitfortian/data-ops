import type {
  DevelopmentDataServiceNodeContext,
  DevelopmentDataServiceRevision,
} from '@/services/data-development';
import type { DataServicePublicationState } from '../../data-service-runtime-publication';

export type DevelopmentDataServiceRuntimeState =
  | 'UNPUBLISHED'
  | 'PUBLICATION_UNAVAILABLE'
  | 'NOT_ONLINE'
  | 'ONLINE_CURRENT'
  | 'ONLINE_OUTDATED'
  | 'OFFLINE_CURRENT'
  | 'OFFLINE_OUTDATED';

export interface DevelopmentDataServiceDeliveryState {
  draftRevision: number;
  latestPublishedRevisionNo?: number;
  runtimeRevisionNo?: number;
  runtimeEnabled?: boolean;
  updateAvailable: boolean;
  runtimeState: DevelopmentDataServiceRuntimeState;
}

/**
 * Data Service Revision truth and Runtime publication truth are deliberately separate.
 * Publishing a DS Revision never implies that the Runtime has been updated or enabled.
 */
export const dataServiceDeliveryState = (
  context: DevelopmentDataServiceNodeContext | undefined,
  publication: DataServicePublicationState | undefined,
  publicationUnavailable = false,
): DevelopmentDataServiceDeliveryState => {
  const latestPublishedRevisionNo = context?.latestPublishedRevision?.revisionNo;
  const runtimeRevisionNo = publication?.detail?.sourceRevisionNo ?? undefined;
  const runtimeEnabled = publication?.detail?.enabled;
  const updateAvailable = Boolean(publication?.updateAvailable);

  let runtimeState: DevelopmentDataServiceRuntimeState;
  if (!latestPublishedRevisionNo) runtimeState = 'UNPUBLISHED';
  else if (publicationUnavailable) runtimeState = 'PUBLICATION_UNAVAILABLE';
  else if (!publication?.published) runtimeState = 'NOT_ONLINE';
  else if (runtimeEnabled) {
    runtimeState = updateAvailable ? 'ONLINE_OUTDATED' : 'ONLINE_CURRENT';
  } else {
    runtimeState = updateAvailable ? 'OFFLINE_OUTDATED' : 'OFFLINE_CURRENT';
  }

  return {
    draftRevision: context?.draft?.draftRevision || 0,
    latestPublishedRevisionNo,
    runtimeRevisionNo,
    runtimeEnabled,
    updateAvailable,
    runtimeState,
  };
};

export type DevelopmentDataServicePublishOutcome =
  | { kind: 'PUBLISHED'; revisionNo: number }
  | { kind: 'UNCHANGED'; revisionNo: number };

export const dataServicePublishOutcome = (
  previousRevisionNo: number | undefined,
  published: Pick<DevelopmentDataServiceRevision, 'revisionNo'>,
): DevelopmentDataServicePublishOutcome =>
  previousRevisionNo === published.revisionNo
    ? { kind: 'UNCHANGED', revisionNo: published.revisionNo }
    : { kind: 'PUBLISHED', revisionNo: published.revisionNo };
