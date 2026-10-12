import { classifyReconcileLastRun, formatHistoricalReconcileCounts } from './reconcileEvidence';

describe('Asset provider historical reconciliation evidence', () => {
  it('does not treat absent or legacy empty history as a successful run', () => {
    expect(classifyReconcileLastRun(null)).toBe('NEVER');
    expect(classifyReconcileLastRun({})).toBe('UNKNOWN');
    expect(classifyReconcileLastRun({ scanned: 0, created: 0, changed: 0, restored: 0 }))
      .toBe('UNKNOWN');
  });

  it('only recognizes completed, countable historical runs, not a new request receipt', () => {
    const lastRun = { at: '2026-10-12T09:00:00', scanned: 0, created: 0, changed: 0, restored: 0 };
    expect(classifyReconcileLastRun(lastRun)).toBe('SUCCESS');
    expect(formatHistoricalReconcileCounts(lastRun)).toBe('0 / 0 / 0 / 0');
    expect(classifyReconcileLastRun({ ...lastRun, scanned: undefined })).toBe('UNKNOWN');
  });

  it('surfaces failure even when other historical statistics are present', () => {
    expect(classifyReconcileLastRun({
      at: '2026-10-12T09:00:00', scanned: 4, created: 2,
      changed: 0, restored: 0, error: 'Provider failed',
    })).toBe('FAILED');
    expect(formatHistoricalReconcileCounts({ scanned: 2 })).toBe('2 / ? / ? / ?');
  });
});
