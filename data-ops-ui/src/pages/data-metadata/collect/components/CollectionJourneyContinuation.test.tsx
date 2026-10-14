import { fireEvent, render, screen } from '@testing-library/react';
import type { CollectJobRecord, CollectRunRecord } from '@/services/metadata/types';
import CollectionJourneyContinuation, { collectionContinuationStage } from './CollectionJourneyContinuation';

const job: CollectJobRecord = {
  id: 71, jobCode: 'order_source', jobName: '订单表采集',
  providerType: 'HARVESTED', dataSourceId: 18, databaseName: 'trade_db',
  schemaName: 'public', tablePattern: 'trade_order%',
  enabled: false, dryRunPassed: false,
};
const preview: CollectRunRecord = {
  jobId: 71, runId: 900, dryRun: true, status: 'SUCCESS', cntPartialFailed: 0,
};
const collected: CollectRunRecord = { ...preview, runId: 901, dryRun: false };

function renderJourney(run: CollectRunRecord | null, extras: {
  resultUnknown?: boolean; canExecute?: boolean;
} = {}) {
  const callbacks = {
    onPreview: jest.fn(), onConfirmRun: jest.fn(), onHistory: jest.fn(),
    onOpenJob: jest.fn(), onDismiss: jest.fn(), sourceReturn: jest.fn(),
  };
  render(<CollectionJourneyContinuation
    job={job} sourceName="交易库" run={run} busy={false}
    canExecute={extras.canExecute ?? true} resultUnknown={extras.resultUnknown}
    {...callbacks}
  />);
  return callbacks;
}

it('only reports preview readiness from a matching successful persisted Run receipt', () => {
  expect(collectionContinuationStage(job, null, false)).toBe('saved');
  expect(collectionContinuationStage(job, { ...preview, jobId: 72 }, false)).toBe('saved');
  expect(collectionContinuationStage(job, { ...preview, runId: 0 }, false)).toBe('saved');
  expect(collectionContinuationStage(job, { ...preview, dryRun: undefined }, false)).toBe('needs-review');
  expect(collectionContinuationStage(job, { ...preview, cntPartialFailed: 1 }, false)).toBe('needs-review');
  expect(collectionContinuationStage(job, { ...preview, status: 'RUNNING' }, false)).toBe('needs-review');
  expect(collectionContinuationStage(job, preview, true)).toBe('needs-review');
  expect(collectionContinuationStage(job, preview, false)).toBe('preview-ok');
  expect(collectionContinuationStage(job, collected, false)).toBe('run-ok');
});

it('puts preview first and keeps direct run secondary without executing either on render', () => {
  const actions = renderJourney(null);
  expect(screen.getByText('任务已保存')).toBeInTheDocument();
  fireEvent.click(screen.getByText('预演采集范围'));
  expect(actions.onPreview).toHaveBeenCalledTimes(1);
  expect(actions.onConfirmRun).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('直接采集'));
  expect(actions.onConfirmRun).toHaveBeenCalledTimes(1);
});

it('separates preview completion from a formal collection receipt', () => {
  const actions = renderJourney(preview);
  expect(screen.getByText('已收到通过的预览回执')).toBeInTheDocument();
  expect(screen.getByText(/尚未写入元数据目录/)).toBeInTheDocument();
  fireEvent.click(screen.getByText('正式采集'));
  expect(actions.onConfirmRun).toHaveBeenCalledTimes(1);
  expect(actions.onPreview).not.toHaveBeenCalled();
});

it('gives run history precedence if execution timed out or partial failures occurred', () => {
  const actions = renderJourney({ ...collected, status: 'FAILED' }, { resultUnknown: true });
  expect(screen.getByText('运行结果待核对')).toBeInTheDocument();
  fireEvent.click(screen.getByText(/核对运行历史/));
  expect(actions.onHistory).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('正式采集')).not.toBeInTheDocument();
});

it('shows successful job evidence and trusted origin return only after a formal success', () => {
  const actions = renderJourney(collected);
  expect(screen.getByText('已收到成功采集回执')).toBeInTheDocument();
  fireEvent.click(screen.getByText(/查看已采集表与运行历史/));
  fireEvent.click(screen.getByText('返回原来源表'));
  expect(actions.onOpenJob).toHaveBeenCalledTimes(1);
  expect(actions.sourceReturn).toHaveBeenCalledTimes(1);
});

it('does not make execution actionable without Metadata permission', () => {
  const actions = renderJourney(null, { canExecute: false });
  const button = screen.getByText('预演采集范围').closest('button');
  expect(button).toBeDisabled();
  expect(actions.onPreview).not.toHaveBeenCalled();
});
