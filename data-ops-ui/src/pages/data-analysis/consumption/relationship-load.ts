import { getConsumerImpact, listSubscriptions, type ConsumerImpact, type Subscription } from '@/services/consumption';

export interface RelationshipReadResult {
  impact: ConsumerImpact | null;
  impactIssue: string;
  subscriptions: Subscription[];
  subscriptionIssue: string;
}

const errorMessage = (reason: unknown, fallback: string): string =>
  reason instanceof Error ? reason.message : fallback;

// Partial failure must not turn an unreadable provider into a verified empty list.
// Access/Subscription/Usage remain distinct owning facts; this is display-only composition.
export const loadConsumptionRelationships = async (productKey: string): Promise<RelationshipReadResult> => {
  const [impact, subscriptions] = await Promise.allSettled([
    getConsumerImpact(productKey),
    listSubscriptions(productKey),
  ]);
  return {
    impact: impact.status === 'fulfilled' ? impact.value : null,
    impactIssue: impact.status === 'rejected' ? errorMessage(impact.reason, '消费影响暂不可用') : '',
    subscriptions: subscriptions.status === 'fulfilled' ? subscriptions.value : [],
    subscriptionIssue: subscriptions.status === 'rejected'
      ? errorMessage(subscriptions.reason, '消费订阅暂不可用') : '',
  };
};
