import { useLocation, useParams } from '@umijs/max';
import { Popconfirm, Spin, Table, Tag, Tooltip, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { ChevronDown, RefreshCw, RotateCcw, SendHorizontal } from 'lucide-react';
import { YakButton, YakEmpty } from '@/components/ui';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { useLatestOperation, useResourceScope } from '@/hooks/useLatestOperation';
import { MODELING_PUBLISHED_EVENT } from '@/pages/modeling/constants';
import { findByBiz } from '@/services/approval/api';
import { getModelingVersion, listModelingVersions, publishModelingModel, rollbackModelingVersion } from '@/services/modeling/api';
import type { ModelingVersionDetail, ModelingVersionSummary } from '@/services/modeling/types';
import ModelStructureReviewPanel from './ModelStructureReviewPanel';
import './ModelVersionPanel.less';

const formatDateTime = (value: string) => value?.replace('T', ' ').slice(0, 19) ?? '-';
const MODEL_PUBLISH_FLOW_CODE = 'MODEL_PUBLISH';
const MODEL_PUBLISH_BIZ_TYPE = 'MODEL';

/** Published snapshots and explicit recovery actions in the model workspace. */
const ModelVersionPanel: React.FC = () => {
  const { id: modelId } = useParams<{ id?: string }>();
  const location = useLocation();
  const { currentProject } = useSecurityProject();
  const resource = JSON.stringify([currentProject?.id, modelId]);
  const beginVersions = useLatestOperation(resource);
  const beginDetail = useLatestOperation(resource);
  const beginApproval = useLatestOperation(resource);
  const captureResource = useResourceScope(resource);
  const [versions, setVersions] = useState<ModelingVersionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedVersionNo, setExpandedVersionNo] = useState<number | null>(null);
  const [detail, setDetail] = useState<ModelingVersionDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [rollbackVersionNo, setRollbackVersionNo] = useState<number | null>(null);
  const [approvalPending, setApprovalPending] = useState(false);

  const refreshApprovalPending = useCallback(async () => {
    if (!modelId) return;
    const isCurrent = beginApproval();
    try {
      const instance = await findByBiz(MODEL_PUBLISH_FLOW_CODE, MODEL_PUBLISH_BIZ_TYPE, modelId);
      if (isCurrent()) setApprovalPending(instance?.status === 'PENDING');
    } catch {
      /* Approval availability does not change the existing recovery contract. */
    }
  }, [modelId, currentProject?.id, beginApproval]);

  const loadVersions = useCallback(async () => {
    if (!modelId) return;
    const isCurrent = beginVersions();
    setLoading(true);
    try {
      const list = await listModelingVersions(modelId);
      if (isCurrent()) setVersions(list ?? []);
    } catch {
      if (isCurrent()) setVersions([]);
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [modelId, currentProject?.id, beginVersions]);

  useEffect(() => {
    setVersions([]);
    setExpandedVersionNo(null);
    setDetail(null);
    setRollbackVersionNo(null);
    setApprovalPending(false);
    void loadVersions();
    void refreshApprovalPending();
  }, [loadVersions, refreshApprovalPending]);

  useEffect(() => {
    const onPublished = (event: Event) => {
      const target = (event as CustomEvent<{ modelId?: string | number }>).detail?.modelId;
      if (target != null && String(target) !== modelId) return;
      void loadVersions();
      void refreshApprovalPending();
    };
    window.addEventListener(MODELING_PUBLISHED_EVENT, onPublished);
    return () => window.removeEventListener(MODELING_PUBLISHED_EVENT, onPublished);
  }, [modelId, loadVersions, refreshApprovalPending]);

  const handleViewDetail = async (versionNo: number) => {
    if (!modelId) return;
    const isCurrent = beginDetail();
    setDetail(null);
    if (expandedVersionNo === versionNo) {
      setExpandedVersionNo(null);
      setDetailLoading(false);
      return;
    }
    setExpandedVersionNo(versionNo);
    setDetailLoading(true);
    try {
      const snapshot = await getModelingVersion(modelId, versionNo);
      if (isCurrent()) setDetail(snapshot);
    } catch {
      if (isCurrent()) message.error('加载版本详情失败');
    } finally {
      if (isCurrent()) setDetailLoading(false);
    }
  };

  const handleRollback = async (versionNo: number, andPublish = false) => {
    if (!modelId) return;
    const isCurrent = captureResource();
    setRollbackVersionNo(versionNo);
    try {
      await rollbackModelingVersion(modelId, versionNo);
      if (!isCurrent()) return;
      if (andPublish) {
        const version = await publishModelingModel(modelId);
        if (!isCurrent()) return;
        message.success(`已回滚并发布为 V${version.versionNo}`);
      } else {
        message.success(`已恢复 V${versionNo} 为草稿，再次发布后生效`);
      }
      await loadVersions();
      if (!isCurrent()) return;
      await refreshApprovalPending();
      if (!isCurrent()) return;
      beginDetail();
      setExpandedVersionNo(null);
      setDetail(null);
    } catch (error) {
      if (isCurrent()) message.error(error instanceof Error ? error.message : '回滚失败');
    } finally {
      if (isCurrent()) setRollbackVersionNo(null);
    }
  };

  if (!modelId) return null;
  const orderedVersions = [...versions].sort((left, right) => right.versionNo - left.versionNo);
  const showComparison = versions.length > 0 || new URLSearchParams(location.search).has('reviewVersion');

  return (
    <div className="model-version-management">
      <header className="model-version-header">
        <div className="model-version-heading">
          <h2>版本管理</h2><span>{versions.length} 个发布版本</span>
          {approvalPending ? <Tooltip title="发布审批在途，暂不可回滚"><Tag color="processing">发布审批中</Tag></Tooltip> : null}
        </div>
        <YakButton size="small" icon={<RefreshCw size={13} />} loading={loading}
          onClick={() => { void loadVersions(); void refreshApprovalPending(); }}>刷新</YakButton>
      </header>

      <Spin spinning={loading}>
        {versions.length ? (
          <div className="model-version-list">
            <div className="model-version-list-head" aria-hidden>
              <span>发布版本</span><span>发布时间</span><span>发布人</span><span>字段数</span><span>操作</span>
            </div>
            {orderedVersions.map((version) => {
              const isExpanded = expandedVersionNo === version.versionNo;
              return (
                <div key={version.id} className="model-version-item">
                  <div className="model-version-row">
                    <div className="model-version-identity">
                      <button type="button" className="model-version-number"
                        aria-label={`查看版本 V${version.versionNo}`} aria-expanded={isExpanded}
                        aria-controls={`model-version-${modelId}-${version.versionNo}`}
                        onClick={() => void handleViewDetail(version.versionNo)}>
                        V{version.versionNo}<ChevronDown size={13} className={isExpanded ? 'is-expanded' : ''} aria-hidden />
                      </button>
                      {version.versionNo === orderedVersions[0]?.versionNo ? <span className="model-version-latest">最新版本</span> : null}
                    </div>
                    <time className="model-version-time" dateTime={version.publishTime}>{formatDateTime(version.publishTime)}</time>
                    <span className="model-version-publisher">{version.publishedBy || '—'}</span>
                    <span className="model-version-field-count">{version.columnCount}</span>
                    <div className="model-version-actions">
                      <Popconfirm title={`恢复 V${version.versionNo} 为草稿？`}
                        description="将覆盖当前草稿，再次发布后生效。"
                        onConfirm={() => void handleRollback(version.versionNo)} okText="恢复为草稿" cancelText="取消">
                        <Tooltip title={approvalPending ? '发布审批在途，回滚已冻结' : '用此版本覆盖当前草稿'}>
                          <button type="button" className="model-version-action"
                            aria-label={`回滚到 V${version.versionNo}（恢复为草稿）`}
                            disabled={rollbackVersionNo !== null || approvalPending}>
                            <RotateCcw size={13} aria-hidden />恢复草稿
                          </button>
                        </Tooltip>
                      </Popconfirm>
                      <Popconfirm title={`确认回滚并发布 V${version.versionNo}？`}
                        description="先用该版本覆盖草稿，再发布为新版本并生效。"
                        onConfirm={() => void handleRollback(version.versionNo, true)} okText="回滚并发布" cancelText="取消">
                        <Tooltip title={approvalPending ? '发布审批在途，回滚已冻结' : '恢复此版本并立即发布'}>
                          <button type="button" className="model-version-action"
                            aria-label={`回滚并发布 V${version.versionNo}`}
                            disabled={rollbackVersionNo !== null || approvalPending}>
                            <SendHorizontal size={13} aria-hidden />回滚并发布
                          </button>
                        </Tooltip>
                      </Popconfirm>
                    </div>
                  </div>
                  {isExpanded ? (
                    <div id={`model-version-${modelId}-${version.versionNo}`} className="model-version-snapshot">
                      <div className="model-version-snapshot-head">
                        <strong>V{version.versionNo} 结构快照</strong>
                        <Tooltip title={version.checksum}><span>校验码 {version.checksum?.slice(0, 12) || '—'}</span></Tooltip>
                      </div>
                      {detailLoading ? <div className="model-version-detail-empty">加载中…</div>
                        : detail?.version.versionNo === version.versionNo ? (
                          <Table size="small" dataSource={detail.structure.columns ?? []} rowKey="columnName"
                            pagination={false} scroll={{ y: 300, x: 660 }} columns={[
                              { title: '#', width: 45, render: (_value, _record, index) => index + 1 },
                              { title: '字段名', dataIndex: 'columnName', width: 150 },
                              { title: '类型', dataIndex: 'dataType', width: 110 },
                              { title: '长度', dataIndex: 'length', width: 65, render: (value) => value ?? '—' },
                              { title: '小数位', dataIndex: 'scale', width: 65, render: (value) => value ?? '—' },
                              { title: '可空', dataIndex: 'nullable', width: 60, render: (value) => value ? '是' : '否' },
                              { title: '注释', dataIndex: 'comment', ellipsis: true },
                            ]} />
                        ) : <div className="model-version-detail-empty">未能加载结构详情</div>}
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
        ) : <YakEmpty compact title={loading ? '加载版本中…' : '暂无发布版本'} description={loading ? undefined : '发布模型后可在此查看版本。'} />}
      </Spin>

      {showComparison ? <ModelStructureReviewPanel modelId={modelId} search={location.search} /> : null}
    </div>
  );
};

export default ModelVersionPanel;
