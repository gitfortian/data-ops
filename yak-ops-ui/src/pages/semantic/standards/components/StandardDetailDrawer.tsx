import { Alert, Button, Descriptions, Drawer, Space, Spin, Typography } from 'antd';
import { history, useSearchParams } from '@umijs/max';
import { useEffect, useMemo, useState } from 'react';
import { ApprovalStatusTag } from '@/components/ApprovalStatusTag';
import type { ApprovalInstance } from '@/services/approval/types';
import { getAffectedMetrics } from '@/services/metric/api';
import type { AffectedMetricRecord } from '@/services/metric/types';
import { formatSemanticTime, SEMANTIC_STANDARD_KIND_LABELS, SEMANTIC_STATUS_LABELS } from '@/pages/semantic/constants';
import { STANDARD_KIND_FIELD_LABELS } from '@/pages/semantic/standards/constants';
import {
  getSemanticStandard,
  getSemanticStandardUsage,
} from '@/services/semantic/api';
import type { SemanticStandardRecord, SemanticStandardUsageSummary } from '@/services/semantic/types';
import {
  classifyStandardContextFailure,
  parseStandardId,
  type StandardContextState,
  withStandardContext,
} from '../stableStandardContext';

interface StandardDetailDrawerProps {
  open: boolean;
  standard: SemanticStandardRecord | null;
  usageSummary: SemanticStandardUsageSummary | null;
  /** 该标准的在途/最近发布审批单(缺口单03);无单据为 null。 */
  publishApproval?: ApprovalInstance | null;
  /** STANDARD_PUBLISH 流程开关;关闭时维持直发,不展示审批入口。 */
  publishFlowEnabled?: boolean;
  onSubmitPublish?: (standard: SemanticStandardRecord) => void;
  onClose: () => void;
}

const STANDARD_CONTEXT_COPY: Record<Extract<StandardContextState, 'EMPTY' | 'FORBIDDEN' | 'UNAVAILABLE'>, { title: string; description: string }> = {
  EMPTY: {
    title: '目标标准在当前项目空间不可见',
    description: '该稳定 ID 在当前 Project Space 中没有可读取对象。不会据此推断其他项目空间是否存在同 ID 对象。',
  },
  FORBIDDEN: {
    title: '无权读取目标标准',
    description: '当前身份没有读取该标准的权限。权限拒绝不会被展示成“没有标准”。',
  },
  UNAVAILABLE: {
    title: '目标标准暂不可读取',
    description: '标准服务或网络暂不可用，请重试。服务不可用不会被展示成“没有标准”。',
  },
};

