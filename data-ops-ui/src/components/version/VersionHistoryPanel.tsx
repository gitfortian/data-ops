import { Popconfirm, Tooltip, message } from 'antd';
import { GitCompareArrows, History, RotateCcw } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { YakEmpty } from '@/components/ui';
import JsonDiffView from './JsonDiffView';

/** 版本行摘要，字段与各模块 versions 端点返回对齐（契约 C3）。 */
export interface VersionSummary {
  versionNo: number;
  createdAt?: string;
  createdBy?: string;
  checksum?: string;
  changeNote?: string;
}

export interface VersionHistoryPanelProps {
  /** GET /{id}/versions */
  listVersions: () => Promise<VersionSummary[] | undefined>;
  /** GET /{id}/versions/{no}，返回快照对象或 JSON 字符串 */
  getVersion?: (versionNo: number) => Promise<unknown>;
  /** POST /{id}/versions/{no}/rollback（追加式回滚） */
  rollback?: (versionNo: number) => Promise<unknown>;
  /** 当前线上版本号，命中行展示「线上」徽标 */
  currentVersionNo?: number | null;
  /** 覆盖默认回滚影响文案（追加式 vs 指针回切语义不同处使用） */
  rollbackDescription?: string;
  /** 自定义快照查看（如 modeling 的结构表格）；缺省渲染 pretty JSON */
  renderDetail?: (payload: unknown, versionNo: number) => React.ReactNode;
  emptyTitle?: string;
  emptyDescription?: string;
  /** 宿主页面提供刷新触发（如发布成功后外部 bump） */
  refreshKey?: string | number;
}

const formatDateTime = (value?: string) => value?.replace('T', ' ').slice(0, 19) ?? '-';

/**
 * 全项目统一的版本历史面板（多版本契约 C3 前端件）：
 * 列表 + 单版查看 + 任选两版 diff + 回滚二次确认（明示影响）。
 */
