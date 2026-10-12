import type { CollectJobRecord, CollectProviderType } from '@/services/metadata/types';

/**
 * Presentation availability only. CollectRunService and the scheduler remain the
 * authoritative execution gate: REGISTERED is persistable but not runnable yet.
 */
export const supportsCollectTaskExecution = (provider: CollectProviderType): boolean =>
  provider === 'HARVESTED';

export const canEnableCollectSchedule = (
  job: Pick<CollectJobRecord, 'providerType' | 'dryRunPassed'>,
): boolean => supportsCollectTaskExecution(job.providerType) && job.dryRunPassed;
