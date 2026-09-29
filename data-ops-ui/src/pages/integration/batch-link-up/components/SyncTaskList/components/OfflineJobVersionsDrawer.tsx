import {
  getOfflineSyncVersion,
  listOfflineSyncVersions,
  rollbackOfflineSyncVersion,
  type OfflineJobDefinitionVO,
} from '@/services/batch-link-up';
import VersionHistoryPanel, {
  type VersionSummary,
} from '@/components/version/VersionHistoryPanel';
import { Drawer } from 'antd';
import { useCallback, useState } from 'react';

interface OfflineJobVersionsDrawerProps {
  open: boolean;
  task: OfflineJobDefinitionVO | null;
  onClose: () => void;
  /** 回滚成功后刷新列表（latestVersionNo / hasPendingDraft 变化）。 */
  onChanged?: () => void | Promise<void>;
}

const OfflineJobVersionsDrawer = ({
  open,
  task,
  onClose,
  onChanged,
}: OfflineJobVersionsDrawerProps) => {
  const [currentVersionNo, setCurrentVersionNo] = useState<number | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const listVersions = useCallback(async (): Promise<VersionSummary[]> => {
    if (task?.id === undefined || task.id === null) return [];
    const rows = await listOfflineSyncVersions(task.id);
    setCurrentVersionNo(rows.find((row) => row.current)?.versionNo ?? null);
    return rows.map((row) => ({
      versionNo: row.versionNo,
      createdAt: row.createTime,
      createdBy: row.createdBy,
      checksum: row.checksum,
    }));
  }, [task?.id]);

  const getVersion = useCallback(
    async (versionNo: number) => {
      if (task?.id === undefined || task.id === null) return undefined;
      const detail = await getOfflineSyncVersion(task.id, versionNo);
      return detail?.payload;
    },
    [task?.id],
  );

  const rollback = useCallback(
    async (versionNo: number) => {
      if (task?.id === undefined || task.id === null) return;
      await rollbackOfflineSyncVersion(task.id, versionNo);
      setRefreshKey((key) => key + 1);
      void onChanged?.();
    },
    [task?.id, onChanged],
  );

  const renderDetail = (payload: unknown) => {
    const snapshot = payload as {
      definition?: unknown;
      jobSpec?: unknown;
      configDigest?: string;
    };
    return (
      <div className="space-y-3">
        {snapshot.configDigest ? (
          <div className="font-mono text-[11px] text-[#98a2b3]">
            configDigest: {snapshot.configDigest}
          </div>
        ) : null}
        <div>
          <div className="mb-1 text-[12px] font-medium text-[#344054]">任务定义</div>
          <pre className="max-h-[260px] overflow-auto rounded-[6px] bg-[#f9fafb] p-3 font-mono text-[11px] leading-5 text-[#344054]">
            {JSON.stringify(snapshot.definition ?? null, null, 2)}
          </pre>
        </div>
        <div>
          <div className="mb-1 text-[12px] font-medium text-[#344054]">JobSpec</div>
          <pre className="max-h-[260px] overflow-auto rounded-[6px] bg-[#f9fafb] p-3 font-mono text-[11px] leading-5 text-[#344054]">
            {JSON.stringify(snapshot.jobSpec ?? null, null, 2)}
          </pre>
        </div>
      </div>
    );
  };

  const publishedLabel =
    currentVersionNo != null
      ? `线上 V${currentVersionNo}`
      : task?.latestVersionNo
        ? '最新发布 V' + task.latestVersionNo + '（指针未生效）'
        : '尚未发布';

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={720}
      destroyOnClose
      title={
        <div className="flex items-baseline gap-2">
          <span className="text-[15px] font-semibold">版本历史</span>
          <span className="text-[12px] font-normal text-[#667085]">
            {task?.jobName ?? '-'} · {publishedLabel}
            {task?.hasPendingDraft ? ' · 存在未发布的草稿修改' : ''}
          </span>
        </div>
      }
    >
      <VersionHistoryPanel
        listVersions={listVersions}
        getVersion={getVersion}
        rollback={rollback}
        currentVersionNo={currentVersionNo}
        refreshKey={refreshKey}
        renderDetail={renderDetail}
        rollbackDescription="将用该版本内容覆盖当前草稿并立即发布为新版本；回滚前的草稿内容会丢失（审计中留痕）。"
      />
    </Drawer>
  );
};

export default OfflineJobVersionsDrawer;
