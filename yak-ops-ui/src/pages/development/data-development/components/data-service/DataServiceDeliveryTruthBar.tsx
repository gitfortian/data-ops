import {
  bringDataServiceOnline,
  fetchDataServicePublicationState,
  getDevelopmentDataServiceNode,
  takeDataServiceOffline,
  type DataServicePublicationState,
  type DevelopmentDataServiceNodeContext,
} from '@/services/data-development';
import { history, useAccess } from '@umijs/max';
import { Button, Spin, Tooltip, message } from 'antd';
import { ExternalLink, Power, PowerOff, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import type { DevelopmentId } from '../../types';
import { dataServiceDeliveryState } from './dataServiceDeliveryExperience';

interface DataServiceDeliveryTruthBarProps {
  nodeId: DevelopmentId;
  refreshKey?: number;
}

const stateText = (state: ReturnType<typeof dataServiceDeliveryState>) => {
  switch (state.runtimeState) {
    case 'UNPUBLISHED':
      return '尚未发布 DS Revision';
    case 'PUBLICATION_UNAVAILABLE':
      return 'Runtime 状态不可用';
    case 'NOT_ONLINE':
      return '尚未上线 Runtime';
    case 'ONLINE_CURRENT':
      return `Runtime DS R${state.runtimeRevisionNo || '-'} · ONLINE`;
    case 'ONLINE_OUTDATED':
      return `Runtime DS R${state.runtimeRevisionNo || '-'} · ONLINE · 有新版本待更新`;
    case 'OFFLINE_CURRENT':
      return `Runtime DS R${state.runtimeRevisionNo || '-'} · OFFLINE`;
    case 'OFFLINE_OUTDATED':
      return `Runtime DS R${state.runtimeRevisionNo || '-'} · OFFLINE · 有新版本待更新`;
  }
};

export default function DataServiceDeliveryTruthBar({
  nodeId,
  refreshKey = 0,
}: DataServiceDeliveryTruthBarProps) {
  const access = useAccess();
  const canRelease = access.hasPermission('data-development:release');
  const [context, setContext] = useState<DevelopmentDataServiceNodeContext>();
  const [publication, setPublication] = useState<DataServicePublicationState>();
  const [publicationUnavailable, setPublicationUnavailable] = useState(false);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setPublicationUnavailable(false);
    try {
      const nextContext = await getDevelopmentDataServiceNode(nodeId);
      setContext(nextContext);
      if (!nextContext.latestPublishedRevision) {
        setPublication(undefined);
        return;
      }
      try {
        setPublication(await fetchDataServicePublicationState(nodeId));
      } catch {
        setPublication(undefined);
        setPublicationUnavailable(true);
      }
    } catch (error) {
      setContext(undefined);
      setPublication(undefined);
      message.error(
        error instanceof Error ? error.message : '读取 Data Service delivery 状态失败',
      );
    } finally {
      setLoading(false);
    }
  }, [nodeId]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const state = dataServiceDeliveryState(
    context,
    publication,
    publicationUnavailable,
  );

  const online = async () => {
    if (!canRelease || !state.latestPublishedRevisionNo || actionLoading) return;
    setActionLoading(true);
    try {
      await bringDataServiceOnline(nodeId, publication);
      await load();
      message.success(
        state.runtimeState === 'ONLINE_OUTDATED'
          ? `DS R${state.latestPublishedRevisionNo} 已更新到 Runtime`
          : `DS R${state.latestPublishedRevisionNo} 已上线 Runtime`,
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Data Service Runtime 上线失败');
    } finally {
      setActionLoading(false);
    }
  };

  const offline = async () => {
    if (!canRelease || !publication?.published || !publication.detail?.enabled || actionLoading) {
      return;
    }
    setActionLoading(true);
    try {
      await takeDataServiceOffline(nodeId);
      await load();
      message.success('Data Service Runtime 已下线；Published DS Revision 保持不变');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Data Service Runtime 下线失败');
    } finally {
      setActionLoading(false);
    }
  };

  const canOnline = Boolean(
    state.latestPublishedRevisionNo
      && !publicationUnavailable
      && (
        !publication?.published
        || state.updateAvailable
        || publication.detail?.enabled === false
      ),
  );
  const canOffline = Boolean(publication?.published && publication.detail?.enabled);

  return (
    <div className="flex min-h-9 shrink-0 items-center justify-between gap-3 border-b border-[#e8e9ec] bg-[#fafafa] px-3 text-[11px] text-[#667085]">
      <div className="flex min-w-0 items-center gap-3 overflow-hidden">
        <span className="shrink-0 font-medium text-[#475467]">Delivery Truth</span>
        {loading ? <Spin size="small" /> : null}
        <span className="shrink-0">Draft #{state.draftRevision || 0}</span>
        <span className="shrink-0">
          {state.latestPublishedRevisionNo
            ? `Published DS R${state.latestPublishedRevisionNo}`
            : '尚未发布 DS Revision'}
        </span>
        <span
          className={[
            'truncate',
            publicationUnavailable
              ? 'text-[#b42318]'
              : state.updateAvailable
                ? 'text-[#b54708]'
                : 'text-[#667085]',
          ].join(' ')}
          title={stateText(state)}
        >
          {stateText(state)}
        </span>
        {publication?.detail?.runtimePath ? (
          <span className="max-w-[260px] truncate font-mono" title={publication.detail.runtimePath}>
            {publication.detail.runtimePath}
          </span>
        ) : null}
      </div>

      <div className="flex shrink-0 items-center gap-1">
        <Tooltip title="刷新 delivery truth">
          <Button
            type="text"
            size="small"
            icon={<RefreshCw size={13} />}
            disabled={loading || actionLoading}
            onClick={() => void load()}
          />
        </Tooltip>
        {canRelease && canOnline ? (
          <Button
            type="text"
            size="small"
            loading={actionLoading}
            icon={<Power size={13} />}
            onClick={() => void online()}
          >
            {state.updateAvailable ? '更新上线' : '上线'}
          </Button>
        ) : null}
        {canRelease && canOffline ? (
          <Button
            type="text"
            danger
            size="small"
            loading={actionLoading}
            icon={<PowerOff size={13} />}
            onClick={() => void offline()}
          >
            下线
          </Button>
        ) : null}
        {publication?.published ? (
          <Button
            type="text"
            size="small"
            icon={<ExternalLink size={13} />}
            onClick={() => history.push('/data-service')}
          >
            API 服务
          </Button>
        ) : null}
        {!canRelease && state.latestPublishedRevisionNo ? (
          <span className="ml-1 shrink-0 text-[#98a2b3]">Release 只读</span>
        ) : null}
      </div>
    </div>
  );
}