/** 标准详情抽屉(ticket 32):全量字段 + 引用/绕过统计与反哺建议。 */
const StandardDetailDrawer = ({
  open,
  standard,
  usageSummary,
  publishApproval,
  publishFlowEnabled,
  onSubmitPublish,
  onClose,
}: StandardDetailDrawerProps) => {
  const [entryParams, setEntryParams] = useSearchParams();
  const deepLinkStandardId = parseStandardId(entryParams.get('standardId'));
  const [linkedStandard, setLinkedStandard] = useState<SemanticStandardRecord | null>(null);
  const [linkedUsageSummary, setLinkedUsageSummary] = useState<SemanticStandardUsageSummary | null>(null);
  const [contextState, setContextState] = useState<StandardContextState>('IDLE');
  const [affectedMetrics, setAffectedMetrics] = useState<AffectedMetricRecord[] | null>(null);
  const [affectedMetricsUnavailable, setAffectedMetricsUnavailable] = useState(false);

  // Any ordinary row-open becomes a stable, copyable deep link without changing Standard ownership.
  useEffect(() => {
    if (!open || standard?.id == null || deepLinkStandardId === Number(standard.id)) return;
    setEntryParams(withStandardContext(entryParams, Number(standard.id)), { replace: true });
  }, [deepLinkStandardId, entryParams, open, setEntryParams, standard?.id]);

  // Metric canonical detail backlinks arrive here as ?standardId=<stable Semantic id>.
  useEffect(() => {
    if (!deepLinkStandardId) {
      setLinkedStandard(null);
      setLinkedUsageSummary(null);
      setContextState('IDLE');
      return undefined;
    }

    // Parent already resolved the same row; do not issue a second truth read.
    if (open && standard?.id != null && Number(standard.id) === deepLinkStandardId) {
      setContextState('READY');
      return undefined;
    }

    let cancelled = false;
    setContextState('LOADING');
    setLinkedStandard(null);
    setLinkedUsageSummary(null);

    getSemanticStandard(deepLinkStandardId)
      .then((resolved) => {
        if (cancelled) return;
        setLinkedStandard(resolved);
        setContextState('READY');
        getSemanticStandardUsage(deepLinkStandardId)
          .then((summary) => {
            if (!cancelled) setLinkedUsageSummary(summary);
          })
          .catch(() => {
            // Usage is supplementary evidence; losing it must not hide the owning Standard detail.
            if (!cancelled) setLinkedUsageSummary(null);
          });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setContextState(classifyStandardContextFailure(error));
      });

    return () => {
      cancelled = true;
    };
  }, [deepLinkStandardId, open, standard?.id]);

  const effectiveStandard = open && standard ? standard : linkedStandard;
  const effectiveUsageSummary = open && standard ? usageSummary : linkedUsageSummary;
  const effectiveOpen = open || Boolean(deepLinkStandardId);

  // CALIBER / UNIT are registered Metric dependencies. Reuse the existing reverse-impact truth
  // instead of creating a second Standard↔Metric relation store.
  useEffect(() => {
    const id = effectiveStandard?.id == null ? null : Number(effectiveStandard.id);
    const dependencyType = effectiveStandard?.kind;
    if (!id || (dependencyType !== 'CALIBER' && dependencyType !== 'UNIT')) {
      setAffectedMetrics(null);
      setAffectedMetricsUnavailable(false);
      return undefined;
    }

    let cancelled = false;
    setAffectedMetrics(null);
    setAffectedMetricsUnavailable(false);
    getAffectedMetrics(dependencyType, id)
      .then((rows) => {
        if (!cancelled) setAffectedMetrics(rows ?? []);
      })
      .catch(() => {
        if (!cancelled) setAffectedMetricsUnavailable(true);
      });
    return () => {
      cancelled = true;
    };
  }, [effectiveStandard?.id, effectiveStandard?.kind]);

  const kindFieldItems = useMemo(() => {
    if (!effectiveStandard) {
      return [];
    }
    return STANDARD_KIND_FIELD_LABELS.filter((item) => {
      const value = effectiveStandard[item.key];
      return value !== undefined && value !== null && String(value).length > 0;
    }).map((item) => ({
      key: item.key as string,
      label: item.label,
      children: String(effectiveStandard[item.key]),
    }));
  }, [effectiveStandard]);

  const usageAdvice =
    effectiveUsageSummary && effectiveUsageSummary.applyCount + effectiveUsageSummary.bypassCount > 0
      ? effectiveUsageSummary.bypassCount >= effectiveUsageSummary.applyCount
        ? '（⚠ 绕过偏高，建议复核该标准：修改或废弃）'
        : '（引用健康）'
      : '';

  const closeDetail = () => {
    if (deepLinkStandardId) {
      setEntryParams(withStandardContext(entryParams, null), { replace: true });
    }
    setLinkedStandard(null);
    setLinkedUsageSummary(null);
    setContextState('IDLE');
    onClose();
  };

  const retryDeepLink = () => {
    if (!deepLinkStandardId) return;
    // Toggle the stable query parameter to retrigger the exact same owning read without fabricating data.
    const cleared = withStandardContext(entryParams, null);
    setEntryParams(cleared, { replace: true });
    queueMicrotask(() => setEntryParams(withStandardContext(cleared, deepLinkStandardId), { replace: true }));
  };

  const contextFailure =
    contextState === 'EMPTY' || contextState === 'FORBIDDEN' || contextState === 'UNAVAILABLE'
      ? STANDARD_CONTEXT_COPY[contextState]
      : null;

  return (
    <Drawer
      open={effectiveOpen}
      width={520}
      title={effectiveStandard ? `${effectiveStandard.name}（${effectiveStandard.code}）` : '标准详情'}
      onClose={closeDetail}
      destroyOnClose
    >
      {contextState === 'LOADING' && !effectiveStandard ? <Spin tip="正在读取目标标准" /> : null}
      {contextFailure ? (
        <Alert
          type={contextState === 'FORBIDDEN' ? 'warning' : contextState === 'EMPTY' ? 'info' : 'error'}
          showIcon
          message={contextFailure.title}
          description={contextFailure.description}
          action={
            <Space direction="vertical" size={4}>
              {contextState === 'UNAVAILABLE' ? (
                <Button size="small" onClick={retryDeepLink}>
                  重试
                </Button>
              ) : null}
              <Button size="small" onClick={closeDetail}>
                退出定位
              </Button>
            </Space>
          }
        />
      ) : null}
      {effectiveStandard ? (
        <Descriptions
          bordered
          size="small"
          column={1}
          items={[
            {
              key: 'kind',
              label: '类别',
              children: SEMANTIC_STANDARD_KIND_LABELS[effectiveStandard.kind],
            },
            {
              key: 'status',
              label: '状态',
              children: SEMANTIC_STATUS_LABELS[effectiveStandard.status],
            },
            { key: 'stableId', label: '稳定 ID', children: String(effectiveStandard.id) },
            { key: 'preset', label: '预置', children: effectiveStandard.preset ? '是' : '否' },
            { key: 'version', label: '版本', children: String(effectiveStandard.version) },
            { key: 'desc', label: '描述', children: effectiveStandard.description || '-' },
            { key: 'creator', label: '创建人', children: effectiveStandard.createdBy || '-' },
            {
              key: 'createTime',
              label: '创建时间',
              children: formatSemanticTime(effectiveStandard.createTime),
            },
            {
              key: 'updateTime',
              label: '更新时间',
              children: formatSemanticTime(effectiveStandard.updateTime),
            },
            ...kindFieldItems,
            ...(effectiveUsageSummary
              ? [
                  {
                    key: 'usage',
                    label: '引用/绕过',
                    children: `${effectiveUsageSummary.applyCount} / ${effectiveUsageSummary.bypassCount}${usageAdvice}`,
                  },
                ]
              : []),
            ...(effectiveStandard.kind === 'CALIBER' || effectiveStandard.kind === 'UNIT'
              ? [
                  {
                    key: 'affectedMetrics',
                    label: '关联指标',
                    children: affectedMetricsUnavailable ? (
                      <span className="text-[#d46b08]">反向依赖暂不可读，不按 0 个引用处理</span>
                    ) : affectedMetrics == null ? (
                      <span className="text-[#98a2b3]">读取中…</span>
                    ) : affectedMetrics.length === 0 ? (
                      '当前项目空间暂无指标登记该依赖'
                    ) : (
                      <Space wrap size={4}>
                        {affectedMetrics.map((metric) => (
                          <Typography.Link
                            key={metric.metricId}
                            onClick={() => history.push(`/metric/manage/${metric.metricId}`)}
                          >
                            {metric.metricName}（{metric.metricCode}）
                          </Typography.Link>
                        ))}
                      </Space>
                    ),
                  },
                ]
              : []),
            ...(publishApproval
              ? [
                  {
                    key: 'publishApproval',
                    label: '发布审批',
                    children: (
                      <Space size={6}>
                        <ApprovalStatusTag status={publishApproval.status} />
                        <Button
                          type="link"
                          size="small"
                          className="!px-0"
                          onClick={() => history.push(`/approval/instance/${publishApproval.id}`)}
                        >
                          查看审批单
                        </Button>
                      </Space>
                    ),
                  },
                ]
              : []),
            ...(publishFlowEnabled &&
            effectiveStandard.kind !== 'CODE' &&
            effectiveStandard.status === 'DISABLED' &&
            onSubmitPublish &&
            publishApproval?.status !== 'PENDING'
              ? [
                  {
                    key: 'publishAction',
                    label: '发布',
                    children: (
                      <Space size={8}>
                        <Button type="primary" size="small" onClick={() => onSubmitPublish(effectiveStandard)}>
                          提交发布
                        </Button>
                        <span className="text-[12px] text-[#98a2b3]">启用需经审批，批准后自动生效</span>
                      </Space>
                    ),
                  },
                ]
              : []),
          ]}
        />
      ) : null}
    </Drawer>
  );
};

export default StandardDetailDrawer;
