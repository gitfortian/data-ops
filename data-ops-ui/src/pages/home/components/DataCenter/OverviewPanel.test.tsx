import { render, screen } from '@testing-library/react';
import type { HomeDataCenterOverview } from '@/services/home';
import { OverviewPanel } from './OverviewPanel';

const mockIntl = { locale: 'zh-CN', formatMessage: ({ id }: { id: string }) => id };
jest.mock('@umijs/max', () => ({ useIntl: () => mockIntl }));
jest.mock('./TrendChart', () => ({ TrendChart: () => <div data-testid="run-trend" /> }));

const overview: HomeDataCenterOverview = {
  period: { start: '2026-09-27', end: '2026-10-03' },
  trend: { labels: ['09-27', '10-03'], values: [0, 0] },
  metrics: { successCount: 0, runningCount: 0, failedCount: 0, scheduleCount: 0, processedRecords: 0, avgDurationMs: 0 },
  compare: { successCount: 0, runningCount: 0, failedCount: 0, scheduleCount: 0, processedRecordsRate: 0, avgDurationMs: 0 },
};

test('无运行记录的零值时间桶显示空态，已有运行事实仍保留总览', () => {
  const { rerender } = render(<OverviewPanel overview={overview} periodKey="7d" periodLabel="近七天" loading={false} failed={false} />);
  expect(screen.queryByTestId('run-trend')).not.toBeInTheDocument();
  expect(screen.getByText('pages.home.dataCenter.overview.empty')).toBeInTheDocument();
  rerender(<OverviewPanel overview={{ ...overview, metrics: { ...overview.metrics, runningCount: 1 } }} periodKey="7d" periodLabel="近七天" loading={false} failed={false} />);
  expect(screen.getByTestId('run-trend')).toBeInTheDocument();
});
