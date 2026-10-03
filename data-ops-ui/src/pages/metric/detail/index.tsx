import { useNavigate, useParams, useSearchParams } from '@umijs/max';
import { Descriptions, Drawer, message, Select, Spin, Table, Tabs, Tag, Timeline, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { getAssetLineageSummary } from '@/services/data-asset/api';
import type { AssetLineageSummary } from '@/services/data-asset/types';
import { getModelingModel } from '@/services/modeling/api';
import {
  assignMetricTags,
  getMetric,
  getMetricTags,
  getMetricUsageList,
  getMetricUsageSummary,
  getMetricVersion,
  listMetricTags,
  listMetricVersions,
  removeMetricTag,
} from '@/services/metric/api';
import type {
  AuthoringNextStep,
  DependencyChange,
  DependencyHealth,
  MetricRecord,
  MetricTagRecord,
  MetricUsageRecord,
  MetricVersionRecord,
  UsageSummary,
} from '@/services/metric/types';
import MetricGovernancePanel from './MetricGovernancePanel';
import MetricVersionComparisonDrawer from './MetricVersionComparisonDrawer';
import {
  formatMetricTime,
  METRIC_STATUS_COLORS,
  METRIC_STATUS_LABELS,
  METRIC_TYPE_COLORS,
  METRIC_TYPE_LABELS,
  STAT_PERIOD_LABELS,
} from '../constants';

const COMPOSITE_SYMBOLS: Record<string, string> = {
  ADD: '+',
  SUB: '-',
  MUL: '*',
  DIV: '/',
  LPAREN: '(',
  RPAREN: ')',
};

const readFailureState = (error: unknown): 'UNAVAILABLE' | 'FORBIDDEN' => {
  const response = error as { status?: number; response?: { status?: number } } | null;
  const status = response?.status ?? response?.response?.status;
  return status === 401 || status === 403 ? 'FORBIDDEN' : 'UNAVAILABLE';
};

/** 与 yak_metric_usage.usage_type 对齐。 */
const USAGE_TYPE_LABELS: Record<string, string> = {
  DATASET: '数据集',
  REPORT: '报表',
  DASHBOARD: '仪表盘',
  API: 'API',
  SCREEN: '大屏',
};

const DEPENDENCY_HEALTH_META: Record<DependencyHealth, { label: string; color: string; description: string }> = {
  UP_TO_DATE: { label: '最新', color: 'green', description: '登记版本与当前上游版本一致' },
  OUTDATED: { label: '已过期', color: 'orange', description: '上游已产生新版本，需要复核定义' },
  REMOVED: { label: '已删除', color: 'red', description: '登记的上游对象已不存在，需要重新绑定' },
  UNAVAILABLE: { label: '暂不可用', color: 'default', description: '上游 provider 当前不可读，不能视为已删除' },
};

const AUTHORING_NEXT_STEP_META: Record<
  AuthoringNextStep,
  { title: string; description: string; tone: string; action?: 'impact' | 'retry' }
> = {
  VALIDATE: {
    title: '下一步：验证当前版本',
    description: '当前依赖检查通过，可验证当前版本。保存或启用后仍需单独发布，才能形成稳定的引用版本。',
    tone: 'border-[#abefc6] bg-[#ecfdf3]',
  },
  REVIEW_OUTDATED_DEPENDENCY: {
    title: '先复核已过期依赖',
    description: '上游版本已经变化。检查影响并确认定义仍成立后，再验证当前版本。',
    tone: 'border-[#fedf89] bg-[#fffaeb]',
    action: 'impact',
  },
  RESOLVE_REMOVED_DEPENDENCY: {
    title: '先处理已删除依赖',
    description: '至少一个上游对象已被删除。重新绑定或调整定义后，再进行版本验证。',
    tone: 'border-[#fecdca] bg-[#fef3f2]',
    action: 'impact',
  },
  RETRY_DEPENDENCY_PROVIDER: {
    title: '依赖事实暂不可用',
    description: '无法确认部分上游当前状态。该状态不是“已删除”，恢复读取后应重新检查。',
    tone: 'border-[#eaecf0] bg-[#f9fafb]',
    action: 'retry',
  },
};

/** stat_dimensions 为 JSON 列：展示时数组统一转顿号文本，存量自由文本原样。 */
const formatStatDimensions = (raw?: string): string => {
  if (!raw) return '-';
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed.join('、');
  } catch {
    // 非 JSON 存量原样展示
  }
  return raw;
};

