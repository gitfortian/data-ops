import { Empty, message, Select, Spin, Tag, Tooltip } from 'antd';
import { AlertTriangle, ChevronDown, Clock3, Copy, FileCode2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { YakButton } from '@/components/ui';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { useLatestOperation } from '@/hooks/useLatestOperation';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { bindModelPolicy, getModelLifecycle, pagePolicies, unbindModelPolicy } from '@/services/data-lifecycle/api';
import type { ModelTtlResolution, PolicyRecord } from '@/services/data-lifecycle/types';
import TtlDispatchWizard from './TtlDispatchWizard';
import './ModelLifecycleTab.less';
import {
  BINDING_SOURCE_LABELS,
  DISPATCH_STATUS_COLORS,
  DISPATCH_STATUS_LABELS,
  formatLifecycleTime,
  formatRetention,
  GRANULARITY_LABELS,
  MODEL_STATE_COLORS,
  MODEL_STATE_LABELS,
  STORAGE_TYPE_LABELS,
} from '../constants';

interface ModelLifecycleTabProps {
  modelId: number | null | undefined;
}

const copyText = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text);
    message.success('语句已复制');
  } catch {
    message.error('复制失败，请手动选择复制');
  }
};

const ModelLifecycleTab = ({ modelId }: ModelLifecycleTabProps) => {
  const { can } = usePermissionAccess();
  const { currentProject } = useSecurityProject();
  const beginPolicyLoad = useLatestOperation(JSON.stringify([currentProject?.id, modelId]));
  const canUpdate = can('data-lifecycle:update');
  const [loading, setLoading] = useState(false);
  const [resolution, setResolution] = useState<ModelTtlResolution | null>(null);
  const [policies, setPolicies] = useState<PolicyRecord[]>([]);
  const [policiesLoading, setPoliciesLoading] = useState(false);
  const [policyId, setPolicyId] = useState<number | undefined>(undefined);
  const [binding, setBinding] = useState(false);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [policyEditing, setPolicyEditing] = useState(false);
  const [sqlExpanded, setSqlExpanded] = useState(false);

  const load = useCallback(async () => {
    if (modelId == null) return;
    setLoading(true);
    try {
      const result = await getModelLifecycle(modelId);
      setResolution(result);
      setPolicyId(result.policy?.id);
    } catch {
      setResolution(null);
    } finally {
      setLoading(false);
    }
  }, [modelId, currentProject?.id]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadPolicies = useCallback(async () => {
    const isCurrent = beginPolicyLoad();
    setPoliciesLoading(true);
    try {
      const result = await pagePolicies({ pageNo: 1, pageSize: 200 });
      if (!isCurrent()) return;
      setPolicies((result.records ?? []).filter((policy) =>
        policy.status === 'ENABLED' && policy.publishState === 'PUBLISHED'));
    } catch {
      if (isCurrent()) setPolicies([]);
    } finally {
      if (isCurrent()) setPoliciesLoading(false);
    }
  }, [currentProject?.id, modelId, beginPolicyLoad]);

  useEffect(() => {
    setPolicies([]);
    setPolicyId(undefined);
    setPolicyEditing(false);
    setSqlExpanded(false);
    void loadPolicies();
  }, [loadPolicies]);

  const policyOptions = useMemo(
    () =>
      policies.map((p) => ({
        value: p.id,
        label: `${p.policyName}（${formatRetention(p.hotDays, p.coldDays, p.destroyDays)}）`,
      })),
    [policies],
  );
  const selectedPolicy = policies.find((policy) => policy.id === policyId);

  if (modelId == null) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未选择模型" />;

  const statement = resolution?.statement;
  const policy = resolution?.policy;
  const retention = (days?: number | null, permanent = false) => days == null
    ? (permanent ? '永久保留' : '不限') : <>{days}<span>天</span></>;
  const sqlRegionId = `model-ttl-statement-${modelId}`;

  return (
    <Spin spinning={loading}>
      {resolution == null ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无法读取该模型的生命周期信息" />
      ) : (
        <div className="model-lifecycle">
          <header className="model-lifecycle-header">
            <div className="model-lifecycle-heading">
              <div className="model-lifecycle-title">
                <h2>生命周期</h2>
                <Tag color={MODEL_STATE_COLORS[resolution.state]}>{MODEL_STATE_LABELS[resolution.state]}</Tag>
              </div>
              <div className="model-lifecycle-target"><span>目标表</span><code>{statement?.qualifiedTable || resolution.model.tableName || '—'}</code></div>
            </div>
            {canUpdate && (
              <YakButton
                type="primary"
                disabled={!resolution.hasTimePartition}
                title={
                  resolution.hasTimePartition
                    ? resolution.previewable
                      ? undefined
                      : '当前存储方言暂不支持平台下发,可按保留期估算分区分布并复制语句手工执行'
                    : (resolution.notPreviewableReason ?? '当前不可预览')
                }
                onClick={() => setWizardOpen(true)}
              >
                预览并下发
              </YakButton>
            )}
          </header>

          {!resolution.hasTimePartition && (
            <div className="model-lifecycle-notice" role="status">
              <AlertTriangle size={15} aria-hidden />
              <span>尚未配置时间分区，请在模型设计中配置后再下发。</span>
            </div>
          )}

          <div className="model-lifecycle-grid">
            <section className="model-lifecycle-card model-lifecycle-policy" aria-label="生效策略">
              <div className="model-lifecycle-card-header">
                <h3>生效策略</h3>
                {canUpdate && policy && !policyEditing ? (
                  <YakButton size="small" onClick={() => setPolicyEditing(true)}>更换策略</YakButton>
                ) : null}
              </div>
              <div className="model-lifecycle-policy-name">
                {policy?.policyName || '未绑定策略'}
                {resolution.virtualPolicy ? <Tag>分层保留期</Tag> : null}
              </div>
              <dl className="model-lifecycle-facts">
                <div><dt>策略来源</dt><dd>{resolution.virtualPolicy ? '分层配置' : BINDING_SOURCE_LABELS[resolution.bindingSource]}</dd></div>
                <div><dt>分区粒度</dt><dd>{policy ? GRANULARITY_LABELS[policy.partitionGranularity] || policy.partitionGranularity : '—'}</dd></div>
                <div><dt>存储类型</dt><dd>{statement ? STORAGE_TYPE_LABELS[statement.storageType] || statement.storageType : '—'}</dd></div>
              </dl>

              {policy ? (
                <div className="model-lifecycle-retention" aria-label="保留周期">
                  <div><span className="model-lifecycle-retention-label"><i className="is-hot" />热数据保留</span><strong>{retention(policy.hotDays)}</strong></div>
                  <div><span className="model-lifecycle-retention-label"><i className="is-cold" />冷数据保留</span><strong>{retention(policy.coldDays)}</strong></div>
                  <div><span className="model-lifecycle-retention-label"><i className="is-destroy" />数据销毁</span><strong>{retention(policy.destroyDays, true)}</strong></div>
                </div>
              ) : null}

              {canUpdate && (!policy || policyEditing) ? (
                <div className="model-lifecycle-policy-editor">
                  <label htmlFor={`model-ttl-policy-${modelId}`}>选择策略</label>
                  <div className="model-lifecycle-policy-controls">
                    <Select id={`model-ttl-policy-${modelId}`}
                      showSearch optionFilterProp="label"
                      placeholder="选择已发布策略"
                      value={selectedPolicy?.id} onChange={setPolicyId}
                      options={policyOptions} loading={policiesLoading}
                      notFoundContent={policiesLoading ? '加载中…' : '暂无已发布且启用的策略'}
                      onOpenChange={(open) => { if (open) void loadPolicies(); }}
                      disabled={binding}
                    />
                    <YakButton type="primary" loading={binding} disabled={!selectedPolicy || policiesLoading}
                      onClick={async () => {
                        if (!selectedPolicy || policiesLoading) return;
                        setBinding(true);
                        try {
                          await bindModelPolicy(modelId, selectedPolicy.id);
                          message.success('已绑定策略');
                          setPolicyEditing(false);
                          await load();
                        } catch {
                          // 全局错误提示已展示原因
                        } finally {
                          setBinding(false);
                        }
                      }}>
                      {policy ? '确认更换' : '绑定策略'}
                    </YakButton>
                    {policy ? <YakButton disabled={binding} onClick={() => {
                      setPolicyId(policy.id);
                      setPolicyEditing(false);
                    }}>取消</YakButton> : null}
                  </div>
                </div>
              ) : null}
              {canUpdate && resolution.bindingSource === 'OVERRIDE' ? (
                <div className="model-lifecycle-policy-footer">
                  <YakButton type="link" size="small" disabled={binding} onClick={async () => {
                    setBinding(true);
                    try {
                      await unbindModelPolicy(modelId);
                      message.success('已解除覆盖，回退分层默认策略');
                      setPolicyEditing(false);
                      await load();
                    } catch {
                      // 全局错误提示已展示原因
                    } finally {
                      setBinding(false);
                    }
                  }}>恢复分层默认</YakButton>
                </div>
              ) : null}
            </section>

            <section className="model-lifecycle-card model-lifecycle-dispatch" aria-label="最近下发">
              <div className="model-lifecycle-card-header"><h3>最近下发</h3></div>
              {resolution.lastDispatch ? (
                <div className="model-lifecycle-dispatch-result">
                  <Tag color={DISPATCH_STATUS_COLORS[resolution.lastDispatch.status]}>
                    {DISPATCH_STATUS_LABELS[resolution.lastDispatch.status]}
                  </Tag>
                  <dl className="model-lifecycle-facts">
                    <div><dt>下发时间</dt><dd>{formatLifecycleTime(resolution.lastDispatch.finishTime ?? resolution.lastDispatch.createTime)}</dd></div>
                  </dl>
                  {resolution.lastDispatch.errorMessage ? (
                    <div className="model-lifecycle-dispatch-error"><AlertTriangle size={14} aria-hidden /><span>{resolution.lastDispatch.errorMessage}</span></div>
                  ) : null}
                </div>
              ) : (
                <div className="model-lifecycle-dispatch-empty"><Clock3 size={24} strokeWidth={1.5} aria-hidden /><span>暂无下发记录</span></div>
              )}
            </section>
          </div>

          {statement?.statement ? (
            <section className="model-lifecycle-sql">
              <div className="model-lifecycle-sql-header">
                <button type="button" aria-expanded={sqlExpanded} aria-controls={sqlRegionId}
                  className="model-lifecycle-sql-toggle" onClick={() => setSqlExpanded((expanded) => !expanded)}>
                  <FileCode2 size={16} aria-hidden />
                  <span>TTL 语句</span>
                  {!statement.writable ? <Tooltip title={statement.note || '该存储方言仅支持复制语句手工执行'}><span className="model-lifecycle-sql-hint">仅可复制</span></Tooltip> : null}
                  <ChevronDown size={15} className={sqlExpanded ? 'is-expanded' : ''} aria-hidden />
                </button>
                <YakButton size="small" icon={<Copy size={13} />} onClick={() => void copyText(statement.statement)}>复制语句</YakButton>
              </div>
              <div id={sqlRegionId} hidden={!sqlExpanded}>
                <pre>{statement.statement}</pre>
              </div>
            </section>
          ) : null}

          <TtlDispatchWizard
            open={wizardOpen}
            modelIds={[modelId]}
            onClose={() => setWizardOpen(false)}
            onDone={() => void load()}
          />
        </div>
      )}
    </Spin>
  );
};

export default ModelLifecycleTab;
