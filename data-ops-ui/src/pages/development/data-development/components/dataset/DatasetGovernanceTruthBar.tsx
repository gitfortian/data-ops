import { ReloadOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import { Button, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import {
  datasetAssetDetailUrl,
  datasetAssetGovernanceLabel,
  lookupDatasetAsset,
  type DatasetAssetGovernanceView,
} from '../../assetGovernance';
import { getDevelopmentDatasetNode } from '../../dataset-service';
import type { DevelopmentId } from '../../types';

interface DatasetGovernanceTruthBarProps {
  nodeId: DevelopmentId;
  active: boolean;
  refreshKey: number;
}

export default function DatasetGovernanceTruthBar({
  nodeId,
  active,
  refreshKey,
}: DatasetGovernanceTruthBarProps) {
  const access = useAccess();
  const canReadAssets = access.hasPermission('data-asset:read');
  const [view, setView] = useState<DatasetAssetGovernanceView>({ state: 'NOT_CREATED' });
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!active) return;
    setLoading(true);
    try {
      const context = await getDevelopmentDatasetNode(nodeId);
      const datasetId = context.dataset?.datasetId;
      if (!datasetId) {
        setView({ state: 'NOT_CREATED' });
        return;
      }
      if (!canReadAssets) {
        setView({
          state: 'PERMISSION_DENIED',
          datasetId,
          reason: '当前账号没有 Asset Governance 读取权限；Dataset 本身仍可继续开发。',
        });
        return;
      }
      try {
        const asset = await lookupDatasetAsset(datasetId);
        setView({
          state: asset.state,
          datasetId,
          asset,
          reason: asset.state === 'NOT_INDEXED'
            ? 'Dataset 已存在，但 Asset Registry 尚未完成对账投影；不能解释为没有治理资产。'
            : undefined,
        });
      } catch (error) {
        setView({
          state: 'UNAVAILABLE',
          datasetId,
          reason: error instanceof Error
            ? error.message
            : 'Asset Governance 查询暂不可用',
        });
      }
    } catch (error) {
      setView({
        state: 'UNAVAILABLE',
        reason: error instanceof Error
          ? error.message
          : 'Dataset / Asset Governance 上下文暂不可用',
      });
    } finally {
      setLoading(false);
    }
  }, [active, canReadAssets, nodeId]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const detailUrl = datasetAssetDetailUrl(view.asset);

  return (
    <div className="flex h-8 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-[#fafbfc] px-3 text-[12px] text-[#475467]">
      <div className="flex min-w-0 items-center gap-2">
        <span className="font-medium text-[#344054]">Governance</span>
        {view.datasetId ? (
          <Tag bordered={false} className="m-0 font-mono">Dataset #{String(view.datasetId)}</Tag>
        ) : null}
        <Tag bordered={false} className="m-0">
          {datasetAssetGovernanceLabel(view.state)}
        </Tag>
        {view.asset?.assetKey ? (
          <span className="max-w-[320px] truncate font-mono text-[12px] text-[#667085]">
            {view.asset.assetKey}
          </span>
        ) : null}
        {view.reason ? (
          <span className="max-w-[520px] truncate text-[#b54708]" title={view.reason}>
            {view.reason}
          </span>
        ) : null}
      </div>
      <div className="ml-3 flex shrink-0 items-center gap-1">
        {detailUrl ? (
          <Button type="link" size="small" className="!h-6 !px-2" onClick={() => history.push(detailUrl)}>
            打开 Asset Governance
          </Button>
        ) : null}
        <Button
          type="text"
          size="small"
          aria-label="刷新 Dataset Governance"
          icon={<ReloadOutlined spin={loading} />}
          onClick={() => void load()}
        />
      </div>
    </div>
  );
}
