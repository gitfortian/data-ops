import { getConsumerImpact, listSubscriptions } from '@/services/consumption';
import { loadConsumptionRelationships } from './relationship-load';

jest.mock('@/services/consumption', () => ({
  getConsumerImpact: jest.fn(),
  listSubscriptions: jest.fn(),
}));

const impactMock = getConsumerImpact as jest.Mock;
const subscriptionMock = listSubscriptions as jest.Mock;

beforeEach(() => { jest.resetAllMocks(); });

describe('Consumption relationship evidence read', () => {
  it('reads both independent sources for the exact product identity', async () => {
    const impact = { subscriptionState: 'READY', usageState: 'EMPTY', consumers: [] };
    const subscriptions = [{ id: 8, status: 'ACTIVE' }];
    impactMock.mockResolvedValue(impact);
    subscriptionMock.mockResolvedValue(subscriptions);
    await expect(loadConsumptionRelationships('DATASET:9')).resolves.toEqual({
      impact, impactIssue: '', subscriptions, subscriptionIssue: '',
    });
    expect(getConsumerImpact).toHaveBeenCalledWith('DATASET:9');
    expect(listSubscriptions).toHaveBeenCalledWith('DATASET:9');
  });

  it('keeps valid subscription evidence when impact provider fails instead of claiming no consumers', async () => {
    impactMock.mockRejectedValue(new Error('impact offline'));
    subscriptionMock.mockResolvedValue([{ id: 8 }]);
    const result = await loadConsumptionRelationships('DATASET:9');
    expect(result.impact).toBeNull();
    expect(result.impactIssue).toBe('impact offline');
    expect(result.subscriptions).toHaveLength(1);
    expect(result.subscriptionIssue).toBe('');
  });

  it('does not claim successful empty subscriptions when subscription provider is unavailable', async () => {
    impactMock.mockResolvedValue({ subscriptionState: 'READY', usageState: 'READY', consumers: [{ id: 4 }] });
    subscriptionMock.mockRejectedValue(new Error('subscription forbidden'));
    const result = await loadConsumptionRelationships('DATA_SERVICE:11');
    expect(result.impact?.consumers).toHaveLength(1);
    expect(result.subscriptionIssue).toBe('subscription forbidden');
    expect(result.subscriptions).toEqual([]);
  });

  it('reports both failures independently without inventing a Consumer or Usage success', async () => {
    impactMock.mockRejectedValue('network offline');
    subscriptionMock.mockRejectedValue('network offline');
    const result = await loadConsumptionRelationships('DATASET:9');
    expect(result).toEqual({
      impact: null, impactIssue: '消费影响暂不可用',
      subscriptions: [], subscriptionIssue: '消费订阅暂不可用',
    });
  });
});
