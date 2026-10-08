import { useAccess } from '@umijs/max';
import { message, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import MetricConsumptionHandoff from './MetricConsumptionHandoff';
import { YakButton } from '@/components/ui';
import {
  getMetricPublication,
  getMetricPublicationHistory,
  getMetricPublicationReadiness,
  getMetricValidationHistory,
  publishMetricVersion,
  validateMetricVersion,
  withdrawMetricPublication,
} from '@/services/metric/api';
import type {
  MetricProviderState,
  MetricPublicationReadiness,
  MetricValidationEvidence,
  MetricValidationResult,
  PublishedMetricContract,
} from '@/services/metric/types';

interface MetricGovernancePanelProps {
  metricId: number;
  currentVersion: number;
  onReviewVersion?: (version: number) => void;
  onCompareVersions?: (publishedVersion: number, currentVersion: number) => void;
}

type ReadState = 'LOADING' | 'READY' | 'EMPTY' | 'UNAVAILABLE' | 'FORBIDDEN';

function failedReadState(error: unknown): ReadState {
  const response = error as { status?: number; response?: { status?: number } } | null;
  const status = response?.status ?? response?.response?.status;
  return status === 401 || status === 403 ? 'FORBIDDEN' : 'UNAVAILABLE';
}

const GATE_PROVIDER_LABELS: Record<string, string> = {
  'metric-definition-validation-gate/v1': '定义校验门禁',
  'metric-dependency-health-gate/v1': '依赖健康门禁',
  'metric-execution-validation/v1': '执行校验门禁',
};

/** 门禁自带的英文说明按契约原样返回,这里给出等价中文。 */
const GATE_REASON_LABELS: Record<string, string> = {
  'No standalone Metric execution runtime is defined; governed execution remains owned by Dataset/Data Service consumption targets':
    '指标不单独定义执行运行时；受治理的执行仍由数据集/数据服务消费目标承担',
};

const GATE_STATUS_LABELS: Record<string, string> = {
  READY: '通过',
  BLOCKED: '未通过',
  UNAVAILABLE: '不可用',
  FORBIDDEN: '无权限',
  NOT_APPLICABLE: '不适用',
};

const VALIDATION_RESULT_LABELS: Record<MetricValidationResult, string> = {
  PASSED: '通过',
  FAILED: '未通过',
  NOT_APPLICABLE: '不适用',
};

const PROVIDER_STATE_LABELS: Record<MetricProviderState, string> = {
  READY: '校验源就绪',
  UNAVAILABLE: '校验源不可用',
  FORBIDDEN: '无权限',
};

const DEPENDENCY_STATE_LABELS: Record<string, string> = {
  UNAVAILABLE: '不可用',
  OUTDATED: '已过期',
  REMOVED: '已移除',
};

/**
 * 门禁 issue 是后端契约码,原样直出业务用户读不懂;未知码仍原样返回,不吞信息。
 */
function formatGateIssue(issue: string): string {
  if (issue === 'DEFINITION_VALIDATION_PASSED_EVIDENCE_REQUIRED') {
    return '缺少「通过」的定义校验证据';
  }
  if (issue === 'DEFINITION_VALIDATION_EVIDENCE_SUBJECT_MISMATCH') {
    return '已有校验证据与当前版本不一致';
  }
  if (issue.startsWith('STALE_METRIC_VERSION')) {
    return `请求的版本不是当前可编辑版本（${issue.slice('STALE_METRIC_VERSION:'.length).trim()}）`;
  }
  // 依赖门禁 issue 形如 <TYPE>:<ID>:<UNAVAILABLE|OUTDATED|REMOVED>
  const segments = issue.split(':');
  const state = segments[segments.length - 1];
  if (DEPENDENCY_STATE_LABELS[state]) {
    return `依赖 ${segments.slice(0, -1).join(':')} ${DEPENDENCY_STATE_LABELS[state]}`;
  }
  return GATE_REASON_LABELS[issue] ?? issue;
}

export default function MetricGovernancePanel({ metricId, currentVersion, onCompareVersions, onReviewVersion }: MetricGovernancePanelProps) {
  const access = useAccess();
  const canValidate = access.hasPermission('metric:update');
  const canPublish = access.hasPermission('metric:publish');
  const [active, setActive] = useState<PublishedMetricContract | null>(null);
  const [validationHistory, setValidationHistory] = useState<MetricValidationEvidence[]>([]);
  const [readiness, setReadiness] = useState<MetricPublicationReadiness | null>(null);
  const [historyCount, setHistoryCount] = useState(0);
  const [activeState, setActiveState] = useState<ReadState>('LOADING');
  const [validationState, setValidationState] = useState<ReadState>('LOADING');
  const [readinessState, setReadinessState] = useState<ReadState>('LOADING');
  const [historyState, setHistoryState] = useState<ReadState>('LOADING');
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setActiveState('LOADING');
    setValidationState('LOADING');
    setReadinessState('LOADING');
    setHistoryState('LOADING');
    const [activeResult, validationResult, readinessResult, historyResult] = await Promise.allSettled([
      getMetricPublication(metricId),
      // 取完整证据历史而不是 latest-ready:后者只返回 PASSED,会让失败的那次凭空消失。
      getMetricValidationHistory(metricId, currentVersion),
      getMetricPublicationReadiness(metricId, currentVersion),
      getMetricPublicationHistory(metricId),
    ]);
    setActive(activeResult.status === 'fulfilled' ? activeResult.value : null);
    setActiveState(activeResult.status === 'fulfilled'
      ? activeResult.value ? 'READY' : 'EMPTY'
      : failedReadState(activeResult.reason));
    setValidationHistory(validationResult.status === 'fulfilled' ? validationResult.value : []);
    setValidationState(validationResult.status === 'fulfilled'
      ? validationResult.value.length ? 'READY' : 'EMPTY'
      : failedReadState(validationResult.reason));
    setReadiness(readinessResult.status === 'fulfilled' ? readinessResult.value : null);
    setReadinessState(readinessResult.status === 'fulfilled'
      ? readinessResult.value ? 'READY' : 'EMPTY'
      : failedReadState(readinessResult.reason));
    setHistoryCount(historyResult.status === 'fulfilled' ? historyResult.value.length : 0);
    setHistoryState(historyResult.status === 'fulfilled' ? 'READY' : failedReadState(historyResult.reason));
    setLoading(false);
  }, [currentVersion, metricId]);

  useEffect(() => {
    void load();
  }, [load]);

  const validate = async () => {
    setActing(true);
    try {
      const result = await validateMetricVersion(metricId, currentVersion);
      // 校验「跑完了」和「跑过了」是两件事:失败结果不能挂成功样式。
      const summary = `v${currentVersion} 校验完成：${VALIDATION_RESULT_LABELS[result.result] ?? result.result}`;
      if (result.result === 'FAILED') {
        message.warning(summary);
      } else {
        message.success(summary);
      }
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '指标定义校验失败');
    } finally {
      setActing(false);
    }
  };

  const publish = async () => {
    setActing(true);
    try {
      await publishMetricVersion(metricId, currentVersion);
      message.success(`已发布 Metric v${currentVersion}`);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布指标失败');
    } finally {
      setActing(false);
    }
  };

  const withdraw = async () => {
    setActing(true);
    try {
      await withdrawMetricPublication(metricId);
      message.success('已撤回当前发布合同');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '撤回指标发布失败');
    } finally {
      setActing(false);
    }
  };

  const isCurrentPublished = active?.metricVersion === currentVersion;
  const drifted = Boolean(active && !isCurrentPublished);

  // 历史按 checkedAt 倒序返回:第一条是最近一次尝试,不论成败。
  const latestAttempt = validationHistory[0] ?? null;
  const latestReady = validationHistory.find(
    (item) => item.result === 'PASSED' && item.providerState === 'READY',
  ) ?? null;
  // 最近一次没过、但历史上有通过证据:门禁据此放行,两者都要说清楚,否则用户以为门禁在乱拦。
  const attemptFailedWithStaleReady =
    latestAttempt?.result === 'FAILED' && Boolean(latestReady);

  return (
    <section className="mt-4 rounded-lg border border-[#e5e7eb] bg-[#fcfcfd] p-4" aria-label="指标验证与发布">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="m-0 text-[15px] font-semibold text-[#344054]">验证与发布</h2>
        {active ? <Tag color="green">Published v{active.metricVersion}</Tag>
          : activeState === 'EMPTY' ? <Tag>未发布</Tag>
            : <Tag color="orange">{activeState === 'FORBIDDEN' ? '无权读取发布状态' : '发布状态暂不可用'}</Tag>}
        {latestAttempt ? (
          <Tag color={latestAttempt.result === 'PASSED' ? 'green' : latestAttempt.result === 'FAILED' ? 'red' : 'default'}>
            最近一次校验 v{latestAttempt.metricVersion} ·{' '}
            {VALIDATION_RESULT_LABELS[latestAttempt.result] ?? latestAttempt.result} ·{' '}
            {PROVIDER_STATE_LABELS[latestAttempt.providerState] ?? latestAttempt.providerState}
          </Tag>
        ) : validationState === 'EMPTY' ? <Tag>当前版本尚无验证证据</Tag>
          : <Tag color="orange">{validationState === 'FORBIDDEN' ? '无权读取校验证据' : '校验证据暂不可用'}</Tag>}
        {attemptFailedWithStaleReady ? (
          <Tag color="orange">
            仍持有 v{latestReady?.metricVersion} 的通过证据（{latestReady?.checkedAt ? latestReady.checkedAt.slice(0, 19).replace('T', ' ') : '时间未知'}）
          </Tag>
        ) : null}
        {drifted ? <Tag color="orange">Draft v{currentVersion} 与 Published v{active?.metricVersion} 不同</Tag> : null}
        <span className="ml-auto text-[12px] text-[#667085]">
          {historyState === 'READY' ? `发布历史 ${historyCount} 条` : '发布历史暂不可读'}
        </span>
      </div>

      <div className="mt-2 text-[13px] text-[#667085]">
        {active
          ? `当前稳定合同绑定 MetricVersion v${active.metricVersion}，摘要 ${active.snapshotDigest.slice(0, 12)}…`
          : activeState === 'EMPTY'
            ? '保存或启用指标不会自动发布；下游只能引用显式发布的精确版本。'
            : '当前无法确认 active publication；请恢复读取后再判断发布状态。'}
      </div>
      {readinessState === 'UNAVAILABLE' || readinessState === 'FORBIDDEN' ? (
        <div className="mt-2 text-[12px] text-[#b54708]">
          {readinessState === 'FORBIDDEN' ? '当前账号无权读取发布 readiness。' : '发布 readiness 暂不可用，不能据此判断指标未就绪。'}
        </div>
      ) : null}
      {readiness?.gates?.length ? (
        <div className="mt-3 flex flex-wrap gap-2" aria-label="发布门禁结果">
          {readiness.gates.map((gate) => (
            <Tag key={gate.provider} color={gate.status === 'READY' ? 'green' : gate.status === 'BLOCKED' ? 'red' : 'default'}>
              {GATE_PROVIDER_LABELS[gate.provider] ?? gate.provider}：{GATE_STATUS_LABELS[gate.status] ?? gate.status}
              {gate.issues?.length ? `（${gate.issues.map(formatGateIssue).join('；')}）` : ''}
            </Tag>
          ))}
        </div>
      ) : null}
      {latestAttempt?.issues?.length ? (
        // 失败原因必须就地可见:门禁会因此 BLOCKED,看不到原因用户就无法自助修复。
        <ul className="mb-0 mt-3 list-disc pl-5 text-[12px] text-[#b42318]">
          {latestAttempt.issues.map((issue, index) => (
            <li key={`${issue.code}-${index}`}>
              {issue.message}
              <span className="ml-1 text-[#667085]">（{issue.code} · {issue.field}）</span>
            </li>
          ))}
        </ul>
      ) : latestAttempt?.result === 'PASSED' ? (
        <div className="mt-3 text-[12px] text-[#027a48]">最近一次校验未发现问题。</div>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2">
        <YakButton loading={loading} onClick={() => void load()}>刷新证据</YakButton>
        {drifted && active && onCompareVersions ? (
          <YakButton onClick={() => onCompareVersions(active.metricVersion, currentVersion)}>
            对比草稿与已发布版本
          </YakButton>
        ) : null}
        {active && onReviewVersion && <YakButton onClick={() => onReviewVersion(active.metricVersion)}>解释已发布版本 v{active.metricVersion}</YakButton>}
        {canValidate ? <YakButton loading={acting} onClick={() => void validate()}>验证当前版本 v{currentVersion}</YakButton> : null}
        {canPublish && !loading && readinessState === 'READY' && readiness?.status === 'READY' && !isCurrentPublished ? (
          <YakButton type="primary" loading={acting} onClick={() => void publish()}>发布 v{currentVersion}</YakButton>
        ) : null}
        {canPublish && active ? <YakButton danger loading={acting} onClick={() => void withdraw()}>撤回发布</YakButton> : null}
        {!canPublish ? <span className="self-center text-[12px] text-[#667085]">当前账号没有指标发布权限</span> : null}
      </div>
      <MetricConsumptionHandoff metricId={metricId} publication={activeState === 'READY' ? active : null} />
    </section>
  );
}
