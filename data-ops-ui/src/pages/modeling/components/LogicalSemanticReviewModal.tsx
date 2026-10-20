import { Alert, Button, Collapse, Modal, Space, Table, Tabs, Tag, Typography } from 'antd';
import type { LogicalVersionSemanticReview } from '@/services/modeling/logical';
import type { GovernanceRecheckResult } from '../governanceRecheck';
import { LOGICAL_CARDINALITY } from '../editor/logicalDesign';

type RepairTarget = { kind: 'attribute'; entityId: number; attributeId: number }
  | { kind: 'relation'; relationId: number };

interface Props {
  review?: LogicalVersionSemanticReview;
  comparison?: GovernanceRecheckResult;
  loading?: boolean;
  canEdit?: boolean;
  onClose: () => void;
  onReload: (versionNo: number) => void | Promise<void>;
  onRepair: (target: RepairTarget) => void | Promise<void>;
}

const changeLabels: Record<string, string> = {
  ADDED_NOW: '新增', REMOVED_NOW: '已移除', CHANGED_NOW: '已修改',
};
const objectLabels: Record<string, string> = {
  ENTITY: '实体', ATTRIBUTE: '属性', RELATION: '关系', MODEL: '模型',
};
const standardLabels: Record<string, string> = {
  ENABLED_NOW: '有效', PENDING_STANDARD: '未关联', INVALID_STANDARD: '需调整',
};

