import YakTab from '@/components/YakTab';
import type { DashboardSummary } from '@/services/dashboard';
import type { DataServiceApi } from '@/services/data-service';
import type { DigitalScreenInstance } from '@/services/digital-screen';
import type { HomeAssetDatasetItem } from '@/services/home';
import { history, useIntl } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';

import { useHomeDataAssets } from '../hooks/useHomeDataAssets';

type CenterTabKey = 'dashboard' | 'screen';

interface AssetItem {
  key: string;
  title: string;
  description: string;
  code: string;
  path: string;
}

interface AssetListProps {
  items: AssetItem[];
  loading: boolean;
  failed: boolean;
  emptyText: string;
  viewAllPath: string;
}

const MAX_VISIBLE_ITEMS = 5;
const EXIT_DURATION = 90;
const ENTER_DURATION = 200;

const DATASET_LIST_PATH = '/dataset';
const DASHBOARD_LIST_PATH = '/dashboard';
const DIGITAL_SCREEN_LIST_PATH = '/digital-screen';
const DATA_SERVICE_LIST_PATH = '/data-service';

const THUMBNAIL_CLASSES = [
  'bg-[#eef4ff]',
  'bg-[#f1f8ed]',
  'bg-[#fff4e8]',
  'bg-[#f5efff]',
  'bg-[#edf8f7]',
] as const;

const getRankClassName = (index: number) => {
  switch (index) {
    case 0:
      return 'bg-[#ff3d5f] text-white';
    case 1:
      return 'bg-[#ff7a1a] text-white';
    case 2:
      return 'bg-[#f5b700] text-white';
    default:
      return 'bg-[#a4a8b0] text-white';
  }
};

const formatRelativeTime = (value?: string | null, locale = 'zh-CN') => {
  if (!value) return '--';
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return value;

  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
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
};

const joinDescription = (...values: Array<string | null | undefined>) => {
  const parts = values
    .map((value) => value?.trim())
    .filter((value): value is string => Boolean(value && value !== '--'));
  return parts.join(' · ') || '--';
};

const dashboardOpenPath = (dashboard: DashboardSummary) =>
  Number(dashboard.publishedVersionNo) > 0
    ? `/dashboard/${encodeURIComponent(dashboard.id)}`
    : `/dashboard/${encodeURIComponent(dashboard.id)}/edit`;

const digitalScreenOpenPath = (screen: DigitalScreenInstance) =>
  screen.status === 'published'
    ? `/digital-screen/${encodeURIComponent(screen.id)}`
    : `/digital-screen/${encodeURIComponent(screen.id)}/edit`;

function StaticSectionHeader({ title }: { title: string }) {
  return (
    <div className="relative flex h-[35px] shrink-0 border-b border-[#eceef2]">
      <div className="relative flex h-full items-start pb-2 text-[13px] font-semibold text-[#292c35]">
        {title}
        <span className="absolute bottom-[-1px] left-0 right-0 h-[3px] bg-[#252832]" />
      </div>
    </div>
  );
}

