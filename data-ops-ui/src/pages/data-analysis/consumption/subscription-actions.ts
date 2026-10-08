import type { ConsumptionMode, ProductType, Subscription } from '@/services/consumption';

export type SubscriptionAction = 'SUBSCRIBE' | 'SUSPEND' | 'RESUME' | 'NONE';

/** Retain the owning record's terminal state rather than treating non-ACTIVE as absent. */
export const findManagedSubscription = (
  subscriptions: Subscription[],
  productKey: string,
  productType: ProductType,
  mode: ConsumptionMode,
  actor: string,
  selectedConsumerId?: number,
): Subscription | undefined => subscriptions.find((item) => {
  if (item.productKey !== productKey || item.consumptionMode !== mode) return false;
  const ref = item.consumerRef;
  if (productType === 'DATASET') {
    return Boolean(actor) && ref.consumerType === 'USER'
      && ref.sourceDomain === 'SECURITY_PRINCIPAL' && ref.sourceIdentity === actor;
  }
  return selectedConsumerId !== undefined && ref.consumerType === 'DATA_SERVICE'
    && ref.sourceDomain === 'DATA_SERVICE_CONSUMER'
    && ref.sourceIdentity === String(selectedConsumerId);
});

export const nextSubscriptionAction = (
  existing?: Pick<Subscription, 'status'>,
): SubscriptionAction => {
  if (!existing) return 'SUBSCRIBE';
  switch (existing.status) {
    case 'ACTIVE': return 'SUSPEND';
    case 'SUSPENDED': return 'RESUME';
    case 'REVOKED': return 'NONE';
  }
};
