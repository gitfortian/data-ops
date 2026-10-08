import type {
  DataServiceInvocationEvidence,
  DataServiceInvocationEvidenceRecord,
} from '@/services/data-service';

/**
 * Exact persisted source lookup, not a bounded recent-window guess.
 * All four facts must agree before a UI may say "verified":
 * FOUND, exact decimal BIGINT, owning API and non-null persisted record.
 */
export const verifiedPersistedInvocation = (
  evidence: DataServiceInvocationEvidence | null,
  apiId: number,
  invocationId?: string,
): DataServiceInvocationEvidenceRecord | undefined => {
  if (!Number.isSafeInteger(apiId) || apiId <= 0
    || !invocationId || !/^[1-9]\d*$/.test(invocationId)
    || evidence?.state !== 'FOUND' || !evidence.record) return undefined;
  if (evidence.record.id !== invocationId
    || evidence.record.apiId !== String(apiId)) return undefined;
  return evidence.record;
};
