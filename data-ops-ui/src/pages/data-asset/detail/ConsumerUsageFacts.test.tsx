import { render, screen } from '@testing-library/react';
import ConsumerUsageFacts from './ConsumerUsageFacts';

it('keeps failed-side counts unknown and shows persisted window limits and no reconciliation', () => {
  render(<ConsumerUsageFacts value={{ consumerCount: 1, dashboardCount: 1, successfulUsageCount: 2,
    activeSubscriptionCount: null, subscriptionState: 'FORBIDDEN', usageState: 'READY',
    subscriptionWindowLimit: 200, usageWindowLimit: 2, subscriptionWindowState: 'UNKNOWN',
    usageWindowState: 'LIMIT_REACHED', sourceReconciliation: 'NOT_PERFORMED' }} />);
  expect(screen.getByText(/成功使用 2 次，活动订阅 未知 个/)).toBeInTheDocument();
  expect(screen.queryByText(/活动订阅 0 个/)).not.toBeInTheDocument();
  expect(screen.getByText(/有效订阅来源：无权读取；窗口最多 200 行，范围未知/)).toBeInTheDocument();
  expect(screen.getByText(/归一化使用来源：可读；窗口最多 2 行，已满窗，可能遗漏更早记录/)).toBeInTheDocument();
  expect(screen.getByText(/本次未同步原始来源.*可读窗口的去重并集/)).toBeInTheDocument();
});

it('distinguishes a readable zero from missing evidence without claiming complete history', () => {
  render(<ConsumerUsageFacts value={{ consumerCount: 1, successfulUsageCount: 0, activeSubscriptionCount: 1,
    subscriptionState: 'READY', usageState: 'EMPTY', subscriptionWindowLimit: 200, usageWindowLimit: 200,
    subscriptionWindowState: 'WITHIN_LIMIT', usageWindowState: 'WITHIN_LIMIT', sourceReconciliation: 'NOT_PERFORMED' }} />);
  expect(screen.getByText(/成功使用 0 次，活动订阅 1 个/)).toBeInTheDocument();
  expect(screen.getByText(/归一化使用来源：窗口内为空.*未满窗，不证明历史完整/)).toBeInTheDocument();
});
