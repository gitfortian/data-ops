import { ReloadOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { Button, Tag } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  getDevelopmentDataServiceNode,
  type DevelopmentDataServiceNodeContext,
} from '../../data-service-node-service';
import {
  fetchDataServicePublicationState,
  type DataServicePublicationState,
} from '../../data-service-runtime-publication';
import type { DevelopmentId } from '../../types';
import { dataServiceDeliveryState } from './dataServiceDeliveryExperience';

interface DataServiceDeliveryTruthBarProps {
  nodeId: DevelopmentId;
  active: boolean;
  localDirty: boolean;
  refreshKey: number;
}

const servingLabel = {
  NO_REVISION: 'Runtime · 等待发布 Revision',
  NOT_PUBLISHED: 'Runtime · 尚未上线',
  ONLINE: 'Runtime · ONLINE',
  OFFLINE: 'Runtime · OFFLINE',
  UPDATE_AVAILABLE: 'Runtime · 有新 Revision 待更新',
  UNAVAILABLE: 'Runtime · 状态不可用',
} as const;

export default function DataServiceDeliveryTruthBar({
  nodeId,
  active,
  localDirty,
  refreshKey,
}: DataServiceDeliveryTruthBarProps) {
  const access = useAccess();
  const canPublish = access.hasPermission('data-development:publish');
  const canRelease = access.hasPermission('data-development:release');
  const [context, setContext] = useState<DevelopmentDataServiceNodeContext>();
  const [publication, setPublication] = useState<DataServicePublicationState>();
  const [publicationUnavailable, setPublicationUnavailable] = useState(false);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!active) return;
    setLoading(true);
    try {
      const next = await getDevelopmentDataServiceNode(nodeId);
      setContext(next);
      if (!next.latestPublishedRevision) {
        setPublication(undefined);
        setPublicationUnavailable(false);
        return;
      }
      try {
        setPublication(await fetchDataServicePublicationState(nodeId));
        setPublicationUnavailable(false);
      } catch {
        setPublication(undefined);
        setPublicationUnavailable(true);
      }
    } finally {
      setLoading(false);
    }
  }, [active, nodeId]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  useEffect(() => {
    if (!active || !context?.latestPublishedRevision) return undefined;
    const timer = window.setInterval(() => void load(), 3000);
    return () => window.clearInterval(timer);
  }, [active, context?.latestPublishedRevision, load]);

  const state = useMemo(
    () => dataServiceDeliveryState(context, localDirty, publication, publicationUnavailable),
    [context, localDirty, publication, publicationUnavailable],
  );

  const runtimeRevision = state.runtimeRevisionNo
    ? ` · serving DS R${state.runtimeRevisionNo}`
    : '';

  return (
    <div className="flex h-8 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-[#fafbfc] px-3 text-[11px] text-[#475467]">
      <div className="flex min-w-0 items-center gap-2">
        <span className="font-medium text-[#344054]">Delivery Truth</span>
        <Tag bordered={false} className="m-0">
          {state.draftState === 'LOCAL_UNSAVED'
            ? `Draft #${state.draftRevision} · 本地未保存`
            : `Draft #${state.draftRevision} · 已保存`}
        </Tag>
        <Tag bordered={false} className="m-0">
          {state.publishedRevisionNo
            ? `Published · DS R${state.publishedRevisionNo}`
            : 'Published · 无 Revision'}
        </Tag>
        <Tag bordered={false} className="m-0">
          {servingLabel[state.servingState]}{runtimeRevision}
        </Tag>
        {state.servingState === 'UPDATE_AVAILABLE' ? (
          <span className="truncate text-[#b54708]">
            最新 Published Revision 尚未成为 Runtime serving revision
          </span>
        ) : null}
        {state.servingState === 'UNAVAILABLE' ? (
          <span className="truncate text-[#b42318]">
            Runtime 状态读取失败；不要将其解释为 OFFLINE
          </span>
        ) : null}
      </div>
      <div className="ml-3 flex shrink-0 items-center gap-2 text-[#667085]">
        {!canPublish ? <span>Publish 只读</span> : null}
        {!canRelease ? <span>Runtime 只读</span> : null}
        <Button
          type="text"
          size="small"
          aria-label="刷新 Data Service Delivery Truth"
          icon={<ReloadOutlined spin={loading} />}
          onClick={() => void load()}
        />
      </div>
    </div>
  );
}
