import qualityEmptyIllustration from '@/assets/image/qulity.png';
import { pageTodo } from '@/services/approval/api';
import type { ApprovalTodoRow } from '@/services/approval/types';
import {
  getQualityAlertOverview,
  type QualityAlertEvent,
} from '@/services/data-quality/alert';
import {
  homeQualityOverviewApi,
  type HomeQualityDimension,
  type HomeQualityIssue,
  type HomeQualityOverview,
} from '@/services/home';
import { BRAND_COLOR } from '@/styles/brand';
import { history, useIntl } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { ChevronRight } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

interface QualityOverviewState {
  data?: HomeQualityOverview;
  loading: boolean;
  failed: boolean;
}

interface RadarDimensionDefinition {
  messageId: string;
  aliases: string[];
}

const RADAR_DIMENSIONS: RadarDimensionDefinition[] = [
  {
    messageId: 'pages.home.quality.dimension.completeness',
    aliases: ['完整性', 'completeness'],
  },
  {
    messageId: 'pages.home.quality.dimension.uniqueness',
    aliases: ['唯一性', 'uniqueness'],
  },
  {
    messageId: 'pages.home.quality.dimension.validity',
    aliases: ['有效性', 'validity'],
  },
  {
    messageId: 'pages.home.quality.dimension.accuracy',
    aliases: ['准确性', 'accuracy'],
  },
  {
    messageId: 'pages.home.quality.dimension.timeliness',
    aliases: ['时效性', '及时性', 'timeliness'],
  },
];

const formatMetric = (value: number | null | undefined, locale: string) => {
  if (value == null) return '--';
  return new Intl.NumberFormat(locale).format(value);
};

const formatRate = (value?: number | null) => {
  if (value == null) return '--';
  return value.toFixed(1);
};

const formatRateWithUnit = (value?: number | null) => {
  if (value == null) return '--';
  return `${value.toFixed(1)}%`;
};

const normalizeRadarDimensions = (
  dimensions: HomeQualityDimension[],
  resolveLabel: (messageId: string) => string,
): HomeQualityDimension[] =>
  RADAR_DIMENSIONS.map((definition) => {
    const matched = dimensions.find((item) => {
      const value = item.dimension.toLowerCase();
      return definition.aliases.some(
        (alias) => alias.toLowerCase() === value,
      );
    });

    return {
      dimension: resolveLabel(definition.messageId),
      total: matched?.total ?? 0,
      issues: matched?.issues ?? 0,
      passRate: matched?.passRate ?? null,
    };
  });

