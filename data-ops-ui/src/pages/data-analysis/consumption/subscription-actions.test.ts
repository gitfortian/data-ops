import type { Subscription, SubscriptionStatus } from '@/services/consumption';
import { findManagedSubscription, nextSubscriptionAction } from './subscription-actions';

const row = (status: SubscriptionStatus, identity = 'alice', productKey = 'DATASET:9'): Subscription => ({
  id: 5, projectId: 1, productKey,
  consumerRef: { consumerType: 'USER', sourceDomain: 'SECURITY_PRINCIPAL', sourceIdentity: identity },
  consumptionMode: 'QUERY', status,
  createdBy: 'alice', createdAt: '2026-10-08T10:00:00',
  updatedBy: 'alice', updatedAt: '2026-10-08T10:00:00',
});

describe('Consumption subscription lifecycle UI', () => {
  it('declares only when no matching relationship exists and never re-declares a revoked record', () => {
    expect(nextSubscriptionAction()).toBe('SUBSCRIBE');
    expect(nextSubscriptionAction(row('ACTIVE'))).toBe('SUSPEND');
    expect(nextSubscriptionAction(row('SUSPENDED'))).toBe('RESUME');
    expect(nextSubscriptionAction(row('REVOKED'))).toBe('NONE');
  });

  it('looks up suspended and revoked Dataset relationships by authenticated principal, mode and product', () => {
    const revoked = row('REVOKED');
    expect(findManagedSubscription([revoked], 'DATASET:9', 'DATASET', 'QUERY', 'alice')).toBe(revoked);
    expect(findManagedSubscription([revoked], 'DATASET:9', 'DATASET', 'QUERY', 'mallory')).toBeUndefined();
    expect(findManagedSubscription([revoked], 'DATASET:9', 'DATASET', 'PREVIEW', 'alice')).toBeUndefined();
    expect(findManagedSubscription([revoked], 'DATASET:10', 'DATASET', 'QUERY', 'alice')).toBeUndefined();
    expect(findManagedSubscription([revoked], 'DATASET:9', 'DATASET', 'QUERY', '')).toBeUndefined();
  });

  it('uses the selected Data Service Consumer stable ID, not a display name or another Consumer', () => {
    const dataService = {
      ...row('SUSPENDED', '21', 'DATA_SERVICE:88'),
      consumptionMode: 'API_INVOKE' as const,
      consumerRef: { consumerType: 'DATA_SERVICE' as const,
        sourceDomain: 'DATA_SERVICE_CONSUMER', sourceIdentity: '21', displayHint: 'Same Name' },
    };
    expect(findManagedSubscription([dataService], 'DATA_SERVICE:88', 'DATA_SERVICE', 'API_INVOKE', 'alice', 21)).toEqual(dataService);
    expect(findManagedSubscription([dataService], 'DATA_SERVICE:88', 'DATA_SERVICE', 'API_INVOKE', 'alice', 22)).toBeUndefined();
    expect(findManagedSubscription([dataService], 'DATA_SERVICE:88', 'DATA_SERVICE', 'API_INVOKE', 'alice')).toBeUndefined();
  });
});
