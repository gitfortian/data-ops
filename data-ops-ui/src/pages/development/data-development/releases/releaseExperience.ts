import type {
  DevelopmentReleaseStatus,
  DevelopmentReleaseSummary,
} from '../types';

export interface DevelopmentReleaseRevisionState {
  activeRevisionNo: number;
  latestPublishedRevisionNo: number;
  serving: boolean;
  activeButNotServing: boolean;
  hasNewerPublishedRevision: boolean;
}

/**
 * Task Catalog currentRevision is the durable active pointer even while OFFLINE.
 * Serving state is therefore derived independently from the active revision identity.
 */
export const releaseRevisionState = (
  release: Pick<
    DevelopmentReleaseSummary,
    'status' | 'currentRevisionNo' | 'latestRevisionNo' | 'hasNewerRevision'
  >,
): DevelopmentReleaseRevisionState => ({
  activeRevisionNo: release.currentRevisionNo,
  latestPublishedRevisionNo: release.latestRevisionNo,
  serving: release.status === 'ONLINE',
  activeButNotServing: release.status === 'OFFLINE',
  hasNewerPublishedRevision:
    release.hasNewerRevision || release.latestRevisionNo > release.currentRevisionNo,
});

export const isReleaseMutableStatus = (status: DevelopmentReleaseStatus) =>
  status === 'ONLINE' || status === 'OFFLINE';

export const releaseNodeUrl = (nodeId: string | number) =>
  `/data-development?nodeId=${encodeURIComponent(String(nodeId))}`;