/** Shows owner results and links repairs to the current draft, never to the frozen snapshot. */
export default function LogicalSemanticReviewModal({
  review, comparison, loading, canEdit = true, onClose, onReload, onRepair,
}: Props) {
  const attributeIssues = review?.attributes.filter(row => row.standardStatus !== 'ENABLED_NOW').length ?? 0;
  const relationIssues = review?.relations.filter(row => row.status === 'CARDINALITY_PENDING').length ?? 0;
  const defaultTab = review?.changes.length ? 'changes' : attributeIssues ? 'standards' : relationIssues ? 'relations' : 'changes';

  return <Modal title="设计版本核对" width={1000} open={Boolean(review)} destroyOnHidden
    className="modeling-modal"
    classNames={{ content: 'modeling-modal-content', header: 'modeling-modal-header',
      body: 'modeling-modal-body', footer: 'modeling-modal-footer' }}
    footer={<Button onClick={onClose}>关闭</Button>} onCancel={onClose}>
    {review && <>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <Space wrap>
            <Typography.Text strong>设计版本 V{review.versionNo}</Typography.Text>
            <Tag>{review.designComparison === 'MATCHES_CURRENT' ? '与当前设计一致' : '当前设计有变更'}</Tag>
          </Space>
          <Typography.Paragraph type="secondary" className="!mb-0 !mt-2">
            实体 {review.entityCount} · 属性 {review.attributeCount} · 关系 {review.relationCount}
            {' · '}修正操作将定位当前草稿，历史版本保持不变。
          </Typography.Paragraph>
        </div>
        <Button loading={loading} onClick={() => void onReload(review.versionNo)}>重新核对</Button>
      </div>
      {comparison && <Alert showIcon className="mb-3"
        type={comparison.state === 'WRONG_OWNER' ? 'error' : 'info'}
        message={comparison.state === 'CHANGED' ? '核对结果已更新'
          : comparison.state === 'UNCHANGED' ? '核对结果与上次一致' : '版本身份不一致，无法比较'}
        description={comparison.explanation} />}
      {review.blockers.length > 0 && <Alert type="warning" showIcon className="mb-3"
        message={`有 ${review.blockers.length} 项需要处理`}
        description={<ul className="!mb-0 !pl-4">{review.blockers.map((reason, index) => <li key={index}>{reason}</li>)}</ul>} />}

      <Tabs key={`${review.logicalModelId}-${review.versionNo}`} defaultActiveKey={defaultTab} items={[
        { key: 'changes', label: `设计差异 (${review.changes.length})`, children:
          <Table size="small" rowKey={row => `${row.objectType}-${row.objectId}`} pagination={{ pageSize: 8, hideOnSinglePage: true }}
            scroll={{ x: 650 }} locale={{ emptyText: '该版本与当前草稿的实体、属性和关系一致' }} dataSource={review.changes}
            columns={[
              { title: '类型', dataIndex: 'objectType', width: 80,
                render: (value: string) => objectLabels[value] || value },
              { title: '业务对象', dataIndex: 'label', width: 150 },
              { title: '变化', dataIndex: 'status', width: 90,
                render: (value: string) => <Tag>{changeLabels[value] || value}</Tag> },
              { title: '版本内容', dataIndex: 'frozenValue', ellipsis: true,
                render: (value?: string) => value ? <Typography.Text ellipsis={{ tooltip: value }}>{value}</Typography.Text> : '—' },
              { title: '当前内容', dataIndex: 'currentValue', ellipsis: true,
                render: (value?: string) => value ? <Typography.Text ellipsis={{ tooltip: value }}>{value}</Typography.Text> : '—' },
            ]} /> },
        { key: 'standards', label: `标准字段${attributeIssues ? ` · 待处理 ${attributeIssues}` : ''}`, children:
          <Table size="small" rowKey="attributeId" pagination={{ pageSize: 8, hideOnSinglePage: true }}
            scroll={{ x: 700 }} locale={{ emptyText: '该版本暂无属性' }} dataSource={review.attributes} columns={[
              { title: '实体 / 属性', key: 'attribute', width: 160,
                render: (_, row) => <div><Typography.Text>{row.attributeName}</Typography.Text>
                  <div className="text-[12px] text-[#667085]">{row.entityName}</div></div> },
              { title: '标准字段', key: 'standard', width: 160,
                render: (_, row) => row.stdFieldId == null ? '未关联'
                  : <Typography.Text title={`标准字段 ID：${row.stdFieldId}`}>{row.standardName || `标准字段 #${row.stdFieldId}`}</Typography.Text> },
              { title: '当前状态', dataIndex: 'standardStatus', width: 100,
                render: (value: string) => <Tag color={value === 'ENABLED_NOW' ? 'success' : 'default'}>{standardLabels[value] || value}</Tag> },
              { title: '核对结果', dataIndex: 'explanation' },
              { title: '操作', key: 'repair', width: 120,
                render: (_, row) => row.standardStatus !== 'ENABLED_NOW' ? <Button type="link" size="small" disabled={loading || !canEdit}
                  onClick={() => void onRepair({ kind: 'attribute', entityId: row.entityId, attributeId: row.attributeId })}>定位当前属性</Button> : '—' },
            ]} /> },
        { key: 'relations', label: `实体关系${relationIssues ? ` · 待确认 ${relationIssues}` : ''}`, children:
          <Table size="small" rowKey="relationId" pagination={{ pageSize: 8, hideOnSinglePage: true }}
            scroll={{ x: 650 }} locale={{ emptyText: '该版本暂无实体关系' }} dataSource={review.relations} columns={[
              { title: '实体关系', key: 'endpoints', width: 200,
                render: (_, row) => `${row.sourceEntity} → ${row.targetEntity}` },
              { title: '关系基数', dataIndex: 'cardinality', width: 100,
                render: (value: string) => LOGICAL_CARDINALITY[value as keyof typeof LOGICAL_CARDINALITY] || value },
              { title: '状态', dataIndex: 'status', width: 90,
                render: (value: string) => <Tag>{value === 'CARDINALITY_PENDING' ? '待确认'
                  : value === 'DECLARED_NOT_CONFIRMED' ? '已声明' : value}</Tag> },
              { title: '核对结果', dataIndex: 'explanation' },
              { title: '操作', key: 'repair', width: 120,
                render: (_, row) => <Button type="link" size="small" disabled={loading || !canEdit}
                  onClick={() => void onRepair({ kind: 'relation', relationId: row.relationId })}>定位当前关系</Button> },
            ]} /> },
      ]} />
      <Collapse ghost className="mt-3" items={[{ key: 'details', label: '核验详情', children: <>
        <Typography.Paragraph type="secondary">
          本报告对照设计版本与当前语义引用，不改变模型审批或发布状态。
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary">逻辑模型 ID：{review.logicalModelId}</Typography.Paragraph>
        <Typography.Paragraph type="secondary">版本内容摘要：<Typography.Text copyable>{review.frozenSnapshotSha256}</Typography.Text></Typography.Paragraph>
        {review.limitations.map((reason, index) => <Typography.Paragraph type="secondary" key={index} className="!mb-1">{reason}</Typography.Paragraph>)}
      </> }]} />
    </>}
  </Modal>;
}
