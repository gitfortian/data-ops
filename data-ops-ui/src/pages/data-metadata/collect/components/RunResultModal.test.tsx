import { fireEvent, render, screen } from '@testing-library/react';
import { history } from '@umijs/max';
import type { CollectRunRecord } from '@/services/metadata/types';
import RunResultModal from './RunResultModal';
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
const run: CollectRunRecord = {
  runId: 8,
  jobId: 71,
  providerType: 'HARVESTED',
  dryRun: false,
  status: 'SUCCESS',
  cntTotal: 30,
  cntNew: 2,
  cntChanged: 1,
  cntUnchanged: 27,
  startedAt: '2026-10-13T09:00:00',
  finishedAt: '2026-10-13T09:00:01',
  durationMs: 1000,
  scopeSnapshot: JSON.stringify({
    dataSourceId: 18,
    databaseName: 'trade_db',
    schemaName: 'public',
    collectColumns: true,
  }),
};
it('uses persisted run scope and presents precise statistics without implementation narration', async () => {
  const close = jest.fn();
  render(<RunResultModal run={run} title="采集结果 · 订单" sourceNames={{ 18: '订单库' }} onClose={close} />);
  await screen.findByText('采集已完成');
  expect(screen.getByText('元数据总数')).toBeInTheDocument();
  expect(screen.getByText('trade_db')).toBeInTheDocument();
  expect(screen.getByText('订单库')).toBeInTheDocument();
  expect(screen.queryByText('接下来做什么？')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /查看已采集表/ }));
  expect(close).toHaveBeenCalled();
  expect(history.push).toHaveBeenCalledWith('/data-asset/catalog?view=entity&datasourceId=18&databaseName=trade_db');
});
it('separates preview estimates from actual writes', async () => {
  render(<RunResultModal run={{ ...run, dryRun: true, cntGone: 2 }} title="dry-run 预演 · 订单" onClose={jest.fn()} />);
  await screen.findByText('采集预览已完成');
  expect(screen.getByText('预计新增')).toBeInTheDocument();
  expect(screen.getByText('2 项元数据预计撤销')).toBeInTheDocument();
  expect(document.body.textContent).not.toMatch(/dry-run|冻结|这个 Run/);
  expect(screen.queryByRole('button', { name: /查看已采集表/ })).not.toBeInTheDocument();
});
it('does not invent historical scope or silently report complete success for partial failures', async () => {
  render(
    <RunResultModal
      run={{ ...run, scopeSnapshot: undefined, cntPartialFailed: 3 }}
      title="采集结果"
      onClose={jest.fn()}
    />,
  );
  await screen.findByText('采集部分完成');
  expect(screen.getByText('3 项处理失败')).toBeInTheDocument();
  expect(screen.getByText('本次采集范围暂不可用')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /查看已采集表/ })).not.toBeInTheDocument();
});
