import { useAccess } from '@umijs/max';
import { message, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { YakButton } from '@/components/ui';
import {
  getLatestMetricValidation,
  getMetricPublication,
  getMetricPublicationHistory,
  getMetricPublicationReadiness,
  publishMetricVersion,
  validateMetricVersion,
  withdrawMetricPublication,
} from '@/services/metric/api';
import type {
  MetricPublicationReadiness,
  MetricValidationEvidence,
  PublishedMetricContract,
} from '@/services/metric/types';

interface MetricGovernancePanelProps {
  metricId: number;
  currentVersion: number;
  onCompareVersions?: (publishedVersion: number, currentVersion: number) => void;
}

type ReadState = 'LOADING' | 'READY' | 'EMPTY' | 'UNAVAILABLE' | 'FORBIDDEN';

function failedReadState(error: unknown): ReadState {
  const response = error as { status?: number; response?: { status?: number } } | null;
  const status = response?.status ?? response?.response?.status;
  return status === 401 || status === 403 ? 'FORBIDDEN' : 'UNAVAILABLE';
}

export default function MetricGovernancePanel({ metricId, currentVersion, onCompareVersions }: MetricGovernancePanelProps) {
  const access = useAccess();
  const canValidate = access.hasPermission('metric:update');
  const canPublish = access.hasPermission('metric:publish');
  const [active, setActive] = useState<PublishedMetricContract | null>(null);
  const [validation, setValidation] = useState<MetricValidationEvidence | null>(null);
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
      getLatestMetricValidation(metricId, currentVersion),
      getMetricPublicationReadiness(metricId, currentVersion),
      getMetricPublicationHistory(metricId),
    ]);
    setActive(activeResult.status === 'fulfilled' ? activeResult.value : null);
    setActiveState(activeResult.status === 'fulfilled'
      ? activeResult.value ? 'READY' : 'EMPTY'
      : failedReadState(activeResult.reason));
    setValidation(validationResult.status === 'fulfilled' ? validationResult.value : null);
    setValidationState(validationResult.status === 'fulfilled'
      ? validationResult.value ? 'READY' : 'EMPTY'
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
      message.success(`v${currentVersion} 校验完成：${result.result}`);
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

  return (
    <section className="mt-4 rounded-lg border border-[#e5e7eb] bg-[#fcfcfd] p-4" aria-label="指标验证与发布">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="m-0 text-[15px] font-semibold text-[#344054]">验证与发布</h2>
        {active ? <Tag color="green">Published v{active.metricVersion}</Tag>
          : activeState === 'EMPTY' ? <Tag>未发布</Tag>
            : <Tag color="orange">{activeState === 'FORBIDDEN' ? '无权读取发布状态' : '发布状态暂不可用'}</Tag>}
        {validation ? (
          <Tag color={validation.result === 'PASSED' ? 'green' : validation.result === 'FAILED' ? 'red' : 'default'}>
            v{validation.metricVersion} · {validation.result} · {validation.providerState}
          </Tag>
        ) : validationState === 'EMPTY' ? <Tag>当前版本尚无验证证据</Tag>
          : <Tag color="orange">{validationState === 'FORBIDDEN' ? '无权读取校验证据' : '校验证据暂不可用'}</Tag>}
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
              {gate.provider}: {gate.status}{gate.issues?.length ? ` (${gate.issues.join(', ')})` : ''}
            </Tag>
          ))}
        </div>
      ) : null}
      {validation?.issues?.length ? (
        <ul className="mb-0 mt-3 list-disc pl-5 text-[12px] text-[#667085]">
          {validation.issues.map((issue, index) => <li key={`${issue.code}-${index}`}>{issue.message}</li>)}
        </ul>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2">
        <YakButton loading={loading} onClick={() => void load()}>刷新证据</YakButton>
        {drifted && active && onCompareVersions ? (
          <YakButton onClick={() => onCompareVersions(active.metricVersion, currentVersion)}>
            对比草稿与已发布版本
          </YakButton>
        ) : null}
        {canValidate ? <YakButton loading={acting} onClick={() => void validate()}>验证当前版本 v{currentVersion}</YakButton> : null}
        {canPublish && !loading && readinessState === 'READY' && readiness?.status === 'READY' && !isCurrentPublished ? (
          <YakButton type="primary" loading={acting} onClick={() => void publish()}>发布 v{currentVersion}</YakButton>
        ) : null}
        {canPublish && active ? <YakButton danger loading={acting} onClick={() => void withdraw()}>撤回发布</YakButton> : null}
        {!canPublish ? <span className="self-center text-[12px] text-[#667085]">当前账号没有指标发布权限</span> : null}
      </div>
    </section>
  );
}
