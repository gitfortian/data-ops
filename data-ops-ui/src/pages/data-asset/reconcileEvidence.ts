import type { ReconcileLastRun } from '@/services/data-asset/types';

/**
 * Reconcile /status is historical provider lastRun, NOT the receipt of the
 * reconcile request currently submitted by the user.
 * A truthy empty object (legacy no-history JSON) is not evidence of success.
 */
export type ReconcileEvidence = 'NEVER' | 'UNKNOWN' | 'FAILED' | 'SUCCESS';

export const classifyReconcileLastRun = (
  lastRun?: ReconcileLastRun | null,
): ReconcileEvidence => {
  if (lastRun == null) return 'NEVER';
  if (typeof lastRun.error === 'string' && lastRun.error.trim()) return 'FAILED';
  const counts = [lastRun.scanned, lastRun.created, lastRun.changed, lastRun.restored];
  const hasCompleteCounts = counts.every(
    (value) => typeof value === 'number' && Number.isFinite(value) && value >= 0,
  );
  if (typeof lastRun.at === 'string' && lastRun.at.trim() && hasCompleteCounts) {
    return 'SUCCESS';
  }
  return 'UNKNOWN';
};

/** Unknown numeric values must not be silently converted to zero. */
export const formatHistoricalReconcileCounts = (lastRun?: ReconcileLastRun | null): string => {
  if (!lastRun) return '-';
  return [lastRun.scanned, lastRun.created, lastRun.changed, lastRun.restored]
    .map((value) => typeof value === 'number' && Number.isFinite(value) ? String(value) : '?')
    .join(' / ');
};
