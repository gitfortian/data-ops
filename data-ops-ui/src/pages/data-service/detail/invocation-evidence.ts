import type { DataServiceCallLog } from '@/services/data-service';

/**
 * Recent call logs are JSON numbers in the existing service API; never call an
 * unsafe floating-point ID an exact match to the source-owned BIGINT ref.
 * Both the call ID and its API ownership must be verified.
 */
export const verifiedInvocationFromWindow = <T extends Pick<DataServiceCallLog, 'id' | 'apiId'>>(
  records: T[],
  apiId: number,
  invocationId?: string,
): T | undefined => {
  if (!Number.isSafeInteger(apiId) || apiId <= 0
    || !invocationId || !/^[1-9]\d*$/.test(invocationId)) return undefined;
  return records.find((row) => Number.isSafeInteger(row.id)
    && row.id > 0 && row.apiId === apiId
    && String(row.id) === invocationId);
};
