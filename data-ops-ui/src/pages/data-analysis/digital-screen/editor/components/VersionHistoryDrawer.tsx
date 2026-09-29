import { YakButton } from '@/components/ui';
import type { DigitalScreenVersionSummary } from '@/services/digital-screen';
import { Drawer, Empty, Popconfirm, Spin } from 'antd';
import { CheckCircle2, History, RotateCcw } from 'lucide-react';

interface VersionHistoryDrawerProps {
  open: boolean;
  versions: DigitalScreenVersionSummary[];
  loading: boolean;
  rollingBackVersionNo?: number;
  onClose: () => void;
  onRollback: (versionNo: number) => void;
}

const formatDateTime = (value: string) => value.replace('T', ' ').slice(0, 19);

export function VersionHistoryDrawer({
  open,
  versions,
  loading,
  rollingBackVersionNo,
  onClose,
  onRollback,
}: VersionHistoryDrawerProps) {
  return (
    <Drawer
      title={(
        <div className="flex items-center gap-2 text-[14px] font-semibold text-[#161823]">
          <History size={15} /> 发布版本
        </div>
      )}
      width={420}
      open={open}
      onClose={onClose}
    >
      {loading ? (
        <div className="flex h-[240px] items-center justify-center"><Spin size="small" /></div>
      ) : versions.length ? (
        <div className="space-y-3">
          {versions.map((version) => (
            <div key={version.id} className="rounded-[8px] border border-[#e7e9ec] p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-[13px] font-semibold text-[#161823]">V{version.versionNo}</span>
                    {version.current ? (
                      <span className="inline-flex items-center gap-1 rounded-[4px] bg-[#edf8f2] px-1.5 py-0.5 text-[10px] font-medium text-[#27845a]">
                        <CheckCircle2 size={10} /> 当前线上
                      </span>
                    ) : null}
                  </div>
                  <div className="mt-1 truncate text-[12px] text-[#555b64]">{version.name}</div>
                  <div className="mt-2 text-[10px] leading-5 text-[#98a2b3]">
                    发布于 {formatDateTime(version.publishedAt)} · Draft R{version.sourceRevision}
                  </div>
                </div>
                <Popconfirm
                  title={`回滚到 V${version.versionNo}？`}
                  description="会覆盖当前草稿，并把该快照追加发布为一个新的版本。"
                  okText="回滚并发布"
                  cancelText="取消"
                  disabled={version.current}
                  onConfirm={() => onRollback(version.versionNo)}
                >
                  <YakButton
                    size="small"
                    icon={<RotateCcw size={12} />}
                    disabled={version.current}
                    loading={rollingBackVersionNo === version.versionNo}
                  >
                    回滚
                  </YakButton>
                </Popconfirm>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有发布版本" />
      )}
    </Drawer>
  );
}
