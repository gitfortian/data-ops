import { useNavigate, useParams } from '@umijs/max';
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
  getMetricLineage,
  getMetricTags,
  getMetricUsageList,
  getMetricUsageSummary,
  listMetricTags,
  listMetricVersions,
  removeMetricTag,
} from '@/services/metric/api';
import type {
  MetricDependencyRecord,
  MetricRecord,
  MetricTagRecord,
  MetricUsageRecord,
  MetricVersionRecord,
  UsageSummary,
} from '@/services/metric/types';
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

/** 与 yak_metric_usage.usage_type 对齐(DATASET 为 01 消费接线新增)。 */
const USAGE_TYPE_LABELS: Record<string, string> = {
  DATASET: '数据集',
  REPORT: '报表',
  DASHBOARD: '仪表盘',
  API: 'API',
  SCREEN: '大屏',
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
  const metricId = Number(params.id);
  const [metric, setMetric] = useState<MetricRecord | null>(null);
  const [loading, setLoading] = useState(false);
  const [versions, setVersions] = useState<MetricVersionRecord[]>([]);
  const [dependencies, setDependencies] = useState<MetricDependencyRecord[]>([]);
  const [usageSummary, setUsageSummary] = useState<UsageSummary | null>(null);
  const [usageList, setUsageList] = useState<MetricUsageRecord[]>([]);
  const [tagIds, setTagIds] = useState<number[]>([]);
  const [allTags, setAllTags] = useState<MetricTagRecord[]>([]);
  const [tagDrawerOpen, setTagDrawerOpen] = useState(false);
  const [tagDraftIds, setTagDraftIds] = useState<number[]>([]);
  const [tagSaving, setTagSaving] = useState(false);
  const [lineageSummary, setLineageSummary] = useState<AssetLineageSummary | null>(null);
  const [dimModelNames, setDimModelNames] = useState<Record<number, string>>({});

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
    getMetricLineage(metricId)
      .then(setDependencies)
      .catch(() => setDependencies([]));
    getMetricUsageSummary(metricId)
      .then(setUsageSummary)
      .catch(() => setUsageSummary(null));
    getMetricUsageList(metricId)
      .then(setUsageList)
      .catch(() => setUsageList([]));
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

  const dependencyColumns: ColumnsType<MetricDependencyRecord> = [
    {
      title: '依赖类型',
      dataIndex: 'dependencyType',
      width: 120,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    { title: '依赖编码', dataIndex: 'dependencyCode', width: 160, render: (v?: string) => v || '-' },
    {
      title: '依赖版本',
      dataIndex: 'dependencyVersion',
      width: 100,
      render: (v?: number) => (v != null ? `v${v}` : '-'),
    },
    {
      title: '绑定时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => formatMetricTime(v),
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
      title: '上报时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => formatMetricTime(v),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      {/* Breadcrumb */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <Typography.Link onClick={() => navigate('/metric/manage')}>指标管理</Typography.Link>
            <span className="text-[#c4c9d1]">/</span>
            <div className="text-[20px] font-semibold leading-7">
              {metric ? `${metric.metricName}（${metric.metricCode}）` : '指标详情'}
            </div>
          </div>
          <div className="mt-1 text-[13px] text-[#667085]">指标的完整信息：属性、依赖、标签、版本与使用情况</div>
        </div>
      </div>

      <Spin spinning={loading}>
        {metric ? (
          <div className="mt-5">
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
                { key: 'domainName', label: '业务域', children: refLink(metric.domainName, '/semantic/domains') },
                { key: 'processName', label: '业务过程', children: refLink(metric.processName, '/semantic/processes') },
                { key: 'caliberName', label: '口径标准', children: refLink(metric.caliberName, '/semantic/standards') },
                { key: 'unitName', label: '度量单位', children: refLink(metric.unitName, '/semantic/standards') },
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
                    label: `依赖 (${dependencies.length})`,
                    children:
                      dependencies.length > 0 ? (
                        <Table<MetricDependencyRecord>
                          rowKey="id"
                          columns={dependencyColumns}
                          dataSource={dependencies}
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
                    children: usageSummary ? (
                      <div>
                        <div className="grid grid-cols-6 gap-4">
                          <UsageCard title="总引用" value={usageSummary.totalCount} />
                          <UsageCard title="数据集" value={usageSummary.datasetCount ?? 0} />
                          <UsageCard title="报表" value={usageSummary.reportCount} />
                          <UsageCard title="仪表盘" value={usageSummary.dashboardCount} />
                          <UsageCard title="API" value={usageSummary.apiCount} />
                          <UsageCard title="大屏" value={usageSummary.screenCount} />
                        </div>
                        {usageList.length > 0 ? (
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

      <Drawer title="管理标签" open={tagDrawerOpen} onClose={() => setTagDrawerOpen(false)} width={360}>
        <div className="text-[13px] text-[#667085]">选择该指标挂载的标签(标签本体在指标服务页创建)</div>
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
