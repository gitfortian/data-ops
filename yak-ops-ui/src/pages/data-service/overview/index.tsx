import YakButton from '@/components/YakButton';
import {
  getDataServiceOverview,
  type DataServiceOverview,
  type DataServiceOverviewFailure,
  type DataServiceOverviewRange,
} from '@/services/data-service';
import { Button, Empty, Table, Tooltip, message, type TableColumnsType } from 'antd';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

const BRAND_COLOR = 'rgba(254,44,85,1)';
const TEXT_PRIMARY = '#161823';
const TEXT_SECONDARY = '#667085';
const TEXT_WEAK = '#98a2b3';
const BORDER = '#f0f0f0';

const formatTime = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 19) : '-';
const formatNumber = (value?: number) => new Intl.NumberFormat('zh-CN').format(value || 0);

const RANGE_ITEMS: Array<{ value: DataServiceOverviewRange; label: string }> = [
  { value: '24h', label: '24 小时' },
  { value: '7d', label: '7 天' },
  { value: '30d', label: '30 天' },
];

const emptyOverview = (range: DataServiceOverviewRange): DataServiceOverview => ({
  range,
  startTime: '',
  endTime: '',
  apiTotal: 0,
  runningApis: 0,
  stoppedApis: 0,
  totalCalls: 0,
  successCalls: 0,
  failureCalls: 0,
  successRate: 0,
  averageDurationMs: 0,
  totalRows: 0,
  trend: [],
  hotApis: [],
  recentFailures: [],
});

const Panel = ({
  title,
  subtitle,
  children,
  className = '',
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  className?: string;
}) => (
  <section className={`rounded-[8px] border border-[#f0f0f0] bg-white ${className}`}>
    <div className="flex min-h-[48px] items-center justify-between border-b border-[#f3f4f5] px-4">
      <div className="min-w-0">
        <div className="text-[13px] font-semibold text-[#30323b]">{title}</div>
        {subtitle ? <div className="mt-0.5 text-[10px] text-[#a0a5ad]">{subtitle}</div> : null}
      </div>
    </div>
    {children}
  </section>
);

const DonutPanel = ({
  title,
  subtitle,
  total,
  items,
}: {
  title: string;
  subtitle: string;
  total: number;
  items: Array<{ name: string; value: number; color: string }>;
}) => {
  const option: EChartsOption = {
    animationDuration: 350,
    tooltip: {
      trigger: 'item',
      backgroundColor: '#fff',
      borderColor: '#e4e7ec',
      textStyle: { color: '#475467', fontSize: 11 },
      formatter: '{b}<br/>{c} ({d}%)',
    },
    series: [
      {
        type: 'pie',
        radius: ['60%', '78%'],
        center: ['50%', '49%'],
        silent: false,
        avoidLabelOverlap: true,
        label: { show: false },
        labelLine: { show: false },
        emphasis: { scale: false },
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        data: items.map((item) => ({
          name: item.name,
          value: item.value,
          itemStyle: { color: item.color },
        })),
      },
    ],
  };

  return (
    <Panel title={title} subtitle={subtitle}>
      <div className="px-4 pb-3 pt-2">
        <div className="relative h-[148px]">
          <ReactECharts option={option} style={{ height: 148 }} notMerge lazyUpdate />
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center pt-1">
            <div className="text-[22px] font-semibold tracking-[-.02em] text-[#161823]">{formatNumber(total)}</div>
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">总计</div>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-2 border-t border-[#f3f4f5] pt-3">
          {items.map((item) => (
            <div key={item.name} className="flex min-w-0 items-center justify-between gap-2 text-[11px]">
              <div className="flex min-w-0 items-center gap-1.5 text-[#667085]">
                <span className="h-1.5 w-1.5 shrink-0 rounded-full" style={{ backgroundColor: item.color }} />
                <span className="truncate">{item.name}</span>
              </div>
              <span className="shrink-0 font-medium text-[#344054]">{formatNumber(item.value)}</span>
            </div>
          ))}
        </div>
      </div>
    </Panel>
  );
};

const MetricCell = ({ label, value, note }: { label: string; value: string; note: string }) => (
  <div className="min-w-0 px-4 py-4">
    <div className="text-[10px] text-[#98a2b3]">{label}</div>
    <div className="mt-1.5 truncate text-[22px] font-semibold tracking-[-.02em] text-[#161823]">{value}</div>
    <div className="mt-1 truncate text-[10px] text-[#a0a5ad]">{note}</div>
  </div>
);