/** M-5:「计算规则」回答“这个指标怎么算”，按类型取真实定义而非 calRule。 */
const formatCalcRule = (m: MetricRecord): string => {
  if (m.metricType === 'ATOMIC') {
    if (!m.measureExpr) return '-';
    return m.filterExpr ? `${m.measureExpr} WHERE ${m.filterExpr}` : m.measureExpr;
  }
  if (m.metricType === 'DERIVED') {
    // 02 组装式派生：measure/filter 已落库直接展示；存量登记式回退引用说明
    if (m.measureExpr) {
      return m.filterExpr ? `${m.measureExpr} WHERE ${m.filterExpr}` : m.measureExpr;
    }
    return m.refMetricName ? `引用原子指标 ${m.refMetricName}` : '-';
  }
  const formula = (m.compositions ?? [])
    .map((c) =>
      c.operator === 'REF' ? c.subMetricCode ?? '' : c.expression || COMPOSITE_SYMBOLS[c.operator] || c.operator,
    )
    .join(' ')
    .trim();
  return formula || '-';
};

/** 02 结构化限定条件 → 可见 chips；存量自由文本 dimConstraint 原样展示。 */
const QualifierChips = ({ raw }: { raw?: string }) => {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed) && parsed.length) {
      return (
        <span className="flex flex-wrap gap-1.5">
          {parsed.map((q: { field?: string; op?: string; value?: string }, i: number) => (
            <Tag key={i} className="!m-0">
              {`${q?.field ?? '-'} ${q?.op ?? ''}${q?.value ? ` ${q.value}` : ''}`}
            </Tag>
          ))}
        </span>
      );
    }
  } catch {
    // 非法 JSON 退回文本
  }
  return <span>{raw}</span>;
};

