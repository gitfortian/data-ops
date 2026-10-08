import type { DataServiceConsumer } from '@/services/data-service/consumer';
import type { ConsumerRef } from '@/services/consumption';
import {
  eligibleConfiguredDataServiceConsumers,
  inspectManagedConsumerConfiguration,
  sourceConsumerAccessState,
} from './managed-consumer-configuration';

const ref = (id: string, type: ConsumerRef['consumerType'] = 'DATA_SERVICE', domain = 'DATA_SERVICE_CONSUMER'): ConsumerRef => ({
  consumerType: type, sourceDomain: domain, sourceIdentity: id, displayHint: 'Mutable display label',
});

const caller = (
  id: number, enabled: boolean, scope: DataServiceConsumer['accessScope'],
  ids: number[], keys: number,
): DataServiceConsumer => ({
  id, name: 'Caller ' + id, description: null, enabled,
  accessScope: scope, apiIds: ids, apiCount: ids.length,
  keyCount: keys, activeKeyCount: keys, ipAccessMode: 'NONE',
  ipRuleCount: 0, defaultRateLimitPerMinute: 60,
});

const inspect = (
  id: string, consumers: DataServiceConsumer[], state: 'READY' | 'FORBIDDEN' | 'UNAVAILABLE' | 'LOADING' = 'READY',
) => inspectManagedConsumerConfiguration(ref(id), 'DATA_SERVICE', '201', state, consumers);

describe('managed consumer source configuration is separate from observed Usage', () => {
  it('distinguishes enabled scoped consumer with active key from a verified approval', () => {
    const result = inspect('10', [caller(10, true, 'SELECTED', [201], 1)]);
    expect(result.state).toBe('CONFIGURED');
    expect(result.detail).toContain('不代表');
    expect(eligibleConfiguredDataServiceConsumers('201', [caller(10, true, 'SELECTED', [201], 1)])).toHaveLength(1);
  });

  it('does not confuse no configured Key, disabled consumer and a missing grant', () => {
    expect(inspect('10', [caller(10, true, 'SELECTED', [201], 0)]).state).toBe('NO_ACTIVE_KEYS');
    expect(inspect('10', [caller(10, false, 'ALL', [], 3)]).state).toBe('DISABLED');
    expect(inspect('10', [caller(10, true, 'SELECTED', [200], 3)]).state).toBe('NOT_GRANTED');
    expect(inspect('10', [caller(10, true, 'ALL', [], 3)]).state).toBe('CONFIGURED');
  });

  it('keeps unreadable provider and partial roles distinct from a verified missing object', () => {
    expect(inspect('10', [], 'FORBIDDEN').state).toBe('NOT_READABLE');
    expect(inspect('10', [], 'UNAVAILABLE').state).toBe('NOT_READABLE');
    expect(inspect('10', [], 'LOADING').state).toBe('NOT_READABLE');
    expect(inspect('10', []).state).toBe('NOT_FOUND');
    expect(sourceConsumerAccessState(false, false, false)).toBe('FORBIDDEN');
    expect(sourceConsumerAccessState(true, true, false)).toBe('LOADING');
    expect(sourceConsumerAccessState(true, false, true)).toBe('UNAVAILABLE');
  });

  it('does not invent a source configuration for Dashboard/USER/JOB/TEAM or mismatched domain', () => {
    const known = [caller(10, true, 'ALL', [], 1)];
    expect(inspectManagedConsumerConfiguration(ref('10', 'DASHBOARD', 'DASHBOARD'), 'DATA_SERVICE', '201', 'READY', known).state).toBe('NOT_APPLICABLE');
    expect(inspectManagedConsumerConfiguration(ref('10', 'USER', 'SECURITY_PRINCIPAL'), 'DATA_SERVICE', '201', 'READY', known).state).toBe('NOT_APPLICABLE');
    expect(inspectManagedConsumerConfiguration(ref('10', 'JOB', 'JOB'), 'DATA_SERVICE', '201', 'READY', known).state).toBe('NOT_APPLICABLE');
    expect(inspectManagedConsumerConfiguration(ref('10', 'TEAM', 'TEAM'), 'DATA_SERVICE', '201', 'READY', known).state).toBe('NOT_APPLICABLE');
    expect(inspectManagedConsumerConfiguration(ref('10', 'DATA_SERVICE', 'DATA_SERVICE'), 'DATA_SERVICE', '201', 'READY', known).state).toBe('NOT_APPLICABLE');
    expect(inspectManagedConsumerConfiguration(ref('10'), 'DATASET', '201', 'READY', known).state).toBe('NOT_APPLICABLE');
  });

  it('rejects unsafe source and consumer BIGINT identity without rounding to another grant', () => {
    const known = [caller(10, true, 'ALL', [], 1), caller(11, true, 'SELECTED', [201], 1)];
    expect(inspect('9007199254740993', known).state).toBe('UNSAFE_ID');
    expect(inspectManagedConsumerConfiguration(ref('10'), 'DATA_SERVICE', '9007199254740993', 'READY', known).state).toBe('UNSAFE_ID');
    expect(eligibleConfiguredDataServiceConsumers('9007199254740993', known)).toHaveLength(0);
    expect(eligibleConfiguredDataServiceConsumers('201', [caller(10, true, 'SELECTED', [Number.MAX_SAFE_INTEGER + 1], 1)])).toHaveLength(0);
    expect(eligibleConfiguredDataServiceConsumers('201', known).map((item) => item.id)).toEqual([10, 11]);
    expect(inspect('00010', known).state).toBe('UNSAFE_ID');
  });

  it('does not match wrong id from the same Project even if display names coincide', () => {
    const known = [caller(11, true, 'ALL', [], 3)];
    expect(inspect('10', known).state).toBe('NOT_FOUND');
    expect(inspect('11', known).state).toBe('CONFIGURED');
  });
});