export default function DataServiceOverviewPage() {
  const [range, setRange] = useState<DataServiceOverviewRange>('24h');
  const [overview, setOverview] = useState<DataServiceOverview>(() => emptyOverview('24h'));
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getDataServiceOverview(range);
      setOverview(data || emptyOverview(range));
    } catch (error: any) {
      message.error(error?.message || '加载数据服务运行概览失败');
      setOverview(emptyOverview(range));
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => { void load(); }, [load]);

  const trendOption = useMemo<EChartsOption>(() => ({
    animationDuration: 350,
    color: ['#344054', BRAND_COLOR, '#98a2b3'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#e4e7ec',
      padding: [8, 10],
      textStyle: { color: '#475467', fontSize: 11 },
    },
    legend: {
      top: 0,
      right: 4,
      itemWidth: 14,
      itemHeight: 7,
      textStyle: { color: TEXT_SECONDARY, fontSize: 10 },
      data: ['总调用', '失败调用', '平均耗时'],
    },
    grid: { left: 44, right: 54, top: 44, bottom: 30, containLabel: false },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: overview.trend.map((item) => item.time),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e4e7ec' } },
      axisLabel: { color: TEXT_WEAK, fontSize: 9, hideOverlap: true, margin: 11 },
    },
    yAxis: [
      {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: TEXT_WEAK, fontSize: 9 },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: '#f2f4f7' } },
      },
      {
        type: 'value',
        axisLabel: { color: TEXT_WEAK, fontSize: 9, formatter: '{value} ms' },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '总调用',
        type: 'line',
        smooth: 0.25,
        symbol: 'none',
        lineStyle: { width: 2, color: '#344054' },
        areaStyle: { color: 'rgba(52,64,84,.06)' },
        data: overview.trend.map((item) => item.calls),
      },
      {
        name: '失败调用',
        type: 'line',
        smooth: 0.25,
        symbol: 'none',
        lineStyle: { width: 1.6, color: BRAND_COLOR },
        data: overview.trend.map((item) => item.failureCalls),
      },
      {
        name: '平均耗时',
        type: 'line',
        yAxisIndex: 1,
        smooth: 0.25,
        symbol: 'none',
        lineStyle: { width: 1.4, type: 'dashed', color: '#98a2b3' },
        data: overview.trend.map((item) => item.averageDurationMs),
      },
    ],
  }), [overview.trend]);

  const hotApiOption = useMemo<EChartsOption>(() => {
    const records = [...overview.hotApis].reverse();
    return {
      animationDuration: 350,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: '#fff',
        borderColor: '#e4e7ec',
        textStyle: { color: '#475467', fontSize: 11 },
      },
      grid: { left: 12, right: 42, top: 8, bottom: 8, containLabel: true },
      xAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { show: false },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
      },
      yAxis: {
        type: 'category',
        data: records.map((item) => item.name),
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: {
          color: TEXT_SECONDARY,
          fontSize: 10,
          width: 110,
          overflow: 'truncate',
        },
      },
      series: [
        {
          type: 'bar',
          barWidth: 10,
          data: records.map((item) => item.calls),
          itemStyle: { color: '#667085', borderRadius: [0, 3, 3, 0] },
          label: {
            show: true,
            position: 'right',
            color: TEXT_WEAK,
            fontSize: 9,
            formatter: '{c}',
          },
        },
      ],
    };
  }, [overview.hotApis]);

  const failureColumns: TableColumnsType<DataServiceOverviewFailure> = [
    {
      title: 'API',
      dataIndex: 'serviceName',
      minWidth: 170,
      render: (_, record) => (
        <div className="min-w-0 py-0.5">
          <div className="truncate text-[12px] font-medium text-[#344054]">{record.serviceName}</div>
          <div className="mt-0.5 truncate font-mono text-[10px] text-[#98a2b3]">{record.servicePath}</div>
        </div>
      ),
    },
    {
      title: '错误',
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (value?: string | null) => value
        ? <Tooltip title={value}><span className="text-[11px] text-[#b42318]">{value}</span></Tooltip>
        : <span className="text-[11px] text-[#98a2b3]">未知错误</span>,
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 82,
      render: (value: number) => <span className="text-[11px] text-[#667085]">{value || 0} ms</span>,
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 150,
      render: (value?: string | null) => <span className="text-[10px] text-[#98a2b3]">{formatTime(value)}</span>,
    },
  ];

  return (
    <div className="h-full overflow-y-auto bg-[#f6f7f8] p-3">
      <div className="min-h-full rounded-[10px] bg-white px-5 pb-5 pt-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="m-0 text-[17px] font-semibold text-[#161823]">运行概览</h1>
            <div className="mt-1 text-[12px] text-[#98a2b3]">
              API 运行状态、调用趋势与近期异常
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1 rounded-[8px] bg-[#f5f5f6] p-1">
              {RANGE_ITEMS.map((item) => {
                const active = range === item.value;
                return (
                  <button
                    key={item.value}
                    type="button"
                    onClick={() => setRange(item.value)}
                    className={[
                      'h-7 rounded-[6px] border-0 px-3 text-[12px] transition-all',
                      active
                        ? 'bg-white font-medium text-[#161823] shadow-[0_1px_3px_rgba(16,24,40,.06)]'
                        : 'bg-transparent text-[#7b808a] hover:text-[#344054]',
                    ].join(' ')}
                  >
                    {item.label}
                  </button>
                );
              })}
            </div>
            <YakButton
              type="text"
              size="middle"
              loading={loading}
              icon={<RefreshCw size={14} />}
              onClick={() => void load()}
              className="bg-[#f5f6f7]"
            >
              刷新
            </YakButton>
          </div>
        </div>

        <div className="my-4 border-t border-[#f0f0f0]" />

        <div className="grid grid-cols-1 gap-3 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(360px,1.2fr)]">
          <DonutPanel
            title="API 状态分布"
            subtitle="当前已上线的数据服务"
            total={overview.apiTotal}
            items={[
              { name: '运行中', value: overview.runningApis, color: '#475467' },
              { name: '已停用', value: overview.stoppedApis, color: '#d0d5dd' },
            ]}
          />
          <DonutPanel
            title="调用结果分布"
            subtitle={`统计范围：${RANGE_ITEMS.find((item) => item.value === range)?.label || range}`}
            total={overview.totalCalls}
            items={[
              { name: '成功', value: overview.successCalls, color: '#475467' },
              { name: '失败', value: overview.failureCalls, color: BRAND_COLOR },
            ]}
          />
          <Panel title="总体指标" subtitle="当前时间范围内的调用汇总">
            <div className="grid grid-cols-2 divide-x divide-y divide-[#f3f4f5]">
              <MetricCell label="调用次数" value={formatNumber(overview.totalCalls)} note="全部 API 调用" />
              <MetricCell label="成功率" value={`${overview.successRate}%`} note={`${overview.failureCalls} 次失败`} />
              <MetricCell label="平均耗时" value={`${formatNumber(overview.averageDurationMs)} ms`} note="按全部调用计算" />
              <MetricCell label="返回行数" value={formatNumber(overview.totalRows)} note="成功查询返回数据量" />
            </div>
          </Panel>
        </div>

        <Panel
          title="调用趋势"
          subtitle="总调用、失败调用与平均耗时"
          className="mt-3"
        >
          <div className="h-[330px] px-3 pb-2 pt-3">
            <ReactECharts option={trendOption} style={{ height: 310 }} notMerge lazyUpdate />
          </div>
        </Panel>

        <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-[minmax(360px,.8fr)_minmax(0,1.4fr)]">
          <Panel title="热门 API" subtitle="按当前时间范围内调用次数排序">
            {overview.hotApis.length ? (
              <div className="h-[300px] px-2 py-3">
                <ReactECharts option={hotApiOption} style={{ height: 276 }} notMerge lazyUpdate />
              </div>
            ) : (
              <div className="flex h-[300px] items-center justify-center">
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无调用数据" />
              </div>
            )}
          </Panel>

          <Panel title="最近失败" subtitle="优先关注最近的异常调用">
            <div className="p-3">
              <Table<DataServiceOverviewFailure>
                rowKey="id"
                size="small"
                bordered
                loading={loading}
                pagination={false}
                dataSource={overview.recentFailures}
                columns={failureColumns}
                scroll={{ x: 700, y: 240 }}
                locale={{ emptyText: '当前时间范围内没有失败调用' }}
              />
            </div>
          </Panel>
        </div>
      </div>
    </div>
  );
}
