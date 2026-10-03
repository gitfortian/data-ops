import { YakButton } from '@/components/ui';
import { useIntl } from '@umijs/max';
import { LockKeyhole, RefreshCw, TriangleAlert } from 'lucide-react';

import type { WorkspaceLoadFailure } from '../workspaceState';

interface WorkspaceLoadFailureStateProps {
  failure: WorkspaceLoadFailure;
  loading: boolean;
  onRetry: () => void;
}

export const WorkspaceLoadFailureState = ({
  failure,
  loading,
  onRetry,
}: WorkspaceLoadFailureStateProps) => {
  const intl = useIntl();
  const permissionDenied = failure.kind === 'permission-denied';
  const Icon = permissionDenied ? LockKeyhole : TriangleAlert;
  const detail = failure.detail ||
    (failure.status ? `HTTP ${failure.status}` : undefined);

  return (
    <div className="flex min-h-0 flex-1 items-center justify-center bg-white px-6">
      <div className="max-w-[520px] text-center">
        <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-[#f5f5f6] text-[#667085]">
          <Icon size={19} strokeWidth={1.8} />
        </span>
        <div className="mt-3 text-[14px] font-semibold text-[#344054]">
          {intl.formatMessage({
            id: permissionDenied
              ? 'pages.dataDevelopment.workspaceState.permissionTitle'
              : 'pages.dataDevelopment.workspaceState.unavailableTitle',
          })}
        </div>
        <div className="mt-1 text-[12px] leading-5 text-[#667085]">
          {intl.formatMessage({
            id: permissionDenied
              ? 'pages.dataDevelopment.workspaceState.permissionDescription'
              : 'pages.dataDevelopment.workspaceState.unavailableDescription',
          })}
        </div>
        {detail ? (
          <div className="mt-2 break-words text-[12px] text-[#667085]">{detail}</div>
        ) : null}
        {!permissionDenied ? (
          <YakButton
            className="mt-4"
            size="small"
            loading={loading}
            icon={<RefreshCw size={13} />}
            onClick={onRetry}
          >
            {intl.formatMessage({ id: 'pages.dataDevelopment.workspaceState.retry' })}
          </YakButton>
        ) : null}
      </div>
    </div>
  );
};

interface ResourceInvalidatedNoticeProps {
  resourceType: 'node' | 'directory';
  onRefresh: () => void;
  onDismiss: () => void;
}

export const ResourceInvalidatedNotice = ({
  resourceType,
  onRefresh,
  onDismiss,
}: ResourceInvalidatedNoticeProps) => {
  const intl = useIntl();

  return (
    <div className="flex shrink-0 items-center gap-3 border-b border-[#f1d8a8] bg-[#fffaf0] px-4 py-2">
      <TriangleAlert size={15} className="shrink-0 text-[#b54708]" />
      <div className="min-w-0 flex-1 text-[12px] text-[#7a2e0e]">
        <span className="font-medium">
          {intl.formatMessage({ id: 'pages.dataDevelopment.workspaceState.resourceMissingTitle' })}
        </span>
        <span className="ml-2 text-[#9a4c1b]">
          {intl.formatMessage(
            { id: 'pages.dataDevelopment.workspaceState.resourceMissingDescription' },
            {
              resource: intl.formatMessage({
                id:
                  resourceType === 'directory'
                    ? 'pages.dataDevelopment.common.directory'
                    : 'pages.dataDevelopment.common.node',
              }),
            },
          )}
        </span>
      </div>
      <YakButton type="text" size="small" onClick={onRefresh}>
        {intl.formatMessage({ id: 'pages.dataDevelopment.workspaceState.refreshCatalog' })}
      </YakButton>
      <YakButton type="text" size="small" onClick={onDismiss}>
        {intl.formatMessage({ id: 'pages.dataDevelopment.workspaceState.dismiss' })}
      </YakButton>
    </div>
  );
};
