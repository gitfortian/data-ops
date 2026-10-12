import { canEnableCollectSchedule, supportsCollectTaskExecution } from './collectTaskCapabilities';

describe('metadata collection task presentation capabilities', () => {
  it('keeps physical harvest executable even when the scheduled task is disabled', () => {
    expect(supportsCollectTaskExecution('HARVESTED')).toBe(true);
  });

  it('never advertises the unimplemented registered reconciliation executor', () => {
    expect(supportsCollectTaskExecution('REGISTERED')).toBe(false);
    expect(canEnableCollectSchedule({ providerType: 'REGISTERED', dryRunPassed: true })).toBe(false);
  });

  it('requires a current passing preview only to enable scheduling, not to run manually', () => {
    expect(canEnableCollectSchedule({ providerType: 'HARVESTED', dryRunPassed: false })).toBe(false);
    expect(canEnableCollectSchedule({ providerType: 'HARVESTED', dryRunPassed: true })).toBe(true);
  });
});