function AssetList({
  items,
  loading,
  failed,
  emptyText,
  viewAllPath,
}: AssetListProps) {
  const intl = useIntl();
  const visibleItems = items.slice(0, MAX_VISIBLE_ITEMS);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="mt-2 flex-1">
        {loading ? (
          <div className="flex min-h-[350px] items-center justify-center text-[12px] text-[#9ca0a8]">
            {intl.formatMessage({ id: 'pages.home.common.loading' })}
          </div>
        ) : failed ? (
          <div className="flex min-h-[350px] items-center justify-center text-[12px] text-[#9ca0a8]">
            {intl.formatMessage({ id: 'pages.home.common.loadFailed' })}
          </div>
        ) : visibleItems.length === 0 ? (
          <div className="flex min-h-[350px] items-center justify-center text-[12px] text-[#9ca0a8]">
            {emptyText}
          </div>
        ) : (
          visibleItems.map((item, index) => (
            <button
              key={item.key}
              type="button"
              onClick={() => history.push(item.path)}
              className="group flex w-full min-w-0 items-center gap-3 border-0 bg-transparent py-[9px] text-left"
            >
              <span
                className={[
                  'relative flex h-[52px] w-[52px] shrink-0 items-center justify-center',
                  'overflow-hidden rounded-[8px]',
                  THUMBNAIL_CLASSES[index % THUMBNAIL_CLASSES.length],
                ].join(' ')}
              >
                <span className="text-[12px] font-semibold tracking-[0.4px] text-[#5d6470]">
                  {item.code}
                </span>
                <span
                  className={[
                    'absolute left-0 top-0 flex h-[17px] min-w-[17px]',
                    'items-center justify-center px-1',
                    'rounded-br-[5px] text-[10px] font-semibold leading-none',
                    getRankClassName(index),
                  ].join(' ')}
                >
                  {index + 1}
                </span>
              </span>

              <span className="min-w-0 flex-1">
                <span className="block truncate text-[13px] font-medium leading-5 text-[#292d36] transition-colors group-hover:text-[#111318]">
                  {item.title}
                </span>
                <span className="mt-1 block truncate text-[12px] leading-5 text-[#858a93]">
                  {item.description}
                </span>
              </span>
            </button>
          ))
        )}
      </div>

      <button
        type="button"
        onClick={() => history.push(viewAllPath)}
        className="mx-auto mt-2 flex h-7 shrink-0 items-center gap-1 border-0 bg-transparent text-[12px] text-[#7b8089] transition-colors hover:text-[#252832]"
      >
        {intl.formatMessage({ id: 'pages.home.common.viewMore' })}
        <span className="text-[16px] leading-none">›</span>
      </button>
    </div>
  );
}

