import Table from '@/components/ReadableTable';
import { YakEmpty } from '@/components/ui';
import YakButton from '@/components/YakButton';
import { getMetadataOverview } from '@/services/metadata';
import type {
  MetadataOverviewData,
  MetadataOverviewEntityType,
  MetadataOverviewRecentRun,
} from '@/services/metadata/types';
import { formatMetadataTime } from '@/services/metadata/presentation';
import { history } from '@umijs/max';
import {
  ArrowRightOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  ReloadOutlined,
  ScheduleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Alert, Button, Empty, Spin, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { ReactNode } from 'react';
import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../shared';

const channelLabels: Record<string, string> = {
  HARVESTED: '物理采集',
  REGISTERED: '投影对账',
};

const taskLabels: Record<string, string> = {
  FILL_COMMENT: '待补充注释',
  CONFIRM_LABEL: '待确认标签',
  FIX_CONFORMANCE: '待修复一致性',
  REVIEW_GONE: '待复核已撤销实体',
};

const runStatus = (status: string) => {
  if (status === 'SUCCESS') return { color: 'success', label: '成功' };
  if (status === 'SUSPECT') return { color: 'warning', label: '疑似坍塌 · 已熔断' };
  if (status === 'FAILED') return { color: 'error', label: '失败' };
  return { color: 'processing', label: status || '状态未知' };
};

const formatDuration = (value?: number | null) => {
  if (value == null) return '—';
  if (value < 1000) return String(value) + ' ms';
  return (value / 1000).toFixed(1) + ' 秒';
};

const Panel = ({
  title, description, action, children, className = '',
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) => (
  <section className={'min-w-0 rounded-2xl border border-solid border-[#e6eaf0] bg-white p-5 shadow-[0_4px_20px_rgba(15,23,42,0.025)] ' + className}>
    <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h2 className="m-0 text-[16px] font-semibold leading-6 text-[#253145]">{title}</h2>
        {description ? <p className="mb-0 mt-1 text-[12px] leading-5 text-[#8190a4]">{description}</p> : null}
      </div>
      {action}
    </div>
    {children}
  </section>
);

const Metric = ({
  label, value, note, icon, attention,
}: {
  label: string;
  value: number;
  note: string;
  icon: ReactNode;
  attention?: boolean;
}) => (
  <div className="min-w-0 rounded-2xl border border-solid border-[#e6eaf0] bg-white p-5 shadow-[0_4px_20px_rgba(15,23,42,0.025)]">
    <div className="flex items-center justify-between text-[13px] font-medium text-[#66758b]">
      {label}
      <span className={'flex h-9 w-9 items-center justify-center rounded-xl text-[17px] ' + (attention ? 'bg-[#fff5e9] text-[#c77725]' : 'bg-[#f0f5fd] text-[#527bac]')}>
        {icon}
      </span>
    </div>
    <div className={'mt-2 text-[31px] font-semibold leading-10 tabular-nums tracking-tight ' + (attention ? 'text-[#a45b1b]' : 'text-[#202b3e]')}>
      {value.toLocaleString()}
    </div>
    <div className="mt-2 text-[12px] leading-5 text-[#8290a3]">{note}</div>
  </div>
);

const MetadataOverviewPage = () => {
  const [overview, setOverview] = useState<MetadataOverviewData>();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const loadOverview = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      setOverview(await getMetadataOverview());
    } catch {
      setOverview(undefined);
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  const recentAttentionCount =
    overview?.recentRuns.filter((run) => run.status === 'FAILED' || run.status === 'SUSPECT').length ?? 0;

  const entityColumns: ColumnsType<MetadataOverviewEntityType> = [
    {
      title: '实体类型',
      key: 'type',
      render: (_, row) => (
        <div className="min-w-0">
          <div className="font-medium text-[#2c3b50]">{row.displayName || row.typeName}</div>
          <div className="mt-0.5 text-[11px] text-[#94a0b1]">{row.typeName}</div>
        </div>
      ),
    },
    {
      title: '来源',
      dataIndex: 'collectible',
      width: 110,
      render: (physical: boolean) => <Tag color={physical ? 'blue' : 'default'}>{physical ? '物理采集' : '写时登记'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value === 'ACTIVE' ? '使用中' : value}</Tag>,
    },
    {
      title: '目录实体',
      dataIndex: 'entityCount',
      width: 94,
      align: 'right',
      render: (value: number) => <span className="font-medium tabular-nums text-[#34445a]">{value.toLocaleString()}</span>,
    },
  ];

  const runColumns: ColumnsType<MetadataOverviewRecentRun> = [
    {
      title: '任务 / 执行方式',
      key: 'job',
      render: (_, run) => (
        <div className="min-w-[160px]">
          <div className="font-medium text-[#2b3950]">{run.jobName || run.jobCode || '任务 #' + run.jobId}</div>
          <div className="mt-0.5 text-[11px] text-[#8b98aa]">
            {channelLabels[run.providerType] || run.providerType} · {run.dryRun ? '仅预演' : run.triggerType === 'SCHEDULE' ? '定时运行' : '手工运行'} · Run #{run.runId}
          </div>
        </div>
      ),
    },
    {
      title: '结果',
      dataIndex: 'status',
      width: 150,
      render: (value: string) => {
        const current = runStatus(value);
        return <Tag color={current.color}>{current.label}</Tag>;
      },
    },
    {
      title: '本轮实体变化',
      key: 'counts',
      width: 245,
      render: (_, run) => (
        <div className="text-[12px] tabular-nums leading-5 text-[#647389]">
          总 {run.totalCount} · 新增 {run.newCount} · 更新 {run.changedCount}
          {run.goneCount > 0 ? <span className="ml-2 text-[#b56b2b]">撤销 {run.goneCount}</span> : null}
          {run.partialFailedCount > 0 ? <span className="ml-2 text-[#b3453e]">部分失败 {run.partialFailedCount}</span> : null}
        </div>
      ),
    },
    {
      title: '时间 / 耗时',
      key: 'time',
      width: 185,
      render: (_, run) => (
        <div className="text-[12px] leading-5 text-[#647389]">
          <div>{formatMetadataTime(run.startedAt || undefined)}</div>
          <div className="text-[#99a3b3]">{formatDuration(run.durationMs)}</div>
        </div>
      ),
    },
  ];

  return (
    <main className="min-h-[calc(100dvh-64px)] bg-[#f7f8fb] px-6 pb-8 pt-6 max-md:px-4">
      <PageHeader
        title="元数据概览"
        subtitle="先看当前目录是否可用、采集运行有没有异常，再决定下一步。所有计数均来自当前项目的元数据记录。"
        extra={
          <>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadOverview()}>刷新</Button>
            <Button onClick={() => history.push('/metadata/explorer')}>浏览元数据目录</Button>
            <YakButton type="primary" onClick={() => history.push('/data-metadata/collect')}>
              采集任务 <ArrowRightOutlined />
            </YakButton>
          </>
        }
      />
      {failed ? (
        <Alert
          className="mt-5 rounded-xl"
          type="error"
          showIcon
          message="当前项目的概览暂时无法加载"
          description="查询失败不表示没有元数据。请检查项目权限或服务状态，再重新加载。"
          action={<Button onClick={() => void loadOverview()}>重新加载</Button>}
        />
      ) : null}
      <Spin spinning={loading}>
        {overview ? (
          <div className="mt-6 space-y-5">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <Metric label="目录实体" value={overview.catalogEntityCount} icon={<DatabaseOutlined />} note="当前项目已登记的目录实体（含多种实体类型）" />
              <Metric label="已保存采集任务" value={overview.totalJobCount} icon={<ScheduleOutlined />} note={'已启用 ' + overview.enabledJobCount + ' · 待预演 ' + overview.dryRunRequiredJobCount} />
              <Metric label="需要关注的运行" value={recentAttentionCount} icon={<WarningOutlined />} attention={recentAttentionCount > 0} note={'最近 ' + overview.recentRunLimit + ' 条记录中的失败 / 熔断，不代表历史总数'} />
              <Metric label="未办结治理待办" value={overview.openTaskCount} icon={<FileSearchOutlined />} note="当前项目按待办类型汇总" />
            </div>

            {recentAttentionCount > 0 ? (
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-solid border-[#f1dfc7] bg-[#fffaf2] px-4 py-3">
                <span className="text-[13px] text-[#9a622d]">最近运行中发现失败或熔断，请先查看执行范围和错误，不要把部分失败当作源表消失。</span>
                <Button type="link" size="small" onClick={() => history.push('/data-metadata/collect')}>查看运行记录 <ArrowRightOutlined /></Button>
              </div>
            ) : null}

            <div className="grid grid-cols-1 gap-5 xl:grid-cols-5">
              <Panel
                title="当前目录 · 实体类型"
                description="按元模型类型统计；目录中的实体数量不等于源数据库完整率。"
                className="xl:col-span-3"
                action={<Button type="link" onClick={() => history.push('/metadata/explorer')}>打开目录 <ArrowRightOutlined /></Button>}
              >
                <Table<MetadataOverviewEntityType>
                  rowKey="typeName"
                  size="small"
                  pagination={false}
                  columns={entityColumns}
                  dataSource={overview.entityTypes}
                  scroll={{ x: 520 }}
                  locale={{ emptyText: <YakEmpty compact title="暂无实体类型" description="先检查元模型配置，再执行物理采集" /> }}
                />
              </Panel>

              <Panel
                title="任务运行准备"
                description="保存、预演和启用调度是三个独立步骤。"
                className="xl:col-span-2"
                action={<Button type="link" onClick={() => history.push('/data-metadata/collect')}>管理任务 <ArrowRightOutlined /></Button>}
              >
                {overview.jobsByProvider.length ? (
                  <div className="space-y-3">
                    {overview.jobsByProvider.map((row) => (
                      <div key={row.providerType} className="rounded-xl border border-solid border-[#eef0f4] bg-[#fbfcfe] p-4">
                        <div className="flex items-center justify-between">
                          <span className="text-[13px] font-medium text-[#2c3b50]">{channelLabels[row.providerType] || row.providerType}</span>
                          <span className="text-[19px] font-semibold tabular-nums text-[#29374c]">{row.totalCount}</span>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[12px] text-[#718097]">
                          <span>已启用 {row.enabledCount}</span><span>待预演 {row.dryRunRequiredCount}</span>
                        </div>
                        {row.providerType === 'REGISTERED' ? (
                          <div className="mt-2 text-[11px] text-[#b16b35]">仅支持保存配置，独立对账执行器尚未开放。</div>
                        ) : null}
                      </div>
                    ))}
                  </div>
                ) : (
                  <YakEmpty compact title="还没有采集任务" description="创建物理采集任务后，在这里跟踪预演和调度状态">
                    <Button type="primary" onClick={() => history.push('/data-metadata/collect')}>创建采集任务</Button>
                  </YakEmpty>
                )}
              </Panel>
            </div>

            <Panel
              title="最近运行"
              description="这里展示历史 Run 的执行证据；当前目录是独立查询，不能当作该 Run 的完整表清单。"
              action={<Button type="link" onClick={() => history.push('/data-metadata/collect')}>查看任务与运行历史 <ArrowRightOutlined /></Button>}
            >
              <Table<MetadataOverviewRecentRun>
                rowKey="runId"
                size="small"
                pagination={false}
                columns={runColumns}
                dataSource={overview.recentRuns}
                scroll={{ x: 830 }}
                locale={{ emptyText: <YakEmpty compact title="还没有运行记录" description="先保存物理采集任务，再预演或手工采集" /> }}
              />
            </Panel>

            <Panel title="元数据治理待办" description="仅汇总当前目录中尚未办结的治理工作，不自动替用户处理。">
              {overview.openTasks.length ? (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                  {overview.openTasks.map((task) => (
                    <div key={task.taskType} className="flex items-center justify-between rounded-xl border border-solid border-[#e9edf3] bg-[#fbfcfe] px-4 py-3">
                      <span className="text-[13px] text-[#617087]">{taskLabels[task.taskType] || task.taskType}</span>
                      <span className="ml-3 text-[20px] font-semibold tabular-nums text-[#2a3a50]">{task.openCount}</span>
                    </div>
                  ))}
                </div>
              ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有未办结治理待办" />}
            </Panel>
          </div>
        ) : !loading && !failed ? (
          <div className="mt-6 rounded-2xl border border-solid border-[#e6eaf0] bg-white p-10">
            <YakEmpty title="尚无可展示的元数据概览" description="可先从数据源发现物理表，或进入采集任务核对范围">
              <Button type="primary" onClick={() => history.push('/data-metadata/collect')}>打开采集任务</Button>
            </YakEmpty>
          </div>
        ) : null}
      </Spin>
    </main>
  );
};

export default MetadataOverviewPage;
