import { FileSuffixIcon } from '@/components/resource/FileSuffixIcon';
import { YAK_OPS_PERMISSIONS } from '@/constants/yakOpsPermissions';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import {
  homeResourceCenterApi,
  type HomeResourceCenterOverview,
} from '@/services/home';
import { API_SUCCESS_CODE } from '@/services/http/response';
import { history, useIntl } from '@umijs/max';
import {
  ChevronRight,
  LockKeyhole,
  RefreshCw,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import { HomeEmptyState } from './HomeEmptyState';

const RESOURCE_MANAGEMENT_PATH = '/resource-management';

const parseResourceTime = (value?: string | null) => {
  if (!value) return Number.NaN;

  const normalized = value.includes(' ')
    ? value.replace(' ', 'T')
    : value;

  return new Date(normalized).getTime();
};

const formatFileSize = (bytes?: number | null) => {
  const value = Number(bytes || 0);

  if (value <= 0) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];

  const unitIndex = Math.min(
    Math.floor(Math.log(value) / Math.log(1024)),
    units.length - 1,
  );

  const normalized = value / 1024 ** unitIndex;

  const precision =
    normalized >= 100 || unitIndex === 0
      ? 0
      : normalized >= 10
        ? 1
        : 2;

  return `${normalized.toFixed(precision)} ${units[unitIndex]}`;
};

const formatRelativeTime = (
  value: string | null | undefined,
  locale: string,
) => {
  const timestamp = parseResourceTime(value);

  if (!Number.isFinite(timestamp)) {
    return '--';
  }

  const diffSeconds = Math.round(
    (timestamp - Date.now()) / 1000,
  );

  const formatter = new Intl.RelativeTimeFormat(locale, {
    numeric: 'auto',
  });

  const absoluteSeconds = Math.abs(diffSeconds);

  if (absoluteSeconds < 60) {
    return formatter.format(diffSeconds, 'second');
  }

  const diffMinutes = Math.round(diffSeconds / 60);

  if (Math.abs(diffMinutes) < 60) {
    return formatter.format(diffMinutes, 'minute');
  }

  const diffHours = Math.round(diffMinutes / 60);

  if (Math.abs(diffHours) < 24) {
    return formatter.format(diffHours, 'hour');
  }

  const diffDays = Math.round(diffHours / 24);

  return formatter.format(diffDays, 'day');
};