export default function HomeDataAssets() {
  const intl = useIntl();
  const { datasets, dashboards, screens, services } = useHomeDataAssets();
  const [activeCenterTab, setActiveCenterTab] =
    useState<CenterTabKey>('dashboard');
  const [renderedCenterTab, setRenderedCenterTab] =
    useState<CenterTabKey>('dashboard');
  const [contentVisible, setContentVisible] = useState(true);
  const switchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const animationFrameRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (switchTimerRef.current) clearTimeout(switchTimerRef.current);
      if (animationFrameRef.current !== null) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, []);

  const handleCenterTabChange = (key: string) => {
    const nextTab = key as CenterTabKey;
    if (nextTab === activeCenterTab) return;

    setActiveCenterTab(nextTab);

    if (switchTimerRef.current) {
      clearTimeout(switchTimerRef.current);
      switchTimerRef.current = null;
    }
    if (animationFrameRef.current !== null) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }

    setContentVisible(false);
    switchTimerRef.current = setTimeout(() => {
      setRenderedCenterTab(nextTab);
      animationFrameRef.current = requestAnimationFrame(() => {
        setContentVisible(true);
        animationFrameRef.current = null;
      });
      switchTimerRef.current = null;
    }, EXIT_DURATION);
  };

  const datasetItems: AssetItem[] = datasets.items.map(
    (item: HomeAssetDatasetItem) => {
      const normalizedStatus = item.status?.toUpperCase();
      const status =
        normalizedStatus === 'ONLINE'
          ? intl.formatMessage({ id: 'pages.home.dataset.status.online' })
          : normalizedStatus === 'OFFLINE'
            ? intl.formatMessage({ id: 'pages.home.dataset.status.offline' })
            : item.status ||
              intl.formatMessage({ id: 'pages.home.dataset.status.unknown' });

      return {
        key: `dataset-${item.id}`,
        title: item.name,
        description: joinDescription(
          status,
          formatRelativeTime(item.updatedAt, intl.locale),
        ),
        code: 'DS',
        path: `/dataset/${encodeURIComponent(item.id)}`,
      };
    },
  );

  const dashboardItems: AssetItem[] = dashboards.items.map((item) => ({
    key: `dashboard-${item.id}`,
    title: item.name,
    description: joinDescription(
      item.description || `V${item.currentVersionNo || 0}`,
      formatRelativeTime(item.updateTime || item.createTime, intl.locale),
    ),
    code: 'BI',
    path: dashboardOpenPath(item),
  }));

  const screenItems: AssetItem[] = screens.items.map((item) => {
    const revision =
      item.status === 'published' && item.publishedVersionNo
        ? `V${item.publishedVersionNo}`
        : item.revision
          ? `R${item.revision}`
          : undefined;

    return {
      key: `screen-${item.id}`,
      title: item.name,
      description: joinDescription(
        item.description || revision,
        formatRelativeTime(item.updatedAt, intl.locale),
      ),
      code: 'SC',
      path: digitalScreenOpenPath(item),
    };
  });

  const serviceItems: AssetItem[] = services.items.map(
    (item: DataServiceApi) => ({
      key: `service-${item.id}`,
      title: item.name,
      description: joinDescription(
        item.path,
        formatRelativeTime(item.updateTime || item.createTime, intl.locale),
      ),
      code: 'API',
      path: `/data-service/api/${encodeURIComponent(String(item.id))}`,
    }),
  );

  const centerState =
    renderedCenterTab === 'dashboard' ? dashboards : screens;
  const centerItems =
    renderedCenterTab === 'dashboard' ? dashboardItems : screenItems;
  const centerEmptyText = intl.formatMessage({
    id:
      renderedCenterTab === 'dashboard'
        ? 'pages.home.dataAssets.dashboardEmpty'
        : 'pages.home.dataAssets.screenEmpty',
  });
  const centerViewAllPath =
    renderedCenterTab === 'dashboard'
      ? DASHBOARD_LIST_PATH
      : DIGITAL_SCREEN_LIST_PATH;

  return (
    <section className="flex h-full min-h-[520px] min-w-0 flex-col rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="shrink-0">
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.home.dataAssets.title' })}
        </h2>
      </header>

      <div className="mt-4 grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-3">
        <div className="flex min-w-0 flex-col pb-5 lg:pb-0 lg:pr-6">
          <StaticSectionHeader
            title={intl.formatMessage({ id: 'pages.home.dataAssets.dataset' })}
          />
          <AssetList
            items={datasetItems}
            loading={datasets.loading}
            failed={datasets.failed}
            emptyText={intl.formatMessage({
              id: 'pages.home.dataAssets.datasetEmpty',
            })}
            viewAllPath={DATASET_LIST_PATH}
          />
        </div>

        <div className="flex min-w-0 flex-col border-t border-[#eceef2] py-5 lg:border-l lg:border-t-0 lg:px-6 lg:py-0">
          <YakTab
            className="shrink-0"
            activeKey={activeCenterTab}
            onChange={handleCenterTabChange}
            items={[
              {
                key: 'dashboard',
                label: intl.formatMessage({
                  id: 'pages.home.dataAssets.dashboardTab',
                }),
              },
              {
                key: 'screen',
                label: intl.formatMessage({
                  id: 'pages.home.dataAssets.screenTab',
                }),
              },
            ]}
          />

          <div
            className={[
              'flex min-h-0 flex-1 flex-col',
              'transition-[opacity,transform]',
              'ease-[cubic-bezier(0.16,1,0.3,1)]',
              contentVisible
                ? 'translate-y-0 opacity-100'
                : 'translate-y-1 opacity-0',
            ].join(' ')}
            style={{
              transitionDuration: contentVisible
                ? `${ENTER_DURATION}ms`
                : `${EXIT_DURATION}ms`,
            }}
          >
            <AssetList
              items={centerItems}
              loading={centerState.loading}
              failed={centerState.failed}
              emptyText={centerEmptyText}
              viewAllPath={centerViewAllPath}
            />
          </div>
        </div>

        <div className="flex min-w-0 flex-col border-t border-[#eceef2] pt-5 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
          <StaticSectionHeader
            title={intl.formatMessage({
              id: 'pages.home.dataAssets.dataService',
            })}
          />
          <AssetList
            items={serviceItems}
            loading={services.loading}
            failed={services.failed}
            emptyText={intl.formatMessage({
              id: 'pages.home.dataAssets.dataServiceEmpty',
            })}
            viewAllPath={DATA_SERVICE_LIST_PATH}
          />
        </div>
      </div>
    </section>
  );
}