const MetricDetailPage = () => {
  const navigate = useNavigate();
  const params = useParams<{ id?: string }>();
  const [searchParams] = useSearchParams();
  const returnAssetIdValue = searchParams.get('returnAssetId');
  const returnAssetId = returnAssetIdValue && /^\d+$/.test(returnAssetIdValue)
    ? Number(returnAssetIdValue)
    : undefined;
  const metricId = Number(params.id);
  const [metric, setMetric] = useState<MetricRecord | null>(null);
  const [loading, setLoading] = useState(false);
  const [versions, setVersions] = useState<MetricVersionRecord[]>([]);
  const [usageSummary, setUsageSummary] = useState<UsageSummary | null>(null);
  const [usageList, setUsageList] = useState<MetricUsageRecord[]>([]);
  const [usageSummaryState, setUsageSummaryState] = useState<'LOADING' | 'READY' | 'UNAVAILABLE' | 'FORBIDDEN'>('LOADING');
  const [usageListState, setUsageListState] = useState<'LOADING' | 'READY' | 'UNAVAILABLE' | 'FORBIDDEN'>('LOADING');
  const [tagIds, setTagIds] = useState<number[]>([]);
  const [allTags, setAllTags] = useState<MetricTagRecord[]>([]);
  const [tagDrawerOpen, setTagDrawerOpen] = useState(false);
  const [tagDraftIds, setTagDraftIds] = useState<number[]>([]);
  const [tagSaving, setTagSaving] = useState(false);
  const [lineageSummary, setLineageSummary] = useState<AssetLineageSummary | null>(null);
  const [dimModelNames, setDimModelNames] = useState<Record<number, string>>({});
  const [historicalVersion, setHistoricalVersion] = useState<MetricVersionRecord | null>(null);
  const [historicalDrawerOpen, setHistoricalDrawerOpen] = useState(false);
  const [historicalLoading, setHistoricalLoading] = useState(false);
  const [versionCompareOpen, setVersionCompareOpen] = useState(false);
  const [comparisonVersions, setComparisonVersions] = useState<[number, number] | null>(null);

  const loadMetric = useCallback(async () => {
    if (!metricId) return;
    setLoading(true);
    try {
      setMetric(await getMetric(metricId));
    } catch {
      message.error('加载指标详情失败');
    } finally {
      setLoading(false);
    }
  }, [metricId]);

  useEffect(() => {
    void loadMetric();
  }, [loadMetric]);

  const dimModelIdList = (metric?.dimModelIds ?? '')
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isInteger(n) && n > 0);

  useEffect(() => {
    if (!dimModelIdList.length) {
      setDimModelNames({});
      return;
    }
    let cancelled = false;
    // 与编辑弹窗同源:模型展示名经 modeling 接口解析,失败退化为 #id 不阻断详情
    Promise.allSettled(dimModelIdList.map((id) => getModelingModel(id))).then((results) => {
      if (cancelled) return;
      const map: Record<number, string> = {};
      results.forEach((r, i) => {
        if (r.status === 'fulfilled' && r.value?.name) map[dimModelIdList[i]] = r.value.name;
      });
      setDimModelNames(map);
    });
    return () => {
      cancelled = true;
    };
  }, [dimModelIdList.join(',')]);

  useEffect(() => {
    if (!metricId) return;
    listMetricVersions(metricId)
      .then(setVersions)
      .catch(() => setVersions([]));
    setUsageSummaryState('LOADING');
    setUsageListState('LOADING');
    getMetricUsageSummary(metricId)
      .then((summary) => {
        setUsageSummary(summary);
        setUsageSummaryState('READY');
      })
      .catch((error) => {
        setUsageSummary(null);
        setUsageSummaryState(readFailureState(error));
      });
    getMetricUsageList(metricId)
      .then((usages) => {
        setUsageList(usages);
        setUsageListState('READY');
      })
      .catch((error) => {
        setUsageList([]);
        setUsageListState(readFailureState(error));
      });
    getMetricTags(metricId)
      .then(setTagIds)
      .catch(() => setTagIds([]));
    listMetricTags()
      .then(setAllTags)
      .catch(() => setAllTags([]));
    // 摘要键 metric:{id} 与后端 MetricLineageRegistrationService#metricAssetKey 同口径
    getAssetLineageSummary(`metric:${metricId}`)
      .then(setLineageSummary)
      .catch(() => setLineageSummary(null));
  }, [metricId]);

  const assignedTags = allTags.filter((tag) => tagIds.includes(tag.id));
  const dependencyChanges = metric?.dependencyChanges ?? [];
  const authoringNextStep = metric?.authoringNextStep;
  const authoringMeta = authoringNextStep ? AUTHORING_NEXT_STEP_META[authoringNextStep] : undefined;

  const openTagDrawer = () => {
    setTagDraftIds([...tagIds]);
    setTagDrawerOpen(true);
  };

  // assign/remove 均为增量端点:提交前 diff,只发变化部分
  const saveTags = async () => {
    const added = tagDraftIds.filter((id) => !tagIds.includes(id));
    const removed = tagIds.filter((id) => !tagDraftIds.includes(id));
    if (!added.length && !removed.length) {
      setTagDrawerOpen(false);
      return;
    }
    setTagSaving(true);
    try {
      if (added.length) await assignMetricTags(metricId, added);
      for (const tagId of removed) await removeMetricTag(metricId, tagId);
      message.success('标签已更新');
      setTagIds(await getMetricTags(metricId));
      setTagDrawerOpen(false);
    } catch {
      message.error('标签更新失败，请稍后重试');
    } finally {
      setTagSaving(false);
    }
  };

  const openHistoricalVersion = async (version: number) => {
    setHistoricalDrawerOpen(true);
    setHistoricalLoading(true);
    setHistoricalVersion(null);
    try {
      setHistoricalVersion(await getMetricVersion(metricId, version));
    } catch {
      message.error('加载历史版本失败');
    } finally {
      setHistoricalLoading(false);
    }
  };

  const openVersionComparison = (leftVersion: number, rightVersion: number) => {
    setComparisonVersions([leftVersion, rightVersion]);
    setVersionCompareOpen(true);
  };

  /** 引用类字段:有展示名即可点击跳转(仅跳已存在页面),缺失如实显示 '-'。 */
  const refLink = (label?: string | null, to?: string) =>
    label ? (
      to ? (
        <Typography.Link onClick={() => navigate(to)}>{label}</Typography.Link>
      ) : (
        label
      )
    ) : (
      '-'
    );

  const dependencyColumns: ColumnsType<DependencyChange> = [
    {
      title: '依赖类型',
      dataIndex: 'dependencyType',
      width: 120,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    { title: '依赖编码', dataIndex: 'dependencyCode', width: 160, render: (v?: string) => v || '-' },
    {
      title: '登记版本',
      dataIndex: 'registeredVersion',
      width: 100,
      render: (v?: number) => (v != null ? `v${v}` : '-'),
    },
    {
      title: '当前版本',
      dataIndex: 'currentVersion',
      width: 100,
      render: (v?: number) => (v != null ? `v${v}` : '-'),
    },
    {
      title: '依赖状态',
      dataIndex: 'dependencyHealth',
      width: 150,
      render: (health?: DependencyHealth) => {
        if (!health) return <Tag>未知</Tag>;
        const meta = DEPENDENCY_HEALTH_META[health];
        return (
          <span title={meta.description}>
            <Tag color={meta.color}>{meta.label}</Tag>
          </span>
        );
      },
    },
  ];

  const versionColumns: ColumnsType<MetricVersionRecord> = [
    { title: '版本', dataIndex: 'version', width: 80, render: (v: number) => `v${v}` },
    { title: '变更说明', dataIndex: 'changeDesc', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '变更人', dataIndex: 'changedBy', width: 100 },
    {
      title: '变更时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => formatMetricTime(v),
    },
    {
      title: '视图',
      key: 'viewType',
      width: 220,
      render: (_: unknown, record) => (
        <span className="flex items-center gap-1">
          <Tag>历史快照 · 只读</Tag>
          <Typography.Link onClick={() => void openHistoricalVersion(record.version)}>
            查看
          </Typography.Link>
          <Typography.Link
            onClick={() => {
              const otherVersion = versions
                .map((version) => version.version)
                .filter((version) => version !== record.version)
                .sort((left, right) => right - left)[0] ?? metric?.version ?? record.version;
              openVersionComparison(record.version, otherVersion);
            }}
          >
            对比
          </Typography.Link>
        </span>
      ),
    },
  ];

  const usageColumns: ColumnsType<MetricUsageRecord> = [
    {
      title: '类型',
      dataIndex: 'usageType',
      width: 100,
      render: (v: string) => <Tag>{USAGE_TYPE_LABELS[v] ?? v}</Tag>,
    },
    {
      title: '使用方',
      dataIndex: 'usageName',
      render: (v: string | undefined, record) => v || `#${record.usageId}`,
    },
    {
      title: '引用版本',
      dataIndex: 'metricVersion',
      width: 130,
      render: (value?: number | null) => value == null ? '历史版本未知' : `Metric v${value}`,
    },
    {
      title: '上报时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => formatMetricTime(v),
    },
  ];

  const historicalSnapshotText = (() => {
    const snapshot = historicalVersion?.snapshot;
    if (!snapshot) return '';
    try {
      return JSON.stringify(JSON.parse(snapshot), null, 2);
    } catch {
      return snapshot;
    }
  })();

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      {/* Breadcrumb */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <Typography.Link onClick={() => navigate('/metric/manage')}>指标管理</Typography.Link>
            <span className="text-[#c4c9d1]">/</span>
            <div className="text-[20px] font-semibold leading-7">
              {metric ? `${metric.metricName}（${metric.metricCode}）` : '指标详情'}
            </div>
            {metric ? (
              <Tag color={metric.editable === false ? 'default' : 'blue'}>
                {metric.definitionViewType === 'CURRENT_EDITABLE' ? '当前定义 · 可编辑' : '当前定义'}
              </Tag>
            ) : null}
          </div>
          <div className="mt-1 text-[13px] text-[#667085]">指标的完整信息：业务定义、依赖健康、版本、血缘与使用情况</div>
        </div>
        {returnAssetId ? (
          <YakButton onClick={() => navigate(`/data-asset/detail/${returnAssetId}`)}>返回资产详情</YakButton>
        ) : null}
      </div>

      <Spin spinning={loading}>
        {metric ? (
          <div className="mt-5">
            <MetricGovernancePanel
              metricId={metric.id}
              currentVersion={metric.version}
              onCompareVersions={(publishedVersion, currentVersion) =>
                openVersionComparison(publishedVersion, currentVersion)
              }
            />
            {/* Descriptions */}
            <Descriptions
              column={3}
              bordered
              size="middle"
              items={[
                { key: 'metricCode', label: '指标编码', children: metric.metricCode },
                { key: 'metricName', label: '指标名称', children: metric.metricName },
                {
                  key: 'metricType',
                  label: '指标类型',
                  children: (
                    <Tag color={METRIC_TYPE_COLORS[metric.metricType]}>{METRIC_TYPE_LABELS[metric.metricType]}</Tag>
                  ),
                },
                {
                  key: 'status',
                  label: '状态',
                  children: (
                    <Tag color={METRIC_STATUS_COLORS[metric.status]}>{METRIC_STATUS_LABELS[metric.status]}</Tag>
                  ),
                },
                {
                  key: 'statPeriod',
                  label: '统计周期',
                  children: STAT_PERIOD_LABELS[metric.statPeriod] ?? metric.statPeriod,
                },
                { key: 'version', label: '当前版本', children: `v${metric.version}` },
                { key: 'owner', label: '负责人', children: metric.owner || '-' },
                {
                  key: 'createTime',
                  label: '创建时间',
                  children: formatMetricTime(metric.createTime),
                },
                {
                  key: 'updateTime',
                  label: '更新时间',
                  children: formatMetricTime(metric.updateTime),
                },
                {
                  key: 'domainName',
                  label: '业务域',
                  children: refLink(
                    metric.domainName,
                    metric.domainId ? `/semantic/domains?domainId=${metric.domainId}` : undefined,
                  ),
                },
                {
                  key: 'processName',
                  label: '业务过程',
                  children: refLink(
                    metric.processName,
                    metric.processId
                      ? `/semantic/processes?processId=${metric.processId}${metric.domainId ? `&domainId=${metric.domainId}` : ''}`
                      : undefined,
                  ),
                },
                {
                  key: 'caliberName',
                  label: '口径标准',
                  children: refLink(
                    metric.caliberName,
                    metric.caliberId ? `/semantic/standards?standardId=${metric.caliberId}` : undefined,
                  ),
                },
                {
                  key: 'unitName',
                  label: '度量单位',
                  children: refLink(
                    metric.unitName,
                    metric.unitId ? `/semantic/standards?standardId=${metric.unitId}` : undefined,
                  ),
                },
                {
                  key: 'modelName',
                  label: '来源模型',
                  children: refLink(
                    metric.modelName ?? (metric.modelId ? `模型#${metric.modelId}` : null),
                    metric.modelId ? `/modeling/models/${metric.modelId}` : undefined,
                  ),
                },
                {
                  key: 'dimModels',
                  label: 'DIM 模型',
                  children: dimModelIdList.length ? (
                    <span className="flex flex-wrap gap-x-2">
                      {dimModelIdList.map((id) => (
                        <Typography.Link key={id} onClick={() => navigate(`/modeling/models/${id}`)}>
                          {dimModelNames[id] ?? `模型#${id}`}
                        </Typography.Link>
                      ))}
                    </span>
                  ) : (
                    '-'
                  ),
                },
                {
                  key: 'refMetric',
                  label: '引用原子指标',
                  children: refLink(
                    metric.refMetricName,
                    metric.refMetricId ? `/metric/manage/${metric.refMetricId}` : undefined,
                  ),
                },
                {
                  key: 'qualifiers',
                  label: '限定条件',
                  children: metric.qualifiersJson ? (
                    <QualifierChips raw={metric.qualifiersJson} />
                  ) : (
                    metric.dimConstraint ? `${metric.dimConstraint}（存量自由文本）` : '-'
                  ),
                },
                ...(metric.metricType === 'DERIVED'
                  ? [
                      { key: 'measureExpr', label: '度量表达式（组装）', children: metric.measureExpr || '-' },
                      { key: 'filterExpr', label: '过滤条件（组装）', span: 2, children: metric.filterExpr || '-' },
                    ]
                  : []),
                { key: 'statDimensions', label: '统计维度', children: formatStatDimensions(metric.statDimensions) },
                { key: 'calRule', label: '计算规则', span: 3, children: metric ? formatCalcRule(metric) : '-' },
                { key: 'calRuleRaw', label: '口径规则', span: 3, children: metric?.calRule || '-' },
                {
                  key: 'businessDesc',
                  label: '业务口径',
                  span: 3,
                  children: metric.businessDesc || '-',
                },
              ]}
            />

            {/* Phase 5 authoring next step: Save/Enable != Validation/Publication. */}
            {authoringMeta ? (
              <div className={`mt-4 flex flex-wrap items-center gap-3 rounded-lg border px-4 py-3 ${authoringMeta.tone}`}>
                <div className="min-w-0 flex-1">
                  <div className="text-[14px] font-semibold text-[#344054]">{authoringMeta.title}</div>
                  <div className="mt-0.5 text-[13px] text-[#667085]">{authoringMeta.description}</div>
                </div>
                {authoringMeta.action === 'impact' ? (
                  <YakButton onClick={() => navigate(`/metric/impact?metricId=${metricId}`)}>检查依赖影响</YakButton>
                ) : null}
                {authoringMeta.action === 'retry' ? (
                  <YakButton loading={loading} onClick={() => void loadMetric()}>
                    重新检查
                  </YakButton>
                ) : null}
              </div>
            ) : null}

            {/* M2-3 跨域引用摘要:只引用血缘/质量既有事实,不可用时如实说明,不伪造 0 */}
            {lineageSummary ? (
              <div className="mt-4 flex flex-wrap items-center gap-3 rounded-lg border border-[#e5e7eb] bg-[#fafbfc] px-4 py-2.5 text-[13px]">
                {lineageSummary.available ? (
                  <span className="text-[#344054]">
                    上游 {lineageSummary.upstreamTables} 张表，其中{' '}
                    <span
                      className={
                        lineageSummary.unauditedTables > 0
                          ? 'font-semibold text-[#D92D20]'
                          : 'font-semibold text-[#12B76A]'
                      }
                    >
                      {lineageSummary.unauditedTables} 张未稽核
                    </span>
                  </span>
                ) : (
                  <span className="text-[#8A94A3]">
                    上游引用情况暂不可读：{lineageSummary.reason || '事实源缺失'}
                  </span>
                )}
                <Typography.Link
                  className="ml-auto"
                  onClick={() =>
                    navigate(
                      `/data-analysis/lineage?metricId=${encodeURIComponent(String(metricId))}&direction=UPSTREAM`,
                    )
                  }
                >
                  查看血缘
                </Typography.Link>
              </div>
            ) : null}

            {/* Tabs */}
            <div className="mt-5">
              <Tabs
                defaultActiveKey="dependencies"
                items={[
                  {
                    key: 'dependencies',
                    label: `依赖 (${dependencyChanges.length})`,
                    children:
                      dependencyChanges.length > 0 ? (
                        <Table<DependencyChange>
                          rowKey={(row) => `${row.dependencyType}-${row.dependencyId}`}
                          columns={dependencyColumns}
                          dataSource={dependencyChanges}
                          pagination={false}
                          size="small"
                        />
                      ) : (
                        <YakEmpty compact title="暂无依赖" description="指标的模型、口径、单位等依赖在编辑时配置" />
                      ),
                  },
                  {
                    key: 'tags',
                    label: `标签 (${assignedTags.length})`,
                    children: (
                      <div>
                        <div className="mb-3">
                          <YakButton size="small" onClick={openTagDrawer}>
                            管理标签
                          </YakButton>
                        </div>
                        {assignedTags.length > 0 ? (
                          <div className="flex flex-wrap gap-2">
                            {assignedTags.map((tag) => (
                              <Tag key={tag.id} color="blue">
                                {tag.tagName}
                              </Tag>
                            ))}
                          </div>
                        ) : (
                          <YakEmpty compact title="暂无标签" description="点击「管理标签」为指标打标" />
                        )}
                      </div>
                    ),
                  },
                  {
                    key: 'versions',
                    label: `版本历史 (${versions.length})`,
                    children:
                      versions.length > 0 ? (
                        <Table<MetricVersionRecord>
                          rowKey="id"
                          columns={versionColumns}
                          dataSource={versions}
                          pagination={false}
                          size="small"
                        />
                      ) : (
                        <YakEmpty compact title="暂无版本记录" description="指标更新后自动生成版本快照" />
                      ),
                  },
                  {
                    key: 'usage',
                    label: '使用情况',
                    children: usageSummaryState === 'FORBIDDEN' || usageSummaryState === 'UNAVAILABLE' ? (
                      <YakEmpty compact
                        title={usageSummaryState === 'FORBIDDEN' ? '无权读取使用汇总' : '使用汇总暂不可用'}
                        description="当前无法判断引用数量；请恢复读取后重试"
                      />
                    ) : usageSummary ? (
                      <div>
                        <div className="grid grid-cols-6 gap-4">
                          <UsageCard title="总引用" value={usageSummary.totalCount} />
                          <UsageCard title="数据集" value={usageSummary.datasetCount ?? 0} />
                          <UsageCard title="报表" value={usageSummary.reportCount} />
                          <UsageCard title="仪表盘" value={usageSummary.dashboardCount} />
                          <UsageCard title="API" value={usageSummary.apiCount} />
                          <UsageCard title="大屏" value={usageSummary.screenCount} />
                        </div>
                        {usageListState === 'FORBIDDEN' || usageListState === 'UNAVAILABLE' ? (
                          <div className="mt-4 text-[13px] text-[#b54708]">
                            {usageListState === 'FORBIDDEN' ? '无权读取 Reference Usage 明细。' : 'Reference Usage 明细暂不可用，不能据此判断为无引用。'}
                          </div>
                        ) : usageList.length > 0 ? (
                          <Table<MetricUsageRecord>
                            className="mt-4"
                            rowKey="id"
                            size="small"
                            pagination={false}
                            columns={usageColumns}
                            dataSource={usageList}
                          />
                        ) : (
                          <div className="mt-4">
                            <YakEmpty compact title="暂无使用明细" description="消费方保存引用后在此列出" />
                          </div>
                        )}
                      </div>
                    ) : usageSummaryState === 'LOADING' ? (
                      <Spin size="small" />
                    ) : (
                      <YakEmpty compact title="暂无使用记录" description="指标被引用后自动统计使用数据" />
                    ),
                  },
                  {
                    key: 'compositions',
                    label: '组成项',
                    children:
                      metric.compositions && metric.compositions.length > 0 ? (
                        <Timeline
                          items={metric.compositions.map((comp, index) => ({
                            key: comp.id ?? index,
                            children: (
                              <div>
                                <span className="font-medium">子指标 {comp.subMetricId}</span>
                                {comp.operator ? <Tag className="ml-2">{comp.operator}</Tag> : null}
                                {comp.expression ? (
                                  <Typography.Text code className="ml-2">
                                    {comp.expression}
                                  </Typography.Text>
                                ) : null}
                              </div>
                            ),
                          }))}
                        />
                      ) : (
                        <YakEmpty compact title="非复合指标" description="仅复合指标包含子指标组成项" />
                      ),
                  },
                ]}
              />
            </div>
          </div>
        ) : (
          !loading && (
            <div className="mt-5">
              <YakEmpty title="指标不存在或已删除" description="返回列表查看当前项目的指标" />
            </div>
          )
        )}
      </Spin>

      <Drawer
        title={historicalVersion ? `历史版本 v${historicalVersion.version}（只读）` : '历史版本（只读）'}
        open={historicalDrawerOpen}
        onClose={() => setHistoricalDrawerOpen(false)}
        width={720}
      >
        <Spin spinning={historicalLoading}>
          {historicalVersion ? (
            <div>
              <div className="mb-4 flex flex-wrap items-center gap-2 text-[13px] text-[#667085]">
                <Tag>HISTORICAL_SNAPSHOT</Tag>
                <span>不可编辑</span>
                <span>·</span>
                <span>{historicalVersion.changeDesc || '无变更说明'}</span>
                <span>·</span>
                <span>{historicalVersion.changedBy || '-'}</span>
                <span>·</span>
                <span>{formatMetricTime(historicalVersion.createTime)}</span>
              </div>
              <pre className="max-h-[70vh] overflow-auto whitespace-pre-wrap break-words rounded-lg bg-[#f8fafc] p-4 text-[12px] leading-5 text-[#344054]">
                {historicalSnapshotText}
              </pre>
            </div>
          ) : historicalLoading ? null : (
            <YakEmpty compact title="历史版本不可读" description="关闭后可重新尝试加载" />
          )}
        </Spin>
      </Drawer>

      {metric ? (
        <MetricVersionComparisonDrawer
          metricId={metric.id}
          metricVersion={metric.version}
          versions={versions}
          open={versionCompareOpen}
          initialVersions={comparisonVersions}
          onClose={() => setVersionCompareOpen(false)}
        />
      ) : null}

      <Drawer title="管理标签" open={tagDrawerOpen} onClose={() => setTagDrawerOpen(false)} width={360}>
        <div className="text-[13px] text-[#667085]">选择该指标挂载的标签(标签本体在指标标签与统计页创建)</div>
        <Select
          className="mt-3 !w-full"
          mode="multiple"
          placeholder="选择标签"
          value={tagDraftIds}
          onChange={(value) => setTagDraftIds(value)}
          options={allTags.map((tag) => ({ label: tag.tagName, value: tag.id }))}
          optionFilterProp="label"
        />
        <div className="mt-4 flex justify-end gap-2">
          <YakButton onClick={() => setTagDrawerOpen(false)}>取消</YakButton>
          <YakButton type="primary" loading={tagSaving} onClick={() => void saveTags()}>
            保存
          </YakButton>
        </div>
      </Drawer>
    </div>
  );
};

const UsageCard = ({ title, value }: { title: string; value: number }) => (
  <div className="rounded-lg border border-[#e5e7eb] px-4 py-3 text-center">
    <div className="text-[22px] font-semibold">{value}</div>
    <div className="mt-1 text-[13px] text-[#667085]">{title}</div>
  </div>
);

export default MetricDetailPage;
