import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { homeQualityOverviewApi, type HomeCockpitHeaderStats } from '@/services/home';
import { API_SUCCESS_CODE } from '@/services/http/response';
import { pageModelingModels } from '@/services/modeling/api';
import { getMetricStats } from '@/services/metric/api';
import { history, useModel } from '@umijs/max';
import { ArrowUpRight, Clock3 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { readSharedHomeAssetOverview } from '../hooks/readSharedHomeAssetOverview';
import {
  canReadHomePulse, HOME_PULSE_ROUTES, pulseScopeKey, safeOwnerCount,
  type DynamicHomePulseKey, type HomePulseKey,
} from './projectPulseContract';
import { appRoutes } from '@/config/navigation';

type LoadState = { status: 'loading' | 'ready' | 'unavailable'; value: number | null };
type PulseSnapshot = {
  scope: string | null;
  models: LoadState;
  metrics: LoadState;
  assets: LoadState;
  quality: LoadState;
};

const pending = (): LoadState => ({ status: 'loading', value: null });
const initialSnapshot = (scope: string | null): PulseSnapshot => ({
  scope, models: pending(), metrics: pending(), assets: pending(), quality: pending(),
});
const routeById = (id: string) => appRoutes.find((route) => route.id === id);

function formatPulse(value: LoadState, failedLabel = '暂不可用') {
  if (value.status === 'loading') return '读取中';
  if (value.status === 'unavailable') return failedLabel;
  return value.value == null ? '—' : value.value.toLocaleString('zh-CN');
}

function OwnerPulseCard({
  label, detail, value, target, clickable,
}: {
  label: string; detail: string; value: LoadState; target?: string; clickable?: boolean;
}) {
  const content = (
    <>
      <span className="block text-[12px] font-medium text-[#627985]">{label}</span>
      <strong className="mt-2 block truncate text-[22px] font-semibold tabular-nums leading-7 text-[#243d46]">
        {formatPulse(value)}
      </strong>
      <span className="mt-2 block text-[11px] leading-4 text-[#83929b]">{detail}</span>
      {clickable ? <ArrowUpRight size={15} className="absolute right-3 top-3 text-[#9bacae]" aria-hidden="true" /> : null}
    </>
  );
  const classes = 'relative min-w-0 rounded-xl border border-[#e3eaea] bg-[#fafcfc] p-3 text-left sm:p-4';
  return target && clickable ? (
    <button
      type="button"
      onClick={() => history.push(target)}
      className={`${classes} transition-colors hover:border-[#85bcb3] hover:bg-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#16877d]`}
      aria-label={`${label}：${formatPulse(value)}，前往对应专业页面`}
    >
      {content}
    </button>
  ) : <div className={classes}>{content}</div>;
}

/**
 * Six independent views of current Project facts. All reads keep their Owner
 * and permission; a rejected/unavailable Provider never becomes numeric zero.
 * Old-project responses cannot commit into the new project's visible snapshot.
 */
export default function HomeProjectPulse({
  stats, cockpitLoading, cockpitFailed,
}: {
  stats?: HomeCockpitHeaderStats;
  cockpitLoading: boolean;
  cockpitFailed: boolean;
}) {
  const { currentProject } = useSecurityProject();
  const { initialState } = useModel('@@initialState');
  const projectId = currentProject?.id == null ? undefined : String(currentProject.id);
  const user = initialState?.currentUser;
  const permissions = user?.permissionCodes;
  const menus = user?.menuCodes;
  const scope = pulseScopeKey(projectId, user?.userid, permissions, menus);

  const allowed = {
    sources: Boolean(scope && canReadHomePulse('sources', permissions, menus)),
    models: Boolean(scope && canReadHomePulse('models', permissions, menus)),
    metrics: Boolean(scope && canReadHomePulse('metrics', permissions, menus)),
    assets: Boolean(scope && canReadHomePulse('assets', permissions, menus)),
    quality: Boolean(scope && canReadHomePulse('quality', permissions, menus)),
  };

  const [snapshot, setSnapshot] = useState<PulseSnapshot>(() => initialSnapshot(null));

  useEffect(() => {
    if (!scope || !projectId) {
      setSnapshot(initialSnapshot(null));
      return;
    }
    let active = true;
    setSnapshot(initialSnapshot(scope));
    const report = (key: DynamicHomePulseKey, result: LoadState) => {
      if (!active) return;
      setSnapshot((previous) => previous.scope === scope
        ? { ...previous, [key]: result } : previous);
    };
    const read = <T,>(key: DynamicHomePulseKey, promise: Promise<T>, select: (value: T) => unknown) => {
      void promise.then((result) => {
        const count = safeOwnerCount(select(result));
        report(key, { status: 'ready', value: count });
      }).catch(() => report(key, { status: 'unavailable', value: null }));
    };

    if (allowed.models) read(
      'models', pageModelingModels({ pageNo: 1, pageSize: 1 }),
      (result) => result?.pagination?.total,
    );
    if (allowed.metrics) read('metrics', getMetricStats(), (result) => result?.total);
    if (allowed.assets) read(
      'assets', readSharedHomeAssetOverview(scope),
      (result) => result?.code === API_SUCCESS_CODE ? result?.data?.dataset?.tableAssetCount : null,
    );
    if (allowed.quality) read(
      'quality', homeQualityOverviewApi.overview(),
      (result) => result?.code === API_SUCCESS_CODE
        ? result?.data?.todayIssueTableCount : null,
    );
    return () => { active = false; };
  }, [scope, projectId, allowed.models, allowed.metrics, allowed.assets, allowed.quality]);

  if (!scope) return null;
  const effective = snapshot.scope === scope ? snapshot : initialSnapshot(scope);
  const homeValue = (value: unknown, available?: boolean): LoadState => {
    if (cockpitFailed || available === false) return { status: 'unavailable', value: null };
    if (cockpitLoading) return pending();
    return { status: 'ready', value: safeOwnerCount(value) };
  };

  const values: Record<HomePulseKey, LoadState> = {
    sources: homeValue(stats?.dataSourceCount, stats?.dataSourceAvailable),
    models: effective.models,
    metrics: effective.metrics,
    assets: effective.assets,
    quality: effective.quality,
    running: homeValue(stats?.runningCount, stats?.runningAvailable),
  };

  const cards: Array<{ key: HomePulseKey; label: string; detail: string }> = [
    { key: 'sources', label: '数据源', detail: 'Home / DataSource' },
    { key: 'models', label: '物理模型', detail: 'Modeling 模型总数' },
    { key: 'metrics', label: '指标定义', detail: 'Metric 定义总数，非发布数' },
    { key: 'assets', label: '表资产', detail: 'Asset 表资产，不代表已上架' },
    { key: 'quality', label: '今日质量异常表', detail: 'Quality 异常表数' },
    { key: 'running', label: '运行中任务', detail: 'Home Cockpit 汇总，非统一 Run' },
  ];

  const visible = cards.filter(({ key }) => key === 'running' || allowed[key]);
  if (!visible.length) return null;

  return (
    <section aria-labelledby="home-project-pulse-title" className="mt-4 min-w-0 rounded-[22px] border border-[#e5ebec] bg-white p-4 sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 id="home-project-pulse-title" className="m-0 text-[17px] font-semibold text-[#253b44]">项目规模与运行概况</h2>
          <p className="mb-0 mt-1 text-[11px] text-[#87959d]">当前项目 · 各卡片独立读取所属领域事实，未知和不可用不会计为零。</p>
        </div>
        <span className="inline-flex items-center gap-1 text-[11px] text-[#87959d]"><Clock3 size={13} aria-hidden="true" />实时读模型</span>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-6">
        {visible.map(({ key, label, detail }) => {
          const route = key === 'running' ? undefined : routeById(HOME_PULSE_ROUTES[key]);
          return (
            <OwnerPulseCard
              key={key} label={label} detail={detail} value={values[key]}
              target={route?.path} clickable={Boolean(route)}
            />
          );
        })}
      </div>
    </section>
  );
}
