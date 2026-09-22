import { useParams } from '@umijs/max';
import { Popconfirm, Table, Tag, message, Tooltip } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { YakEmpty } from '@/components/ui';
import { MODELING_PUBLISHED_EVENT } from '@/pages/modeling/constants';
import { findByBiz } from '@/services/approval/api';
import {
  getModelingVersion,
  listModelingVersions,
  publishModelingModel,
  rollbackModelingVersion,
} from '@/services/modeling/api';
import type { ModelingVersionDetail, ModelingVersionSummary } from '@/services/modeling/types';
import { History, RotateCcw, SendHorizontal } from 'lucide-react';

const formatDateTime = (value: string) => value?.replace('T', ' ').slice(0, 19) ?? '-';

/** 与详情页同源的发布审批常量(01)：在途单冻结回滚，防止覆盖审批快照语义。 */
const MODEL_PUBLISH_FLOW_CODE = 'MODEL_PUBLISH';
const MODEL_PUBLISH_BIZ_TYPE = 'MODEL';

/** 模型版本历史面板(统一视图「版本」Tab)。 */
const ModelVersionPanel: React.FC = () => {
  const params = useParams<{ id?: string }>();
  const modelId = params.id;

  const [versions, setVersions] = useState<ModelingVersionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [expandedVersionNo, setExpandedVersionNo] = useState<number | null>(null);
  const [detail, setDetail] = useState<ModelingVersionDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [rollbackVersionNo, setRollbackVersionNo] = useState<number | null>(null);
  const [approvalPending, setApprovalPending] = useState(false);

  const refreshApprovalPending = useCallback(async () => {
    if (!modelId) return;
    try {
      const instance = await findByBiz(MODEL_PUBLISH_FLOW_CODE, MODEL_PUBLISH_BIZ_TYPE, modelId);
      setApprovalPending(instance?.status === 'PENDING');
    } catch {
      /* 无审批读权限/审批中心不可用：不冻结面板 */
    }
  }, [modelId]);

  const loadVersions = useCallback(async () => {
    if (!modelId) return;
    setLoading(true);
    try {
      const list = await listModelingVersions(modelId);
      setVersions(list ?? []);
    } catch {
      setVersions([]);
    } finally {
      setLoading(false);
    }
  }, [modelId]);

  useEffect(() => {
    void loadVersions();
    void refreshApprovalPending();
  }, [loadVersions, refreshApprovalPending]);

  // 发布联动(01):直发/审批回调发布后刷新版本列表并解除在途冻结
  useEffect(() => {
    const onPublished = () => {
      void loadVersions();
      void refreshApprovalPending();
    };
    window.addEventListener(MODELING_PUBLISHED_EVENT, onPublished);
    return () => window.removeEventListener(MODELING_PUBLISHED_EVENT, onPublished);
  }, [loadVersions, refreshApprovalPending]);

  const handleViewDetail = async (versionNo: number) => {
    if (!modelId) return;
    if (expandedVersionNo === versionNo) {
      setExpandedVersionNo(null);
      setDetail(null);
      return;
    }
    setExpandedVersionNo(versionNo);
    setDetailLoading(true);
    try {
      const d = await getModelingVersion(modelId, versionNo);
      setDetail(d);
    } catch {
      message.error('加载版本详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleRollback = async (versionNo: number, andPublish = false) => {
    if (!modelId) return;
    setRollbackVersionNo(versionNo);
    try {
      await rollbackModelingVersion(modelId, versionNo);
      if (andPublish) {
        const v = await publishModelingModel(modelId);
        message.success(`已回滚并发布为 V${v.versionNo}`);
      } else {
        message.success(
          `已用 V${versionNo} 覆盖草稿（现为 DRAFT），需再次「发布」才生效`,
        );
      }
      await loadVersions();
      await refreshApprovalPending();
      setExpandedVersionNo(null);
      setDetail(null);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '回滚失败');
    } finally {
      setRollbackVersionNo(null);
    }
  };

  if (!modelId) return null;

  if (!loading && versions.length === 0) {
    return (
      <YakEmpty
        compact
        title="暂无发布版本"
        description="在「表结构」页面编辑字段后点击「发布」按钮即可生成版本快照"
      />
    );
  }

  const detailColumns = detail?.structure?.columns ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-[14px] text-[#667085]">
        <History size={15} />
        <span>共 {versions.length} 个版本，点击版本号查看结构快照</span>
        {approvalPending ? (
          <Tooltip title="存在在途 MODEL_PUBLISH 审批单，批准后按最新保存结构自动发布">
            <Tag color="processing" className="mr-0">
              发布审批中，回滚已冻结
            </Tag>
          </Tooltip>
        ) : null}
      </div>

      <div className="space-y-3">
        {versions.map((version) => {
          const isExpanded = expandedVersionNo === version.versionNo;
          return (
            <div
              key={version.id}
              className="rounded-[8px] border border-[#e7e9ec] p-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    className="rounded-[4px] bg-[#f2f4f7] px-2 py-0.5 text-[13px] font-semibold text-[#344054] transition-colors hover:bg-[#e4e7ec]"
                    onClick={() => void handleViewDetail(version.versionNo)}
                  >
                    V{version.versionNo}
                  </button>
                  <span className="text-[12px] text-[#667085]">
                    {formatDateTime(version.publishTime)}
                  </span>
                  {version.publishedBy ? (
                    <span className="text-[12px] text-[#98a2b3]">{version.publishedBy}</span>
                  ) : null}
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-[12px] text-[#98a2b3]">{version.columnCount} 字段</span>
                  <Tooltip title={version.checksum}>
                    <span className="font-mono text-[11px] text-[#98a2b3]">
                      {version.checksum?.slice(0, 8)}
                    </span>
                  </Tooltip>
                  <Popconfirm
                    title={`确认回滚到 V${version.versionNo}？`}
                    description="将用该版本内容覆盖当前草稿并置为 DRAFT，需再次发布才生效；线上消费方暂时仍读旧发布版。"
                    onConfirm={() => void handleRollback(version.versionNo)}
                    okText="恢复为草稿"
                    cancelText="取消"
                  >
                    <Tooltip title={approvalPending ? '发布审批在途，回滚已冻结' : '回滚到此版本（恢复为草稿）'}>
                      <button
                        type="button"
                        className="rounded-[4px] border border-[#d0d5dd] p-1 text-[#667085] transition-colors hover:bg-[#f2f4f7]"
                        disabled={rollbackVersionNo !== null || approvalPending}
                      >
                        <RotateCcw size={14} />
                      </button>
                    </Tooltip>
                  </Popconfirm>
                  <Popconfirm
                    title={`确认回滚并发布 V${version.versionNo}？`}
                    description="先用该版本内容覆盖草稿，随即发布为新版本并对消费方生效。"
                    onConfirm={() => void handleRollback(version.versionNo, true)}
                    okText="回滚并发布"
                    cancelText="取消"
                  >
                    <Tooltip title={approvalPending ? '发布审批在途，回滚已冻结' : '回滚并发布（直接生效）'}>
                      <button
                        type="button"
                        className="rounded-[4px] border border-[#91caff] bg-[#e6f4ff] p-1 text-[#1677ff] transition-colors hover:bg-[#bae0ff]"
                        disabled={rollbackVersionNo !== null || approvalPending}
                      >
                        <SendHorizontal size={14} />
                      </button>
                    </Tooltip>
                  </Popconfirm>
                </div>
              </div>

              {isExpanded && (
                <div className="mt-3 border-t border-[#eaecf0] pt-3">
                  {detailLoading ? (
                    <div className="py-4 text-center text-[12px] text-[#98a2b3]">加载中…</div>
                  ) : detail ? (
                    <Table
                      size="small"
                      dataSource={detailColumns}
                      rowKey="columnName"
                      pagination={false}
                      scroll={{ y: 300 }}
                      columns={[
                        { title: '#', width: 40, render: (_v, _r, i) => i + 1 },
                        { title: '字段名', dataIndex: 'columnName', width: 140 },
                        { title: '类型', dataIndex: 'dataType', width: 100 },
                        { title: '长度', dataIndex: 'length', width: 60, render: (v) => v ?? '-' },
                        { title: '小数', dataIndex: 'scale', width: 50, render: (v) => v ?? '-' },
                        { title: '可空', dataIndex: 'nullable', width: 50, render: (v) => (v ? '是' : '否') },
                        { title: '注释', dataIndex: 'comment', ellipsis: true },
                      ]}
                    />
                  ) : (
                    <div className="py-4 text-center text-[12px] text-[#98a2b3]">
                      未能加载结构详情
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default ModelVersionPanel;
