import { Alert, Button, Tag } from 'antd';
import { ArrowRightOutlined, CheckCircleOutlined, ClockCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import type { CollectJobRecord, CollectRunRecord } from '@/services/metadata/types';

/**
 * J1 continuation is a view of the Metadata Owner's saved Job/Run receipts.
 * No client-side "step complete" flag, fake run or global collection status.
 */
export type CollectContinuationStage = 'saved' | 'preview-ok' | 'run-ok' | 'needs-review';

export function collectionContinuationStage(
  job: CollectJobRecord,
  run: CollectRunRecord | null,
  resultUnknown: boolean,
): CollectContinuationStage {
  if (resultUnknown) return 'needs-review';
  if (!run || run.jobId !== job.id || !Number.isSafeInteger(run.runId) || run.runId <= 0)
    return 'saved';
  if (run.status !== 'SUCCESS' || (run.cntPartialFailed ?? 0) > 0)
    return 'needs-review';
  if (run.dryRun === true) return 'preview-ok';
  if (run.dryRun === false) return 'run-ok';
  return 'needs-review';
}

export default function CollectionJourneyContinuation({
  job,
  sourceName,
  run,
  resultUnknown = false,
  busy,
  canExecute,
  sourceReturn,
  onPreview,
  onConfirmRun,
  onHistory,
  onOpenJob,
  onDismiss,
}: {
  job: CollectJobRecord;
  sourceName: string;
  run: CollectRunRecord | null;
  resultUnknown?: boolean;
  busy: boolean;
  canExecute: boolean;
  sourceReturn?: () => void;
  onPreview: () => void;
  onConfirmRun: () => void;
  onHistory: () => void;
  onOpenJob: () => void;
  onDismiss: () => void;
}) {
  const stage = collectionContinuationStage(job, run, resultUnknown);
  const isPreviewDone = stage === 'preview-ok';
  // Reopened jobs may have no run receipt in this browser session. The
  // persisted Owner dryRunPassed gate still proves current saved scope passed
  // preview; it does NOT prove any formal data collection has occurred.
  const persistedPreviewReady = stage === 'saved' && job.dryRunPassed;
  const isCollected = stage === 'run-ok';
  // Formal collection alone is NOT proof a separate preview was performed.
  // The backend invalidates this persisted flag when the scope is edited.
  const previewConfirmed = isPreviewDone || job.dryRunPassed;
  return (
    <section aria-label="采集任务下一步" className="collect-journey">
      <div className="collect-journey-top">
        <div>
          <div className="collect-journey-eyebrow">来源采集 · 当前任务</div>
          <h2>{job.jobName}</h2>
          <p>{sourceName} · {[job.databaseName, job.schemaName, job.tablePattern || '全部表'].filter(Boolean).join(' / ')}</p>
        </div>
        <div className="collect-journey-right">
          <Tag>{isCollected ? '已收到成功采集回执' : isPreviewDone ? '已收到通过的预览回执' : stage === 'needs-review' ? '运行结果待核对' : persistedPreviewReady ? '当前范围预演闸门已通过' : '任务已保存'}</Tag>
          <Button type="text" onClick={onDismiss}>收起引导</Button>
        </div>
      </div>
      <div className="collect-journey-stages" aria-label="任务进度">
        <div className="collect-journey-stage complete"><CheckCircleOutlined /> 1 保存任务</div>
        <div className={previewConfirmed ? 'collect-journey-stage complete' : 'collect-journey-stage'}>
          {previewConfirmed ? <CheckCircleOutlined /> : <ClockCircleOutlined />} 2 采集预览（建议）
        </div>
        <div className={isCollected ? 'collect-journey-stage complete' : 'collect-journey-stage'}>
          {isCollected ? <CheckCircleOutlined /> : <ClockCircleOutlined />} 3 正式采集与核验
        </div>
      </div>
      {stage === 'needs-review' ? (
        <Alert type="warning" showIcon icon={<ExclamationCircleOutlined />}
          message="执行结果不完整或尚未得到可靠回执"
          description="请先查看此任务的运行历史并核对实际状态，不要将超时当作采集失败或自动重试。" />
      ) : (
        <p className="collect-journey-next">
          {isCollected
            ? '正式采集已返回成功回执。可进入任务详情核对本次采集表与字段，或返回原来源继续建模。'
            : isPreviewDone
              ? '采集预览已通过，尚未写入元数据目录。下一步可确认范围并正式采集。'
              : persistedPreviewReady
                ? '已保存的当前采集范围通过了服务端预演闸门，但本页没有本次预演运行回执。可以核对历史后正式采集；尚未证明目录已更新。'
              : '任务定义已保存。建议先预览影响范围，再决定是否正式采集；直接采集保留为次级操作。'}
        </p>
      )}
      <div className="collect-journey-actions">
        {isCollected ? (
          <Button type="primary" onClick={onOpenJob}>查看已采集表与运行历史 <ArrowRightOutlined /></Button>
        ) : stage === 'needs-review' ? (
          <Button type="primary" onClick={onHistory}>核对运行历史 <ArrowRightOutlined /></Button>
        ) : isPreviewDone || persistedPreviewReady ? (
          <Button type="primary" disabled={!canExecute || busy} loading={busy} onClick={onConfirmRun}>
            正式采集 <ArrowRightOutlined />
          </Button>
        ) : (
          <Button type="primary" disabled={!canExecute || busy} loading={busy} onClick={onPreview}>
            预演采集范围 <ArrowRightOutlined />
          </Button>
        )}
        {!isPreviewDone && !persistedPreviewReady && !isCollected && stage !== 'needs-review' ? (
          <Button disabled={!canExecute || busy} onClick={onConfirmRun}>直接采集</Button>
        ) : null}
        {(isPreviewDone || persistedPreviewReady || isCollected) ? <Button onClick={onHistory}>查看运行历史</Button> : null}
        {sourceReturn ? <Button onClick={sourceReturn}>返回原来源表</Button> : null}
        <Button type="link" onClick={onOpenJob}>任务详情</Button>
      </div>
    </section>
  );
}
