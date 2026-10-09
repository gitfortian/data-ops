import type { DevelopmentDatasetNodeContext } from '@/services/data-development';

export type DevelopmentDatasetDraftState =
  | 'NOT_CREATED'
  | 'LOCAL_UNSAVED'
  | 'SAVED';

export interface DevelopmentDatasetDeliveryState {
  datasetId?: string;
  draftState: DevelopmentDatasetDraftState;
  publishedVersionNo?: number;
  published: boolean;
  deliveryStatus?: 'ONLINE' | 'OFFLINE';
}

/**
 * Dataset Save owns persisted Draft state only; immutable DV identity is created by Publish.
 * Keep those identities independent in the UI instead of inferring a DV from a successful Save.
 */
export const datasetDeliveryState = (
  context: DevelopmentDatasetNodeContext | undefined,
  localDirty: boolean,
): DevelopmentDatasetDeliveryState => {
  const dataset = context?.dataset || undefined;
  return {
    datasetId: dataset?.datasetId ? String(dataset.datasetId) : undefined,
    draftState: localDirty
      ? 'LOCAL_UNSAVED'
      : dataset
        ? 'SAVED'
        : 'NOT_CREATED',
    publishedVersionNo: dataset?.currentVersion?.versionNo,
    published: Boolean(dataset?.currentVersion),
    deliveryStatus: dataset?.status,
  };
};

export type DevelopmentDatasetPublishOutcome =
  | { kind: 'PUBLISHED'; versionNo: number }
  | { kind: 'UNCHANGED'; versionNo: number }
  | { kind: 'UNAVAILABLE' };

/** The backend may no-op Publish when the saved Draft matches the current immutable version. */
export const datasetPublishOutcome = (
  beforeVersionNo?: number,
  afterVersionNo?: number,
): DevelopmentDatasetPublishOutcome => {
  if (!afterVersionNo) return { kind: 'UNAVAILABLE' };
  if (beforeVersionNo === afterVersionNo) {
    return { kind: 'UNCHANGED', versionNo: afterVersionNo };
  }
  return { kind: 'PUBLISHED', versionNo: afterVersionNo };
};