const VersionHistoryPanel: React.FC<VersionHistoryPanelProps> = ({
  listVersions,
  getVersion,
  rollback,
  currentVersionNo,
  rollbackDescription,
  renderDetail,
  emptyTitle = '暂无发布版本',
  emptyDescription = '发布当前内容后即可在这里查看版本历史',
  refreshKey,
}) => {
  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [payloads, setPayloads] = useState<Record<number, unknown>>({});
  const [payloadLoading, setPayloadLoading] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [diffMode, setDiffMode] = useState(false);
  const [diffSelection, setDiffSelection] = useState<number[]>([]);
  const [rollingBack, setRollingBack] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await listVersions();
      const desc = [...(list ?? [])].sort((a, b) => b.versionNo - a.versionNo);
      setVersions(desc);
    } catch {
      setVersions([]);
    } finally {
      setLoading(false);
    }
  }, [listVersions]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const ensurePayload = useCallback(
    async (versionNo: number): Promise<unknown> => {
      if (payloads[versionNo] !== undefined) return payloads[versionNo];
      if (!getVersion) return undefined;
      setPayloadLoading(versionNo);
      try {
        const payload = await getVersion(versionNo);
        setPayloads((prev) => ({ ...prev, [versionNo]: payload }));
        return payload;
      } catch {
        message.error(`加载 V${versionNo} 快照失败`);
        return undefined;
      } finally {
        setPayloadLoading(null);
      }
    },
    [getVersion, payloads],
  );

  const toggleVersion = async (versionNo: number) => {
    if (diffMode) {
      setDiffSelection((prev) => {
        if (prev.includes(versionNo)) return prev.filter((v) => v !== versionNo);
        const next = [...prev, versionNo].sort((a, b) => a - b);
        return next.length > 2 ? next.slice(next.length - 2) : next;
      });
      await ensurePayload(versionNo);
      return;
    }
    if (expanded === versionNo) {
      setExpanded(null);
      return;
    }
    setExpanded(versionNo);
    await ensurePayload(versionNo);
  };

  const handleRollback = async (versionNo: number) => {
    if (!rollback) return;
    setRollingBack(versionNo);
    try {
      await rollback(versionNo);
      message.success(`已回滚：以 V${versionNo} 内容生成新版本`);
      setExpanded(null);
      setDiffSelection([]);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '回滚失败');
    } finally {
      setRollingBack(null);
    }
  };

  const defaultDescription =
    rollbackDescription ?? '将用该版本内容追加新版本并置为已发布，当前未发布的草稿内容会被丢弃。';

  if (!loading && versions.length === 0) {
    return <YakEmpty compact title={emptyTitle} description={emptyDescription} />;
  }

  const diffReady = diffSelection.length === 2;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-[13px] text-[#667085]">
          <History size={15} />
          <span>共 {versions.length} 个版本</span>
        </div>
        {getVersion ? (
          <button
            type="button"
            className={`flex items-center gap-1.5 rounded-[6px] border px-2.5 py-1 text-[12px] transition-colors ${
              diffMode
                ? 'border-[#1677ff] bg-[#e6f4ff] text-[#1677ff]'
                : 'border-[#d0d5dd] text-[#667085] hover:bg-[#f2f4f7]'
            }`}
            onClick={() => {
              setDiffMode((prev) => !prev);
              setExpanded(null);
              setDiffSelection([]);
            }}
          >
            <GitCompareArrows size={14} />
            {diffMode ? '退出对比' : '对比版本'}
          </button>
        ) : null}
      </div>

      {diffMode ? (
        <div className="rounded-[8px] border border-dashed border-[#baddff] bg-[#f5faff] px-3 py-2 text-[12px] text-[#475467]">
          {diffReady ? (
            <div className="space-y-2">
              <span>
                V{diffSelection[0]} → V{diffSelection[1]} 字段差异：
              </span>
              {payloadLoading === null ? (
                <JsonDiffView
                  before={payloads[diffSelection[0]]}
                  after={payloads[diffSelection[1]]}
                />
              ) : (
                <span className="text-[#98a2b3]">快照加载中…</span>
              )}
            </div>
          ) : (
            <span>点击两个版本号进行对比（已选 {diffSelection.length}/2）</span>
          )}
        </div>
      ) : null}

      <div className="space-y-3">
        {versions.map((version) => {
          const isExpanded = expanded === version.versionNo;
          const isSelected = diffSelection.includes(version.versionNo);
          const isCurrent = currentVersionNo != null && currentVersionNo === version.versionNo;
          return (
            <div key={version.versionNo} className="rounded-[8px] border border-[#e7e9ec] p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    className={`rounded-[4px] px-2 py-0.5 text-[13px] font-semibold transition-colors ${
                      isSelected
                        ? 'bg-[#e6f4ff] text-[#1677ff] ring-1 ring-[#91caff]'
                        : 'bg-[#f2f4f7] text-[#344054] hover:bg-[#e4e7ec]'
                    }`}
                    onClick={() => void toggleVersion(version.versionNo)}
                  >
                    V{version.versionNo}
                  </button>
                  {isCurrent ? (
                    <span className="rounded-[4px] bg-[#f6ffed] px-1.5 py-0.5 text-[11px] text-[#389e0d]">
                      线上
                    </span>
                  ) : null}
                  <span className="text-[12px] text-[#667085]">{formatDateTime(version.createdAt)}</span>
                  {version.createdBy ? (
                    <span className="text-[12px] text-[#98a2b3]">{version.createdBy}</span>
                  ) : null}
                  {version.changeNote ? (
                    <span className="text-[12px] text-[#98a2b3]">{version.changeNote}</span>
                  ) : null}
                </div>
                <div className="flex items-center gap-3">
                  {version.checksum ? (
                    <Tooltip title={version.checksum}>
                      <span className="font-mono text-[11px] text-[#98a2b3]">
                        {version.checksum.slice(0, 8)}
                      </span>
                    </Tooltip>
                  ) : null}
                  {rollback ? (
                    <Popconfirm
                      title={`确认回滚到 V${version.versionNo}？`}
                      description={defaultDescription}
                      onConfirm={() => void handleRollback(version.versionNo)}
                      okText="确认回滚"
                      cancelText="取消"
                    >
                      <Tooltip title="回滚到此版本">
                        <button
                          type="button"
                          className="rounded-[4px] border border-[#d0d5dd] p-1 text-[#667085] transition-colors hover:bg-[#f2f4f7]"
                          disabled={rollingBack !== null}
                        >
                          <RotateCcw size={14} />
                        </button>
                      </Tooltip>
                    </Popconfirm>
                  ) : null}
                </div>
              </div>

              {isExpanded ? (
                <div className="mt-3 border-t border-[#eaecf0] pt-3">
                  {payloadLoading === version.versionNo ? (
                    <div className="py-4 text-center text-[12px] text-[#98a2b3]">加载中…</div>
                  ) : payloads[version.versionNo] !== undefined ? (
                    renderDetail ? (
                      renderDetail(payloads[version.versionNo], version.versionNo)
                    ) : (
                      <pre className="max-h-[320px] overflow-auto rounded-[6px] bg-[#f9fafb] p-3 font-mono text-[11px] leading-5 text-[#344054]">
                        {JSON.stringify(payloads[version.versionNo], null, 2)}
                      </pre>
                    )
                  ) : (
                    <div className="py-4 text-center text-[12px] text-[#98a2b3]">未能加载快照</div>
                  )}
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default VersionHistoryPanel;
