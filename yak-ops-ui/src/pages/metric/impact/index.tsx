import { useNavigate, useSearchParams } from '@umijs/max';
import { message, Select, Spin, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { useCallback } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { getAffectedMetrics, getMetricImpactContext, pageMetrics } from '@/services/metric/api';
import { pageSemanticStandards } from '@/services/semantic/api';
import { pageModelingModels } from '@/services/modeling/api';
import type {
  AffectedMetricRecord,
  DependencyChange,
  MetricImpactContext,
  MetricRecord,
} from '@/services/metric/types';
import { METRIC_STATUS_COLORS, METRIC_STATUS_LABELS, METRIC_TYPE_COLORS, METRIC_TYPE_LABELS } from '../constants';
import ImpactUsageContext from './components/ImpactUsageContext';

const CHANGE_STATUS_LABELS: Record<string, { label: string; color: string }> = {
  CHANGED: { label: '已变更', color: 'orange' },
  UNCHANGED: { label: '无变化', color: 'green' },
  PENDING_CHECK: { label: '待检查', color: 'default' },
  MISSING: { label: '已缺失', color: 'red' },
  UNKNOWN: { label: '无法获取当前版本', color: 'default' },
};

interface Option {
  label: string;
  value: number;
}

/** 正向:依赖健康 + Reference Usage + Observed Runtime Usage coverage。 */
const UpstreamChangesView = () => {
  const [searchParams] = useSearchParams();
  const queryMetricIdValue = searchParams.get('metricId');
  const queryMetricId = queryMetricIdValue && /^\d+$/.test(queryMetricIdValue)
    ? Number(queryMetricIdValue)
    : null;
  const [metricId, setMetricId] = useState<number | null>(queryMetricId);
  const [metricOptions, setMetricOptions] = useState<Option[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [context, setContext] = useState<MetricImpactContext | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setSearchLoading(true);
    pageMetrics({ pageNo: 1, pageSize: 200 })
      .then((result) => {
        setMetricOptions(
          (result.records ?? []).map((record: MetricRecord) => ({
            label: `${record.metricName}（${record.metricCode}）`,
            value: record.id,
          })),
        );
      })
      .catch(() => {
        message.error('加载指标列表失败');
      })
      .finally(() => setSearchLoading(false));
  }, []);

  const analyzeImpact = useCallback(async (id: number) => {
    setLoading(true);
    try {
      setContext(await getMetricImpactContext(id));
    } catch {
      message.error('影响上下文加载失败，请稍后重试');
      setContext(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (queryMetricId == null) return;
    setMetricId(queryMetricId);
    void analyzeImpact(queryMetricId);
  }, [analyzeImpact, queryMetricId]);

  const changeColumns: ColumnsType<DependencyChange> = [
    {
      title: '依赖类型',
      dataIndex: 'dependencyType',
      width: 120,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: '依赖编码',
      dataIndex: 'dependencyCode',
      width: 160,
      render: (v?: string) => v || '-',
    },
    {
      title: '注册版本',
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
      title: '变更状态',
      dataIndex: 'changeStatus',
      width: 120,
      render: (value: string) => {
        const config = CHANGE_STATUS_LABELS[value] ?? { label: value, color: 'default' };
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
  ];

  const changedCount =
    context?.dependencies.filter((c) => c.changeStatus === 'CHANGED' || c.changeStatus === 'MISSING').length ?? 0;
  const unavailableDependencyCount =
    context?.dependencies.filter((change) => change.dependencyHealth === 'UNAVAILABLE').length ?? 0;
  const referenceUsageCount = context
    && (context.referenceUsageCoverage?.status === 'READY' || context.referenceUsageCoverage?.status === 'EMPTY')
    ? context.referenceUsage.length
    : '—';

  return (
    <div>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Select
          showSearch
          loading={searchLoading}
          placeholder="选择指标进行影响分析"
          className="!w-[320px]"
          value={metricId ?? undefined}
          onChange={(value) => {
            setMetricId(value);
            setContext(null);
          }}
          options={metricOptions}
          filterOption={(input, option) =>
            (option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
          }
        />
        {metricId ? (
          <YakButton
            type="primary"
            className="!h-9 !rounded-lg !px-4 !text-white"
            onClick={() => void analyzeImpact(metricId)}
          >
            执行分析
          </YakButton>
        ) : null}
      </div>

      <Spin spinning={loading}>
        {context ? (
          <div className="mt-4 space-y-5">
            <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
              <div className="text-[15px] font-semibold">
                {context.metricName}（{context.metricCode}） · v{context.metricVersion}
              </div>
              <div className="mt-1 text-[12px] text-[#667085]">
                Impact Context 生成于 {context.generatedAt}；Reference Usage 与 Observed Runtime Usage 保持独立事实来源
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
                <div className="text-[22px] font-semibold">{context.dependencies.length}</div>
                <div className="mt-1 text-[13px] text-[#667085]">依赖总数</div>
              </div>
              <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
                <div className={`text-[22px] font-semibold ${changedCount > 0 || unavailableDependencyCount > 0 ? 'text-[#fa8c16]' : ''}`}>
                  {unavailableDependencyCount > 0 ? `${changedCount} · ${unavailableDependencyCount} 项待确认` : changedCount}
                </div>
                <div className="mt-1 text-[13px] text-[#667085]">依赖变更 / 缺失</div>
              </div>
              <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
                <div className="text-[22px] font-semibold">{referenceUsageCount}</div>
                <div className="mt-1 text-[13px] text-[#667085]">Reference Usage</div>
              </div>
            </div>

            <div>
              <div className="mb-2 font-medium">Dependency Evidence</div>
              {context.dependencies.length > 0 ? (
                <Table<DependencyChange>
                  rowKey={(row) => `${row.dependencyType}-${row.dependencyId}`}
                  columns={changeColumns}
                  dataSource={context.dependencies}
                  pagination={false}
                  size="small"
                />
              ) : (
                <YakEmpty compact title="无依赖项" description="该指标暂未配置依赖" />
              )}
            </div>

            <ImpactUsageContext context={context} />
          </div>
        ) : metricId ? (
          <div className="mt-8">
            <YakEmpty title="点击「执行分析」" description="检查依赖、Reference Usage 与可得的 Observed Runtime Usage" />
          </div>
        ) : (
          <div className="mt-8">
            <YakEmpty title="请选择指标" description="从下拉列表选择一个指标进行影响分析" />
          </div>
        )}
      </Spin>
    </div>
  );
};

const UPSTREAM_TYPE_OPTIONS = [
  { label: '来源模型 (DWD)', value: 'MODEL' },
  { label: '指标（被派生/复合引用）', value: 'METRIC' },
  { label: '口径标准', value: 'CALIBER' },
  { label: '单位', value: 'UNIT' },
];

/** 反向:上游对象变更 → 波及哪些指标(基于依赖登记快照反查,零引用如实返回空)。 */
const AffectedMetricsView = () => {
  const navigate = useNavigate();
  const [upstreamType, setUpstreamType] = useState<string>('MODEL');
  const [targetId, setTargetId] = useState<number | null>(null);
  const [targetOptions, setTargetOptions] = useState<Option[]>([]);
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [rows, setRows] = useState<AffectedMetricRecord[] | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setTargetId(null);
    setRows(null);
    setOptionsLoading(true);
    const load = async () => {
      try {
        if (upstreamType === 'MODEL') {
          const result = await pageModelingModels({ pageNo: 1, pageSize: 200, layerCode: 'DWD' });
          setTargetOptions(
            (result.bizData ?? [])
              .filter((model) => model.id != null)
              .map((model) => ({ label: `${model.name ?? model.code}（${model.code}）`, value: Number(model.id) })),
          );
        } else if (upstreamType === 'METRIC') {
          const result = await pageMetrics({ pageNo: 1, pageSize: 200 });
          setTargetOptions(
            (result.records ?? []).map((record) => ({
              label: `${record.metricName}（${record.metricCode}）`,
              value: record.id,
            })),
          );
        } else {
          const result = await pageSemanticStandards({
            pageNo: 1,
            pageSize: 200,
            kind: upstreamType as 'CALIBER' | 'UNIT',
          });
          setTargetOptions(
            (result.bizData ?? []).map((std) => ({
              label: `${std.name}（${std.code}）`,
              value: Number(std.id),
            })),
          );
        }
      } catch {
        setTargetOptions([]);
        message.error('加载上游对象列表失败');
      } finally {
        setOptionsLoading(false);
      }
    };
    void load();
  }, [upstreamType]);

  const analyze = async () => {
    if (targetId == null) return;
    setLoading(true);
    try {
      setRows(await getAffectedMetrics(upstreamType, targetId));
    } catch {
      message.error('反向影响分析失败，请稍后重试');
      setRows(null);
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<AffectedMetricRecord> = [
    {
      title: '指标名称',
      dataIndex: 'metricName',
      render: (value: string, row) => (
        <Typography.Link onClick={() => navigate(`/metric/manage/${row.metricId}`)}>{value}</Typography.Link>
      ),
    },
    { title: '指标编码', dataIndex: 'metricCode', width: 180 },
    {
      title: '类型',
      dataIndex: 'metricType',
      width: 100,
      render: (value: string) => (
        <Tag color={METRIC_TYPE_COLORS[value as keyof typeof METRIC_TYPE_COLORS]}>
          {METRIC_TYPE_LABELS[value as keyof typeof METRIC_TYPE_LABELS] ?? value}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'metricStatus',
      width: 90,
      render: (value: string) => (
        <Tag color={METRIC_STATUS_COLORS[value as keyof typeof METRIC_STATUS_COLORS]}>
          {METRIC_STATUS_LABELS[value as keyof typeof METRIC_STATUS_LABELS] ?? value}
        </Tag>
      ),
    },
    {
      title: '引用方式',
      dataIndex: 'dependencyTypes',
      width: 160,
      render: (values: string[]) => (
        <span className="flex flex-wrap gap-1">
          {(values ?? []).map((value) => (
            <Tag key={value}>{value}</Tag>
          ))}
        </span>
      ),
    },
    {
      title: '引用时刻版本',
      dataIndex: 'registeredVersion',
      width: 120,
      render: (v?: number) => (v != null ? `v${v}` : '-'),
    },
    { title: '负责人', dataIndex: 'owner', width: 100, render: (v?: string) => v || '-' },
  ];

  return (
    <div>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Select
          className="!w-[220px]"
          value={upstreamType}
          options={UPSTREAM_TYPE_OPTIONS}
          onChange={(value) => setUpstreamType(value)}
        />
        <Select
          showSearch
          loading={optionsLoading}
          placeholder="选择上游对象"
          className="!w-[320px]"
          value={targetId ?? undefined}
          onChange={(value) => {
            setTargetId(value);
            setRows(null);
          }}
          options={targetOptions}
          filterOption={(input, option) =>
            (option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
          }
        />
        {targetId != null ? (
          <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={() => void analyze()}>
            分析波及面
          </YakButton>
        ) : null}
      </div>

      <Spin spinning={loading}>
        {rows ? (
          <div className="mt-4">
            {rows.length > 0 ? (
              <Table<AffectedMetricRecord>
                rowKey="metricId"
                columns={columns}
                dataSource={rows}
                pagination={false}
                size="small"
              />
            ) : (
              <YakEmpty
                compact
                title="暂无指标引用该上游"
                description="依赖登记在指标保存时自动写入，新绑定后此处即可查到"
              />
            )}
          </div>
        ) : (
          <div className="mt-8">
            <YakEmpty title="请选择上游对象" description="选择一个模型/指标/标准，查看哪些指标登记依赖了它" />
          </div>
        )}
      </Spin>
    </div>
  );
};

const MetricImpactPage = () => (
  <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div>
        <div className="text-[20px] font-semibold leading-7">影响分析</div>
        <div className="mt-1 text-[13px] text-[#667085]">
          正向组合依赖、Reference Usage 与可得的运行证据；反向查看上游对象变更波及的指标清单
        </div>
      </div>
    </div>

    <Tabs
      className="mt-2"
      defaultActiveKey="upstream"
      items={[
        { key: 'upstream', label: '正向：指标影响上下文', children: <UpstreamChangesView /> },
        { key: 'affected', label: '反向：谁引用了它', children: <AffectedMetricsView /> },
      ]}
    />
  </div>
);

export default MetricImpactPage;
