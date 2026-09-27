import { useNavigate } from '@umijs/max';
import { Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { YakButton, YakEmpty } from '@/components/ui';
import type {
  MetricImpactContext,
  MetricLineageEvidence,
  MetricObservedUsageCoverage,
  MetricReferenceUsageEvidence,
} from '@/services/metric/types';

const COVERAGE_META: Record<string, { label: string; color?: string }> = {
  READY: { label: '证据可读', color: 'green' },
  EMPTY: { label: '暂无证据' },
  UNAVAILABLE: { label: 'Provider 不可用', color: 'orange' },
  FORBIDDEN: { label: '无权限', color: 'red' },
  NOT_APPLICABLE: { label: '不适用' },
};

const referenceColumns: ColumnsType<MetricReferenceUsageEvidence> = [
  { title: '引用类型', dataIndex: 'usageType', width: 120, render: (value: string) => <Tag>{value}</Tag> },
  {
    title: '引用方',
    dataIndex: 'usageName',
    render: (value: string | undefined, row) => value || `${row.usageType}#${row.usageId}`,
  },
  { title: '稳定 ID', dataIndex: 'usageId', width: 110, render: (value: number) => `#${value}` },
  {
    title: '登记时间',
    dataIndex: 'recordedAt',
    width: 190,
    render: (value?: string) => value || '-',
  },
];

const lineageColumns: ColumnsType<MetricLineageEvidence> = [
  {
    title: '方向',
    dataIndex: 'direction',
    width: 100,
    render: (value: MetricLineageEvidence['direction']) => (
      <Tag color={value === 'UPSTREAM' ? 'blue' : value === 'DOWNSTREAM' ? 'green' : undefined}>{value}</Tag>
    ),
  },
  { title: '关系', dataIndex: 'relationType', width: 130, render: (value?: string) => value || '-' },
  { title: 'Source', dataIndex: 'sourceAssetKey', ellipsis: true },
  { title: 'Target', dataIndex: 'targetAssetKey', ellipsis: true },
  {
    title: '来源证据',
    key: 'provenance',
    width: 160,
    render: (_: unknown, row) => `${row.sourceType || '-'}${row.sourceId ? `:${row.sourceId}` : ''}`,
  },
  { title: '版本', dataIndex: 'version', width: 90, render: (value?: string) => value || '-' },
  { title: '观测时间', dataIndex: 'observedAt', width: 190, render: (value?: string) => value || '-' },
];

const ObservedCoverage = ({ coverage }: { coverage: MetricObservedUsageCoverage }) => {
  const meta = COVERAGE_META[coverage.status] ?? { label: coverage.status };
  return (
    <div className="rounded-lg border border-[#e5e7eb] p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Typography.Text strong>{coverage.provider}</Typography.Text>
        <Tag color={meta.color}>{meta.label}</Tag>
        <Typography.Text type="secondary" className="!text-[12px]">
          运行事实仍由该 Provider / Phase 4 owning contract 持有
        </Typography.Text>
      </div>
      {coverage.reason ? <div className="mt-2 text-[13px] text-[#667085]">{coverage.reason}</div> : null}
      {coverage.evidence.length > 0 ? (
        <div className="mt-3 space-y-2">
          {coverage.evidence.map((evidence, index) => (
            <div
              key={evidence.evidenceId ?? `${coverage.provider}-${index}`}
              className="rounded border border-[#f0f0f0] px-3 py-2 text-[13px]"
            >
              <div className="flex flex-wrap gap-x-4 gap-y-1">
                <span>Evidence: {evidence.evidenceId || '-'}</span>
                <span>Product: {evidence.productKey || '-'}</span>
                <span>Consumer: {evidence.consumerRef || '-'}</span>
                <span>Action: {evidence.action || '-'}</span>
                <span>Outcome: {evidence.outcome || '-'}</span>
                <span>Observed: {evidence.observedAt || '-'}</span>
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
};

const ImpactUsageContext = ({ context }: { context: MetricImpactContext }) => {
  const navigate = useNavigate();
  const lineageMeta = COVERAGE_META[context.lineage.status] ?? { label: context.lineage.status };

  return (
    <div className="space-y-5">
      <div>
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <Typography.Text strong>Lineage Evidence</Typography.Text>
          <Tag color={lineageMeta.color}>{lineageMeta.label}</Tag>
          <Typography.Text type="secondary" className="!text-[12px]">
            来源：{context.lineage.provider} · Root {context.lineage.rootAssetKey} · 此处仅展示 direct depth=1，不推断为 Consumer
          </Typography.Text>
          <YakButton
            size="small"
            className="ml-auto"
            onClick={() => navigate(`/data-analysis/lineage?metricId=${context.metricId}&direction=UPSTREAM`)}
          >
            查看完整血缘
          </YakButton>
        </div>
        {context.lineage.reason ? (
          <div className="mb-2 text-[13px] text-[#667085]">{context.lineage.reason}</div>
        ) : null}
        {context.lineage.evidence.length > 0 ? (
          <Table<MetricLineageEvidence>
            rowKey="relationId"
            size="small"
            pagination={false}
            columns={lineageColumns}
            dataSource={context.lineage.evidence}
          />
        ) : context.lineage.status === 'UNAVAILABLE' || context.lineage.status === 'FORBIDDEN' ? (
          <YakEmpty
            compact
            title="Lineage Evidence 暂不可读"
            description="Provider 不可用或无权限不能解释为没有影响；恢复后可重新执行分析"
          />
        ) : (
          <YakEmpty compact title="暂无直接血缘关系" description="Lineage Provider 正常，但当前未记录 direct relation" />
        )}
      </div>

      <div>
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <Typography.Text strong>Reference Usage</Typography.Text>
          <Tag>{context.referenceUsage.length}</Tag>
          <Typography.Text type="secondary" className="!text-[12px]">
            表示哪些下游对象声明/保存了对该 Metric 定义的引用，不代表真实调用次数
          </Typography.Text>
        </div>
        {context.referenceUsage.length > 0 ? (
          <Table<MetricReferenceUsageEvidence>
            rowKey="referenceId"
            size="small"
            pagination={false}
            columns={referenceColumns}
            dataSource={context.referenceUsage}
          />
        ) : (
          <YakEmpty compact title="暂无 Reference Usage" description="当前没有下游对象登记对该指标的稳定引用" />
        )}
      </div>

      <div>
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <Typography.Text strong>Observed Runtime Usage</Typography.Text>
          <Typography.Text type="secondary" className="!text-[12px]">
            来自 Phase 4 / source owning domain 的真实 Query / Preview / Export / Invoke evidence；不会由 Metric 推断
          </Typography.Text>
        </div>
        <div className="space-y-2">
          {context.observedUsage.map((coverage) => (
            <ObservedCoverage key={coverage.provider} coverage={coverage} />
          ))}
        </div>
      </div>
    </div>
  );
};

export default ImpactUsageContext;