function useQualityOverview(): QualityOverviewState {
  const [state, setState] = useState<QualityOverviewState>({
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;

    homeQualityOverviewApi
      .overview()
      .then((response) => {
        if (!active) return;

        if (!response.data) {
          setState({ loading: false, failed: true });
          return;
        }

        setState({
          data: response.data,
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setState({ loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}

function relativeTime(value: string | null | undefined, locale: string) {
  if (!value) return '--';

  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return value;

  const diff = Math.max(0, Date.now() - timestamp);
  const minutes = Math.floor(diff / 60_000);
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });

  if (minutes < 1) return formatter.format(0, 'minute');
  if (minutes < 60) return formatter.format(-minutes, 'minute');

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return formatter.format(-hours, 'hour');

  const days = Math.floor(hours / 24);
  if (days < 7) return formatter.format(-days, 'day');

  return new Date(timestamp).toLocaleDateString(locale, {
    month: '2-digit',
    day: '2-digit',
  });
}

function getObjectLabel(issue: HomeQualityIssue) {
  return issue.objectName || issue.tableName || issue.monitorName || '--';
}

function getHealthStatus(passRate: number | null | undefined) {
  if (passRate == null) {
    return {
      messageId: 'pages.home.quality.health.noData',
      textClass: 'text-[#858b94]',
      dotClass: 'bg-[#c8ccd2]',
    };
  }

  if (passRate >= 95) {
    return {
      messageId: 'pages.home.quality.health.healthy',
      textClass: 'text-[#34855d]',
      dotClass: 'bg-[#53a675]',
    };
  }

  if (passRate >= 80) {
    return {
      messageId: 'pages.home.quality.health.attention',
      textClass: 'text-[#a76c24]',
      dotClass: 'bg-[#d99a45]',
    };
  }

  return {
    messageId: 'pages.home.quality.health.risky',
    textClass: 'text-[#c64e59]',
    dotClass: 'bg-[#df5a66]',
  };
}

function buildRadarOption(dimensions: HomeQualityDimension[]): EChartsOption {
  const hasData = dimensions.some((item) => item.passRate != null);
  const dimensionMap = new Map(
    dimensions.map((item) => [item.dimension, item]),
  );

  return {
    animation: hasData,
    animationDuration: 700,
    animationEasing: 'cubicOut',
    tooltip: hasData
      ? {
          trigger: 'item',
          borderWidth: 0,
          backgroundColor: 'rgba(32, 35, 43, 0.92)',
          textStyle: {
            color: '#ffffff',
            fontSize: 12,
          },
          padding: [9, 12],
          formatter: () =>
            dimensions
              .map(
                (item) =>
                  `${item.dimension}&nbsp;&nbsp;${formatRateWithUnit(item.passRate)}`,
              )
              .join('<br/>'),
        }
      : { show: false },
    radar: {
      center: ['50%', '52%'],
      radius: '66%',
      splitNumber: 4,
      indicator: dimensions.map((item) => ({
        name: item.dimension,
        max: 100,
      })),
      axisName: {
        color: '#565c66',
        fontSize: 11,
        fontWeight: 500,
        lineHeight: 18,
        formatter: (name: string) => {
          const dimension = dimensionMap.get(name);
          return `{name|${name}}\n{value|${formatRateWithUnit(dimension?.passRate)}}`;
        },
        rich: {
          name: {
            color: '#656b75',
            fontSize: 11,
            fontWeight: 500,
            lineHeight: 17,
          },
          value: {
            color: '#a0a5ad',
            fontSize: 9,
            fontWeight: 400,
            lineHeight: 14,
          },
        },
      },
      axisLine: {
        lineStyle: {
          color: '#e6e8ec',
          width: 1,
        },
      },
      splitLine: {
        lineStyle: {
          color: '#e5e8ec',
          width: 1,
        },
      },
      splitArea: {
        areaStyle: {
          color: ['#ffffff', '#fafbfc', '#ffffff', '#fafbfc'],
        },
      },
    },
    series: hasData
      ? [
          {
            type: 'radar',
            symbol: 'circle',
            symbolSize: 4,
            lineStyle: {
              width: 2,
              color: BRAND_COLOR,
            },
            itemStyle: {
              color: BRAND_COLOR,
              borderColor: '#ffffff',
              borderWidth: 1.5,
            },
            areaStyle: {
              color: 'rgba(254, 44, 85, 0.09)',
            },
            data: [
              {
                value: dimensions.map((item) => item.passRate ?? 0),
                name: 'Quality',
              },
            ],
          },
        ]
      : [],
  };
}

function LoadingLines() {
  return (
    <div className="divide-y divide-[#eef0f2]">
      {[0, 1, 2, 3].map((item) => (
        <div
          key={item}
          className="flex h-[68px] animate-pulse items-center gap-3"
        >
          <div className="h-2 w-2 rounded-full bg-[#eceef1]" />
          <div className="min-w-0 flex-1">
            <div className="h-3 w-[42%] rounded bg-[#eceef1]" />
            <div className="mt-2 h-2.5 w-[62%] rounded bg-[#f1f2f4]" />
          </div>
          <div className="h-2.5 w-12 rounded bg-[#f1f2f4]" />
        </div>
      ))}
    </div>
  );
}

function EmptyIssues({ failed }: { failed: boolean }) {
  const intl = useIntl();

  return (
    <div className="flex min-h-[325px] flex-col items-center justify-center">
      {failed ? (
        <div className="text-[32px] font-light leading-none text-[#d6d9de]">
          —
        </div>
      ) : (
        <img
          src={qualityEmptyIllustration}
          alt=""
          className="h-[180px] w-[180px] object-contain"
        />
      )}

      <strong
        className={`text-[12px] font-medium text-[#646a74] ${failed ? 'mt-3' : 'mt-2'}`}
      >
        {intl.formatMessage({
          id: failed
            ? 'pages.home.quality.issueFailed'
            : 'pages.home.quality.emptyIssues',
        })}
      </strong>
    </div>
  );
}

function QualityMetric({
  label,
  value,
  warning = false,
  locale,
}: {
  label: string;
  value?: number | null;
  warning?: boolean;
  locale: string;
}) {
  const hasWarning = warning && (value ?? 0) > 0;

  return (
    <div className="min-w-0">
      <div className="truncate text-[12px] leading-4 text-[#9398a1]">
        {label}
      </div>
      <strong
        className={`mt-0.5 block truncate text-[16px] font-semibold leading-6 tracking-[-0.3px] ${
          hasWarning ? 'text-[#d55360]' : 'text-[#343943]'
        }`}
      >
        {formatMetric(value, locale)}
      </strong>
    </div>
  );
}

function QualityRadar({ state }: { state: QualityOverviewState }) {
  const intl = useIntl();
  const data = state.data;

  const dimensions = useMemo(
    () =>
      normalizeRadarDimensions(data?.dimensions ?? [], (messageId) =>
        intl.formatMessage({ id: messageId }),
      ),
    [data?.dimensions, intl],
  );
  const option = useMemo(() => buildRadarOption(dimensions), [dimensions]);
  const health = getHealthStatus(data?.passRate);

  return (
    <div className="flex min-w-0 flex-col lg:pr-7">
      <div className="flex items-start justify-between gap-5">
        <div>
          <div className="text-[12px] font-medium text-[#858b94]">
            {intl.formatMessage({ id: 'pages.home.quality.overallPassRate' })}
          </div>

          <div className="mt-1 flex items-baseline gap-1">
            <strong className="text-[34px] font-semibold leading-[40px] tracking-[-1px] text-[#262a32]">
              {state.loading ? '--' : formatRate(data?.passRate)}
            </strong>
            {data?.passRate != null ? (
              <span className="text-[13px] font-medium text-[#7d838c]">%</span>
            ) : null}
          </div>

          <div
            className={`mt-1 flex items-center gap-1.5 text-[12px] font-medium ${health.textClass}`}
          >
            <span className={`h-1.5 w-1.5 rounded-full ${health.dotClass}`} />
            {intl.formatMessage({
              id: state.loading
                ? 'pages.home.quality.loading'
                : state.failed
                  ? 'pages.home.quality.failed'
                  : health.messageId,
            })}
          </div>
        </div>

        <span className="mt-1 shrink-0 rounded-full bg-[#f5f6f8] px-2.5 py-1 text-[12px] font-medium text-[#858b94]">
          {intl.formatMessage({ id: 'pages.home.quality.period7d' })}
        </span>
      </div>

      <div className="mt-1 min-h-[235px]">
        {state.failed ? (
          <div className="flex h-[235px] items-center justify-center text-[12px] text-[#9a9fa7]">
            {intl.formatMessage({
              id: 'pages.home.quality.dimensionUnavailable',
            })}
          </div>
        ) : (
          <ReactECharts
            option={option}
            notMerge
            lazyUpdate
            style={{ width: '100%', height: 235 }}
          />
        )}
      </div>

      <div className="grid grid-cols-3 gap-5 border-t border-[#eceef1] pt-3.5">
        <QualityMetric
          label={intl.formatMessage({
            id: 'pages.home.quality.metric.monitoredTables',
          })}
          value={data?.monitoredTableCount}
          locale={intl.locale}
        />
        <QualityMetric
          label={intl.formatMessage({
            id: 'pages.home.quality.metric.todayChecks',
          })}
          value={data?.todayExecutionCount}
          locale={intl.locale}
        />
        <QualityMetric
          label={intl.formatMessage({
            id: 'pages.home.quality.metric.enabledRules',
          })}
          value={data?.enabledRuleCount}
          locale={intl.locale}
        />
      </div>
    </div>
  );
}

function IssueRow({ issue }: { issue: HomeQualityIssue }) {
  const intl = useIntl();
  const executionError = issue.checkResult?.toUpperCase() === 'ERROR';
  const statusLabel = intl.formatMessage({
    id: executionError
      ? 'pages.home.quality.status.executionError'
      : 'pages.home.quality.status.failed',
  });

  return (
    <button
      type="button"
      onClick={() =>
        history.push(
          `/data-quality/execution/${encodeURIComponent(issue.executionNo)}`,
        )
      }
      className="group flex w-full min-w-0 items-center gap-3 border-0 bg-transparent py-[13px] text-left"
    >
      <span
        className={`h-8 w-[3px] shrink-0 rounded-full ${
          executionError ? 'bg-[#dfa04d]' : 'bg-[#df5d69]'
        }`}
      />

      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-2">
          <strong className="min-w-0 truncate text-[12px] font-medium leading-5 text-[#373c45] transition-colors group-hover:text-[#20242b]">
            {issue.ruleName}
          </strong>
          <span className="shrink-0 rounded-full bg-[#f3f4f6] px-2 py-[2px] text-[9px] font-medium leading-4 text-[#737983]">
            {issue.dimension}
          </span>
          <span
            className={`hidden shrink-0 text-[9px] font-medium sm:inline ${
              executionError ? 'text-[#b97b2f]' : 'text-[#cf5863]'
            }`}
          >
            {statusLabel}
          </span>
        </span>

        <span className="mt-0.5 flex min-w-0 items-center gap-1.5 text-[12px] leading-4 text-[#9499a2]">
          <span className="truncate">{getObjectLabel(issue)}</span>
          {issue.columnName ? (
            <>
              <span className="shrink-0 text-[#d1d4d9]">·</span>
              <span className="truncate">{issue.columnName}</span>
            </>
          ) : null}
        </span>
      </span>

      <span className="shrink-0 text-[9px] text-[#667085]">
        {relativeTime(issue.queuedAt, intl.locale)}
      </span>
      <span className="shrink-0 translate-x-[-2px] text-[17px] font-light leading-none text-[#c3c7cd] opacity-0 transition-all group-hover:translate-x-0 group-hover:opacity-100">
        ›
      </span>
    </button>
  );
}

function RecentIssues({ state }: { state: QualityOverviewState }) {
  const intl = useIntl();
  const data = state.data;
  const issues = (data?.recentIssues ?? []).slice(0, 4);
  const issueCount = data?.recentIssueCount;

  return (
    <div className="flex min-w-0 flex-col lg:border-l lg:border-[#eceef1] lg:pl-7">
      <div className="flex shrink-0 items-start justify-between gap-5">
        <div className="flex items-baseline gap-2">
          <h3 className="m-0 text-[15px] font-semibold tracking-[-0.2px] text-[#2b3038]">
            {intl.formatMessage({ id: 'pages.home.quality.pendingIssues' })}
          </h3>
          {(issueCount ?? 0) > 0 ? (
            <strong className="text-[13px] font-semibold text-[#d6525f]">
              {formatMetric(issueCount, intl.locale)}
            </strong>
          ) : null}
        </div>

        <div className="flex shrink-0 gap-6">
          <div className="text-right">
            <div className="text-[12px] leading-4 text-[#667085]">
              {intl.formatMessage({
                id: 'pages.home.quality.metric.todayIssueTables',
              })}
            </div>
            <strong
              className={`mt-0.5 block text-[15px] font-semibold leading-5 ${
                (data?.todayIssueTableCount ?? 0) > 0
                  ? 'text-[#d6525f]'
                  : 'text-[#3f444d]'
              }`}
            >
              {formatMetric(data?.todayIssueTableCount, intl.locale)}
            </strong>
          </div>

          <div className="text-right">
            <div className="text-[12px] leading-4 text-[#667085]">
              {intl.formatMessage({ id: 'pages.home.quality.issues7d' })}
            </div>
            <strong
              className={`mt-0.5 block text-[15px] font-semibold leading-5 ${
                (issueCount ?? 0) > 0 ? 'text-[#d6525f]' : 'text-[#3f444d]'
              }`}
            >
              {formatMetric(issueCount, intl.locale)}
            </strong>
          </div>
        </div>
      </div>

      <div className="mt-3 min-h-[268px] flex-1 border-t border-[#eceef1]">
        {state.loading ? (
          <LoadingLines />
        ) : issues.length > 0 ? (
          <div className="divide-y divide-[#eceef1]">
            {issues.map((issue) => (
              <IssueRow key={issue.id} issue={issue} />
            ))}
          </div>
        ) : (
          <EmptyIssues failed={state.failed} />
        )}
      </div>
    </div>
  );
}

function DataQualityPanel() {
  const intl = useIntl();
  const state = useQualityOverview();

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex items-start justify-between gap-6">
        <h2 className="m-0 text-[18px] font-semibold tracking-[-0.35px] text-[#252932]">
          {intl.formatMessage({ id: 'pages.home.quality.title' })}
        </h2>

        <button
          type="button"
          onClick={() => history.push('/data-quality/overview')}
          className="group flex shrink-0 items-center gap-1 border-0 bg-transparent p-0 text-[12px] font-medium text-[#7a8089] transition-colors hover:text-[#30353d]"
        >
          {intl.formatMessage({ id: 'pages.home.common.viewMore' })}
          <ChevronRight size={15} strokeWidth={1.9} />
        </button>
      </header>

      <div className="mt-4 pt-4">
        <div className="grid min-w-0 grid-cols-1 gap-7 lg:grid-cols-[minmax(300px,0.82fr)_minmax(460px,1.18fr)] lg:gap-0">
          <QualityRadar state={state} />
          <RecentIssues state={state} />
        </div>
      </div>
    </section>
  );
}

function WorkbenchPanel({
  title,
  count,
  countClass,
  onMore,
  children,
}: {
  title: string;
  count?: number | null;
  countClass?: string;
  onMore: () => void;
  children: React.ReactNode;
}) {
  const intl = useIntl();

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-4 pt-5">
      <header className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-baseline gap-2">
          <h2 className="m-0 truncate text-[16px] font-semibold tracking-[-0.3px] text-[#252932]">
            {title}
          </h2>
          {count != null ? (
            <strong
              className={`text-[13px] font-semibold ${countClass ?? 'text-[#3f444d]'}`}
            >
              {formatMetric(count, intl.locale)}
            </strong>
          ) : null}
        </div>

        <button
          type="button"
          onClick={onMore}
          className="group flex shrink-0 items-center gap-1 border-0 bg-transparent p-0 text-[12px] font-medium text-[#7a8089] transition-colors hover:text-[#30353d]"
        >
          {intl.formatMessage({ id: 'pages.home.common.viewMore' })}
          <ChevronRight size={15} strokeWidth={1.9} />
        </button>
      </header>

      <div className="mt-2 border-t border-[#eceef1]">{children}</div>
    </section>
  );
}

function WorkbenchLines() {
  return (
    <div className="divide-y divide-[#eef0f2]">
      {[0, 1, 2].map((item) => (
        <div
          key={item}
          className="flex h-[46px] animate-pulse items-center gap-3"
        >
          <div className="min-w-0 flex-1">
            <div className="h-3 w-[48%] rounded bg-[#eceef1]" />
            <div className="mt-1.5 h-2.5 w-[30%] rounded bg-[#f1f2f4]" />
          </div>
          <div className="h-2.5 w-10 rounded bg-[#f1f2f4]" />
        </div>
      ))}
    </div>
  );
}

function WorkbenchPlaceholder({
  failed,
  messageId,
}: {
  failed: boolean;
  messageId: string;
}) {
  const intl = useIntl();

  return (
    <div className="flex h-[150px] flex-col items-center justify-center">
      {failed ? (
        <div className="text-[26px] font-light leading-none text-[#d6d9de]">
          —
        </div>
      ) : null}
      <strong
        className={`text-[12px] font-medium text-[#9398a1] ${failed ? 'mt-2' : ''}`}
      >
        {intl.formatMessage({
          id: failed ? 'pages.home.common.unavailable' : messageId,
        })}
      </strong>
    </div>
  );
}

function TodoRow({ row }: { row: ApprovalTodoRow }) {
  const intl = useIntl();
  const { instance } = row;

  return (
    <button
      type="button"
      onClick={() => history.push(`/approval/instance/${instance.id}`)}
      className="group flex w-full min-w-0 items-center gap-3 border-0 bg-transparent py-[10px] text-left"
    >
      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-2">
          <strong className="min-w-0 truncate text-[12px] font-medium leading-5 text-[#373c45] transition-colors group-hover:text-[#20242b]">
            {instance.title || '--'}
          </strong>
          <span className="shrink-0 rounded-full bg-[#f3f4f6] px-2 py-[2px] text-[9px] font-medium leading-4 text-[#737983]">
            {instance.flowName || instance.flowCode}
          </span>
        </span>
        <span className="mt-0.5 block truncate text-[12px] leading-4 text-[#9499a2]">
          {instance.applicant}
        </span>
      </span>

      <span className="shrink-0 text-[9px] text-[#667085]">
        {relativeTime(instance.createTime, intl.locale)}
      </span>
      <span className="shrink-0 translate-x-[-2px] text-[17px] font-light leading-none text-[#c3c7cd] opacity-0 transition-all group-hover:translate-x-0 group-hover:opacity-100">
        ›
      </span>
    </button>
  );
}

function TodoPanel() {
  const intl = useIntl();
  const [state, setState] = useState<{
    rows: ApprovalTodoRow[];
    total: number;
    loading: boolean;
    failed: boolean;
  }>({ rows: [], total: 0, loading: true, failed: false });

  useEffect(() => {
    let active = true;

    pageTodo(1, 5)
      .then((page) => {
        if (!active) return;
        setState({
          rows: page.bizData ?? [],
          total: page.pagination?.total ?? 0,
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setState({ rows: [], total: 0, loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <WorkbenchPanel
      title={intl.formatMessage({ id: 'pages.home.workbench.todo.title' })}
      count={state.loading || state.failed ? null : state.total}
      countClass={state.total > 0 ? 'text-[#d6525f]' : undefined}
      onMore={() => history.push('/approval/todo')}
    >
      {state.loading ? (
        <WorkbenchLines />
      ) : state.failed ? (
        <WorkbenchPlaceholder failed messageId="" />
      ) : state.rows.length > 0 ? (
        <div className="divide-y divide-[#eceef1]">
          {state.rows.map((row) => (
            <TodoRow key={row.stepId} row={row} />
          ))}
        </div>
      ) : (
        <WorkbenchPlaceholder
          failed={false}
          messageId="pages.home.workbench.todo.empty"
        />
      )}
    </WorkbenchPanel>
  );
}

const ALERT_LEVEL_MESSAGE: Record<string, string> = {
  WARNING: 'pages.home.workbench.alert.level.warning',
  CRITICAL: 'pages.home.workbench.alert.level.critical',
};

// 投递状态是后端原样透传的字符串;未识别值直接展示原文,不假装归一。
const DELIVERY_MESSAGE: Record<string, string> = {
  RECORDED: 'pages.home.workbench.alert.delivery.recorded',
  PENDING: 'pages.home.workbench.alert.delivery.pending',
  FAILED: 'pages.home.workbench.alert.delivery.failed',
};

function AlertRow({ event }: { event: QualityAlertEvent }) {
  const intl = useIntl();
  const critical = event.alertLevel === 'CRITICAL';
  const target =
    event.monitorId != null
      ? `/data-quality/monitor/${event.monitorId}`
      : event.executionNo
        ? `/data-quality/execution/${encodeURIComponent(event.executionNo)}`
        : null;
  const levelMessageId = event.alertLevel
    ? ALERT_LEVEL_MESSAGE[event.alertLevel]
    : undefined;
  const deliveryMessageId = event.deliveryStatus
    ? DELIVERY_MESSAGE[event.deliveryStatus]
    : undefined;
  const deliveryLabel = event.deliveryStatus
    ? deliveryMessageId
      ? intl.formatMessage({ id: deliveryMessageId })
      : event.deliveryStatus
    : null;

  return (
    <button
      type="button"
      disabled={!target}
      onClick={() => target && history.push(target)}
      className="group flex w-full min-w-0 items-center gap-3 border-0 bg-transparent py-[10px] text-left disabled:cursor-default"
    >
      <span
        className={`h-8 w-[3px] shrink-0 rounded-full ${
          critical ? 'bg-[#df5d69]' : 'bg-[#dfa04d]'
        }`}
      />

      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-2">
          <strong className="min-w-0 truncate text-[12px] font-medium leading-5 text-[#373c45] transition-colors group-hover:text-[#20242b]">
            {event.monitorName || event.executionNo || '--'}
          </strong>
          <span
            className={`shrink-0 text-[9px] font-medium ${
              critical ? 'text-[#cf5863]' : 'text-[#b97b2f]'
            }`}
          >
            {levelMessageId
              ? intl.formatMessage({ id: levelMessageId })
              : (event.alertLevel ?? '--')}
          </span>
        </span>
        <span className="mt-0.5 flex min-w-0 items-center gap-1.5 text-[12px] leading-4 text-[#9499a2]">
          <span className="truncate">
            {event.alertMessage || event.executionNo || '--'}
          </span>
          {deliveryLabel ? (
            <>
              <span className="shrink-0 text-[#d1d4d9]">·</span>
              <span className="shrink-0">{deliveryLabel}</span>
            </>
          ) : null}
        </span>
      </span>

      <span className="shrink-0 text-[9px] text-[#667085]">
        {relativeTime(event.createdAt, intl.locale)}
      </span>
      {target ? (
        <span className="shrink-0 translate-x-[-2px] text-[17px] font-light leading-none text-[#c3c7cd] opacity-0 transition-all group-hover:translate-x-0 group-hover:opacity-100">
          ›
        </span>
      ) : null}
    </button>
  );
}

function AlertPanel() {
  const intl = useIntl();
  const [state, setState] = useState<{
    last24hCount: number;
    recent: QualityAlertEvent[];
    loading: boolean;
    failed: boolean;
  }>({ last24hCount: 0, recent: [], loading: true, failed: false });

  useEffect(() => {
    let active = true;

    getQualityAlertOverview(5)
      .then((overview) => {
        if (!active) return;
        setState({
          last24hCount: overview.last24hCount ?? 0,
          recent: overview.recent ?? [],
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setState({ last24hCount: 0, recent: [], loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <WorkbenchPanel
      title={intl.formatMessage({ id: 'pages.home.workbench.alert.title' })}
      count={state.loading || state.failed ? null : state.last24hCount}
      countClass={state.last24hCount > 0 ? 'text-[#d6525f]' : undefined}
      onMore={() => history.push('/data-quality/overview')}
    >
      {state.loading ? (
        <WorkbenchLines />
      ) : state.failed ? (
        <WorkbenchPlaceholder failed messageId="" />
      ) : state.recent.length > 0 ? (
        <div className="divide-y divide-[#eceef1]">
          {state.recent.map((event) => (
            <AlertRow key={event.id} event={event} />
          ))}
        </div>
      ) : (
        <WorkbenchPlaceholder
          failed={false}
          messageId="pages.home.workbench.alert.empty"
        />
      )}
    </WorkbenchPanel>
  );
}

export function HomeWorkbenchMain() {
  return (
    <>
      <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-2">
        <TodoPanel />
        <AlertPanel />
      </div>
      <DataQualityPanel />
    </>
  );
}

export default function HomeWorkbench() {
  return <HomeWorkbenchMain />;
}
