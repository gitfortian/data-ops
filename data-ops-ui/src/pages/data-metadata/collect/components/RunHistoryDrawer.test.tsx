import { fireEvent, render, screen } from '@testing-library/react';
import { pageCollectRuns } from '@/services/metadata/api';
import RunHistoryDrawer from './RunHistoryDrawer';
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/services/metadata/api', () => ({ pageCollectRuns: jest.fn() }));
it('opens the selected persisted run with its scope and keeps previews separate', async () => {
  jest
    .mocked(pageCollectRuns)
    .mockResolvedValue({
      records: [
        {
          runId: 9,
          jobId: 71,
          status: 'SUCCESS',
          dryRun: true,
          triggerType: 'DRY_RUN',
          cntTotal: 30,
          startedAt: '2026-10-13T09:00:00',
          scopeSnapshot: JSON.stringify({ dataSourceId: 18, databaseName: 'historical_db', collectColumns: true }),
        },
      ],
      total: 1,
      pages: 1,
      pageNo: 1,
      pageSize: 10,
    });
  render(
    <RunHistoryDrawer
      open
      job={{
        id: 71,
        jobCode: 'orders',
        jobName: '订单采集',
        providerType: 'HARVESTED',
        databaseName: 'changed_db',
        enabled: false,
        dryRunPassed: true,
      }}
      onClose={jest.fn()}
    />,
  );
  await screen.findByRole('button', { name: '查看' });
  fireEvent.click(screen.getByRole('button', { name: '查看' }));
  await screen.findByText('采集预览已完成');
  expect(screen.getByText('historical_db')).toBeInTheDocument();
  expect(document.body.textContent).not.toMatch(/历史 Run|真实 Run|dry-run|changed_db/);
});
