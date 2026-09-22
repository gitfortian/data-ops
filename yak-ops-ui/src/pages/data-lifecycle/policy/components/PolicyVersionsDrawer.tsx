import { Drawer } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import VersionHistoryPanel from '@/components/version/VersionHistoryPanel';
import {
  getPolicyVersion,
  listPolicyVersions,
  rollbackPolicyVersion,
} from '@/services/data-lifecycle/api';
import type { PolicyRecord } from '@/services/data-lifecycle/types';
import { GRANULARITY_LABELS, LAYER_LABELS } from '../../constants';

const FIELD_LABELS: Record<string, string> = {
  policyCode: '策略编码',
  policyName: '策略名称',
  scopeType: '适用范围',
  layerCode: '分层',
  partitionGranularity: '分区粒度',
  hotDays: '热数据(天)',
  coldDays: '冷数据(天)',
  destroyDays: '销毁(天)',
  remark: '备注',
};

const renderValue = (key: string, value: unknown): string => {
  if (value === null || value === undefined || value === '') return '-';
  const text = String(value);
  if (key === 'partitionGranularity') return GRANULARITY_LABELS[text as keyof typeof GRANULARITY_LABELS] ?? text;
  if (key === 'layerCode') return LAYER_LABELS[text.toUpperCase()] ?? text;
  if (key === 'scopeType') return text === 'LAYER_DEFAULT' ? '分层默认' : '自定义';
  return text;
};

/** TTL 策略快照的表格式详情：把 payload 渲染成 字段/值 两列，替代裸 JSON。 */
const PolicySnapshotDetail = ({ payload }: { payload: unknown }) => {
  const entries = Object.entries((payload ?? {}) as Record<string, unknown>);
  return (
    <div className="overflow-hidden rounded-[8px] border border-[#eaecf0]">
      <table className="w-full border-collapse text-[12px]">
        <tbody>
          {entries.map(([key, value], index) => (
            <tr key={key} className={index % 2 ? 'bg-[#f9fafb]' : 'bg-white'}>
              <td className="w-[140px] border-r border-[#eaecf0] px-3 py-1.5 text-[#667085]">
                {FIELD_LABELS[key] ?? key}
              </td>
              <td className="px-3 py-1.5 text-[#242731]">{renderValue(key, value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

interface PolicyVersionsDrawerProps {
  open: boolean;
  policy: PolicyRecord | null;
  onClose: () => void;
  /** 回滚改变了主表内容，宿主需要刷新列表。 */
  onChanged: () => void;
}

/** 策略版本历史抽屉（契约 C3 前端件）：查看/对比/回滚，数据源 /policies/{id}/versions。 */
const PolicyVersionsDrawer: React.FC<PolicyVersionsDrawerProps> = ({
  open,
  policy,
  onClose,
  onChanged,
}) => {
  const [refreshToken, setRefreshToken] = useState(0);
  const [onlineVersionNo, setOnlineVersionNo] = useState<number | null>(null);

  const listVersions = useCallback(async () => {
    if (!policy) return [];
    const rows = await listPolicyVersions(policy.id);
    setOnlineVersionNo((rows ?? []).find((row) => row.current)?.versionNo ?? null);
    return (rows ?? []).map((row) => ({
      versionNo: row.versionNo,
      createdAt: row.createTime ?? undefined,
      createdBy: row.createdBy ?? undefined,
      checksum: row.checksum ?? undefined,
    }));
  }, [policy]);

  const getVersion = useCallback(
    async (versionNo: number) => {
      if (!policy) return undefined;
      const detail = await getPolicyVersion(policy.id, versionNo);
      return detail?.payload;
    },
    [policy],
  );

  const rollback = useCallback(
    async (versionNo: number) => {
      if (!policy) return;
      await rollbackPolicyVersion(policy.id, versionNo);
      setRefreshToken((prev) => prev + 1);
      onChanged();
    },
    [policy, onChanged],
  );

  // 线上位以版本行的 current 标记为准（回滚后指针会移动），行数据未就绪时回退静态推导。
  const currentVersionNo = useMemo(
    () =>
      onlineVersionNo ??
      (policy?.latestVersionNo && !policy.hasPendingDraft
        ? policy.latestVersionNo
        : null),
    [onlineVersionNo, policy],
  );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={640}
      destroyOnClose
      title={
        policy ? (
          <div>
            <div className="text-[15px] font-semibold">{policy.policyName} · 版本历史</div>
            <div className="mt-0.5 text-[12px] font-normal text-[#667085]">
              {policy.policyCode}
              {policy.latestVersionNo ? ` · 最新 V${policy.latestVersionNo}` : ' · 尚未发布'}
              {policy.hasPendingDraft ? ' · 存在未发布的草稿修改' : ''}
            </div>
          </div>
        ) : (
          '版本历史'
        )
      }
    >
      <VersionHistoryPanel
        listVersions={listVersions}
        getVersion={getVersion}
        rollback={rollback}
        currentVersionNo={currentVersionNo}
        renderDetail={(payload) => <PolicySnapshotDetail payload={payload} />}
        emptyTitle="还没有发布版本"
        emptyDescription="在列表中点击「发布」后，这里会记录每一版全量快照，可对比、可回滚"
        refreshKey={refreshToken}
      />
    </Drawer>
  );
};

export default PolicyVersionsDrawer;