function ResourceCenterSkeleton() {
  return (
    <div className="mt-4 animate-pulse">
      <div className="grid grid-cols-3 gap-2">
        {Array.from({ length: 3 }).map((_, index) => (
          <div
            key={index}
            className="h-[82px] rounded-[14px] border border-[#f0f1f3] bg-[#fafbfc] px-4 py-3"
          >
            <div className="h-3 w-14 rounded bg-[#eceef1]" />
            <div className="mt-3 h-5 w-16 rounded bg-[#e5e7eb]" />
          </div>
        ))}
      </div>

      <div className="mt-5 border-t border-[#eef0f3] pt-4">
        <div className="h-4 w-20 rounded bg-[#eceef1]" />

        <div className="mt-3 space-y-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <div
              key={index}
              className="flex items-center gap-3"
            >
              <div className="h-10 w-10 rounded-[11px] bg-[#f0f1f3]" />

              <div className="min-w-0 flex-1">
                <div className="h-3.5 w-3/5 rounded bg-[#eceef1]" />
                <div className="mt-2 h-3 w-2/5 rounded bg-[#f1f2f4]" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function EmptyRecentFiles() {
  const intl = useIntl();

  return (
    <div className="flex min-h-[190px] flex-1 flex-col items-center justify-center px-4 py-6 text-center">
      <img
        src="/image/add.png"
        alt=""
        className="h-[140px] w-[140px] object-contain"
      />

      <div className="mt-3 text-[12px] font-medium text-[#707681]">
        {intl.formatMessage({
          id: 'pages.home.resourceCenter.emptyRecent',
        })}
      </div>

    </div>
  );
}

export default function HomeResourceCenter() {
  const intl = useIntl();

  const { can } = usePermissionAccess();

  const canRead = can(
    YAK_OPS_PERMISSIONS.resource.read,
  );

  const [overview, setOverview] =
    useState<HomeResourceCenterOverview>();

  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  const loadOverview = useCallback(async () => {
    if (!canRead) {
      return;
    }

    try {
      setLoading(true);
      setFailed(false);

      const response =
        await homeResourceCenterApi.overview();

      if (
        response.code !== API_SUCCESS_CODE ||
        !response.data
      ) {
        setFailed(true);
        return;
      }

      setOverview(response.data);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [canRead]);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  const statItems = [
    {
      key: 'files',
      label: intl.formatMessage({
        id: 'pages.home.resourceCenter.files',
      }),
      value:
        overview?.fileCount == null
          ? '--'
          : overview.fileCount.toLocaleString(
              intl.locale,
            ),
    },
    {
      key: 'folders',
      label: intl.formatMessage({
        id: 'pages.home.resourceCenter.folders',
      }),
      value:
        overview?.folderCount == null
          ? '--'
          : overview.folderCount.toLocaleString(
              intl.locale,
            ),
    },
    {
      key: 'storage',
      label: intl.formatMessage({
        id: 'pages.home.resourceCenter.storage',
      }),
      value:
        overview?.totalBytes == null
          ? '--'
          : formatFileSize(overview.totalBytes),
    },
  ];

  const recentFiles = overview?.recentFiles || [];

  return (
    <section className="flex min-h-[420px] flex-1 flex-col rounded-[22px] border border-[#f0f1f3] bg-white px-5 pb-5 pt-5">
      <header className="flex items-center justify-between gap-4">
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({
            id: 'pages.home.resourceCenter.title',
          })}
        </h2>

        {canRead ? (
          <button
            type="button"
            onClick={() =>
              history.push(
                RESOURCE_MANAGEMENT_PATH,
              )
            }
            className="flex shrink-0 items-center gap-0.5 border-0 bg-transparent p-0 text-[12px] text-[#666b75] transition-colors hover:text-[#252832]"
          >
            {intl.formatMessage({
              id: 'pages.home.common.viewAll',
            })}

            <ChevronRight
              size={14}
              strokeWidth={1.8}
            />
          </button>
        ) : null}
      </header>

      {!canRead ? (
        <HomeEmptyState
          icon={LockKeyhole}
          title={intl.formatMessage({
            id: 'pages.home.resourceCenter.noPermission',
          })}
          size="medium"
          className="min-h-[300px] flex-1"
        />
      ) : loading && !overview ? (
        <ResourceCenterSkeleton />
      ) : (
        <>
          {/* 资源统计 */}
          <div className="mt-4 grid grid-cols-3 gap-2">
            {statItems.map((item) => (
              <div
                key={item.key}
                className="min-w-0 rounded-[14px] border border-[#f0f1f3] bg-[#fbfbfc] px-4 py-3"
              >
                <div className="truncate text-[10px] text-[#9398a1]">
                  {item.label}
                </div>

                <strong
                  className="mt-2 block truncate text-[17px] font-semibold tracking-[-0.25px] text-[#292d36]"
                  title={
                    failed ? '--' : item.value
                  }
                >
                  {failed ? '--' : item.value}
                </strong>
              </div>
            ))}
          </div>

          {/* 最近更新 */}
          <div className="mt-5 flex min-h-[220px] flex-1 flex-col border-t border-[#eef0f3] pt-4">
            <div className="flex items-center justify-between gap-3">
              <strong className="text-[13px] font-semibold text-[#3c4049]">
                {intl.formatMessage({
                  id: 'pages.home.resourceCenter.recent',
                })}
              </strong>

              <span className="text-[10px] text-[#9a9fa8]">
                {intl.formatMessage({
                  id: 'pages.home.resourceCenter.recentRange',
                })}
              </span>
            </div>

            {failed ? (
              <div className="flex flex-1 flex-col items-center justify-center py-5 text-center">
                <HomeEmptyState
                  icon={RefreshCw}
                  title={intl.formatMessage({
                    id: 'pages.home.common.loadFailed',
                  })}
                  size="small"
                />

                <button
                  type="button"
                  onClick={() =>
                    void loadOverview()
                  }
                  className="mt-2 border-0 bg-transparent p-0 text-[10px] font-medium text-[#6f7681] hover:text-[#252832]"
                >
                  {intl.formatMessage({
                    id: 'pages.home.resourceCenter.retry',
                  })}
                </button>
              </div>
            ) : recentFiles.length ? (
              <div className="mt-2 divide-y divide-[#f1f2f4]">
                {recentFiles.map((resource) => (
                  <div
                    key={resource.id}
                    className="flex min-w-0 items-center gap-3 py-2.5"
                    title={
                      resource.fullPath ||
                      resource.name
                    }
                  >
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] bg-[#f5f6f8]">
                      <FileSuffixIcon
                        suffix={
                          resource.suffix ||
                          undefined
                        }
                        size={18}
                      />
                    </span>

                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[12px] font-medium text-[#3c4049]">
                        {resource.name}
                      </div>

                      <div className="mt-1 flex min-w-0 items-center gap-1.5 text-[10px] text-[#9a9fa8]">
                        <span className="shrink-0">
                          {formatFileSize(
                            resource.fileSize,
                          )}
                        </span>

                        <span className="h-0.5 w-0.5 shrink-0 rounded-full bg-[#c7cad0]" />

                        <span className="min-w-0 truncate">
                          {formatRelativeTime(
                            resource.updatedAt,
                            intl.locale,
                          )}
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyRecentFiles />
            )}
          </div>
        </>
      )}
    </section>
  );
}