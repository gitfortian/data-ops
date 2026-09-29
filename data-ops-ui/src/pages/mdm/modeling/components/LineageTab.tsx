import { Space, Typography, message } from 'antd';
import { useCallback, useState } from 'react';
import { history } from '@umijs/max';
import { YakButton } from '@/components/ui';
import { syncMdmLineage } from '@/services/mdm/api';

/**
 * 实体详情"血缘"Tab(R3):血缘图复用「数据血缘」(lineage,零改动)。
 * 一键登记三段物理表血缘(源表→落地表→yak_mdm_record)，资产键与元数据/开发
 * 血缘共用口径，可反复同步(幂等 upsert)。
 */
const LineageTab = ({ entityId }: { entityId: number }) => {
  const [syncing, setSyncing] = useState(false);
  const [receipt, setReceipt] = useState<{ assetCount: number; relationCount: number } | null>(
    null,
  );

  const sync = useCallback(async () => {
    setSyncing(true);
    try {
      const result = await syncMdmLineage(entityId);
      setReceipt(result);
      message.success(`血缘同步完成：${result.assetCount} 个表节点、${result.relationCount} 条边`);
    } catch (error: any) {
      message.error(error?.message || '血缘同步失败');
    } finally {
      setSyncing(false);
    }
  }, [entityId]);

  return (
    <div>
      <div className="mb-3 rounded-lg bg-[#f6f7f8] px-3 py-2 text-[13px] text-[#667085]">
        血缘复用「数据血缘」模块：一键登记三段物理表血缘
        源表 → 采集落地表 → yak_mdm_record（主数据记录表），随采集/加工变化可重复同步。
      </div>
      <Space size={12}>
        <YakButton type="primary" className="!h-8 !rounded-lg !px-3" loading={syncing} onClick={sync}>
          同步三段血缘
        </YakButton>
        <Typography.Link onClick={() => history.push('/data-analysis/lineage')}>
          在血缘图谱中查看
        </Typography.Link>
        {receipt && (
          <span className="text-[13px] text-[#667085]">
            最近同步：{receipt.assetCount} 资产 / {receipt.relationCount} 关系
          </span>
        )}
      </Space>
    </div>
  );
};

export default LineageTab;
