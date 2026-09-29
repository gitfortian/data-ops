import { Alert, Descriptions, Empty, message, Select, Space, Spin, Tag, Tooltip } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { YakButton } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { bindModelPolicy, getModelLifecycle, pagePolicies, unbindModelPolicy } from '@/services/data-lifecycle/api';
import type { ModelTtlResolution, PolicyRecord } from '@/services/data-lifecycle/types';
import TtlDispatchWizard from './TtlDispatchWizard';
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
  const canUpdate = can('data-lifecycle:update');
  const [loading, setLoading] = useState(false);
  const [resolution, setResolution] = useState<ModelTtlResolution | null>(null);
  const [policies, setPolicies] = useState<PolicyRecord[]>([]);
  const [policyId, setPolicyId] = useState<number | undefined>(undefined);
  const [binding, setBinding] = useState(false);
  const [wizardOpen, setWizardOpen] = useState(false);

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
  }, [modelId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    pagePolicies({ pageNo: 1, pageSize: 200 })
      .then((result) => setPolicies((result.records ?? []).filter((p) => p.status === 'ENABLED')))
      .catch(() => setPolicies([]));
  }, []);

  const policyOptions = useMemo(
    () =>
      policies.map((p) => ({
        value: p.id,
        label: `${p.policyName}（${formatRetention(p.hotDays, p.coldDays, p.destroyDays)}）`,
      })),
    [policies],
  );

  if (modelId == null) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未选择模型" />;

  const statement = resolution?.statement;
  const policy = resolution?.policy;

  return (
    <Spin spinning={loading}>
      {resolution == null ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无法读取该模型的生命周期信息" />
      ) : (
        <div className="pt-2">
          {!resolution.hasTimePartition && (
            <Alert
              className="mb-3"
              type="warning"
              showIcon
              message="模型未配置时间分区，TTL 下发不会生效"
              description="请先在字段配置中将时间字段设为分区字段。"
            />
          )}

          <Descriptions column={2} size="small" bordered={false} labelStyle={{ width: 110, color: '#667085' }}>
            <Descriptions.Item label="TTL 状态">
              <Space size={6}>
                <Tag color={MODEL_STATE_COLORS[resolution.state]}>{MODEL_STATE_LABELS[resolution.state]}</Tag>
                {resolution.virtualPolicy && <Tag>未绑定（分层旧配置合成）</Tag>}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="策略来源">{BINDING_SOURCE_LABELS[resolution.bindingSource]}</Descriptions.Item>
            <Descriptions.Item label="生效策略">{policy?.policyName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="保留周期">
              {policy ? formatRetention(policy.hotDays, policy.coldDays, policy.destroyDays) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="分区粒度">
              {policy ? (GRANULARITY_LABELS[policy.partitionGranularity] ?? policy.partitionGranularity) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="存储类型">
              {statement ? (STORAGE_TYPE_LABELS[statement.storageType] ?? statement.storageType) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="目标表" span={2}>
              {statement?.qualifiedTable ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="最近下发" span={2}>
              {resolution.lastDispatch ? (
                <Space size={6}>
                  <Tag color={DISPATCH_STATUS_COLORS[resolution.lastDispatch.status]}>
                    {DISPATCH_STATUS_LABELS[resolution.lastDispatch.status]}
                  </Tag>
                  <span className="text-[12px] text-[#667085]">
                    {formatLifecycleTime(resolution.lastDispatch.finishTime ?? resolution.lastDispatch.createTime)}
                  </span>
                  {resolution.lastDispatch.errorMessage && (
                    <Tooltip title={resolution.lastDispatch.errorMessage}>
                      <span className="text-[12px] text-[#d92d20]">查看错误</span>
                    </Tooltip>
                  )}
                </Space>
              ) : (
                '暂无下发记录'
              )}
            </Descriptions.Item>
          </Descriptions>

          {statement?.statement && (
            <div className="mt-4 rounded-lg bg-[#0b1021] p-3">
              <div className="mb-1 flex items-center justify-between">
                <span className="text-[12px] text-[#98a2b3]">
                  TTL 语句
                  {!statement.writable && (
                    <Tooltip title={statement.note ?? '该存储方言暂不支持平台下发，仅可复制手工执行'}>
                      <Tag color="orange" className="!ml-2">
                        仅可复制
                      </Tag>
                    </Tooltip>
                  )}
                </span>
                <YakButton size="small" className="!h-6 !px-2 !text-[12px]" onClick={() => void copyText(statement.statement)}>
                  复制
                </YakButton>
              </div>
              <pre className="m-0 overflow-auto whitespace-pre-wrap text-[12px] leading-5 text-[#d0d6e4]">
                {statement.statement}
              </pre>
            </div>
          )}

          <div className="mt-4 flex flex-wrap items-center gap-3">
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择要绑定的策略（覆盖分层默认）"
              className="!w-[360px]"
              value={policyId}
              onChange={setPolicyId}
              options={policyOptions}
              disabled={!canUpdate}
            />
            {canUpdate && (
              <YakButton
                loading={binding}
                disabled={policyId == null}
                className="!rounded-lg"
                onClick={async () => {
                  if (policyId == null) return;
                  setBinding(true);
                  try {
                    await bindModelPolicy(modelId, policyId);
                    message.success('已绑定策略');
                    await load();
                  } catch {
                    // 全局错误提示已展示原因
                  } finally {
                    setBinding(false);
                  }
                }}
              >
                {resolution.bindingSource === 'OVERRIDE' ? '更换策略' : '绑定策略'}
              </YakButton>
            )}
            {canUpdate && resolution.bindingSource === 'OVERRIDE' && (
              <YakButton
                className="!rounded-lg"
                onClick={async () => {
                  try {
                    await unbindModelPolicy(modelId);
                    message.success('已解除覆盖，回退分层默认策略');
                    await load();
                  } catch {
                    // 全局错误提示已展示原因
                  }
                }}
              >
                回退分层默认
              </YakButton>
            )}
            <div className="flex-1" />
            {canUpdate && (
              <YakButton
                type="primary"
                className="!rounded-lg !text-white"
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
          </div>

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
