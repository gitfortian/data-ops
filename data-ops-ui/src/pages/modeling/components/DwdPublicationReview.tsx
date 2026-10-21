import { Alert, Button, Collapse, Space, Table, Tag, Typography } from 'antd';
import type { ModelPublicationReadiness } from '@/services/modeling/api';

interface Props {
  readiness: ModelPublicationReadiness | null;
  onOpenStandards: () => void;
  onOpenLogical: () => void;
}

const statusLabels = { VALID: '已核对', BLOCKED: '需调整', UNKNOWN: '待核实' };
const mappingLabels: Record<string, string> = {
  FROZEN_SAVED_DRAFT: '映射版本已保存', MISSING: '尚无逻辑映射',
  NOT_FROZEN: '尚未保存映射版本', STALE: '映射版本需更新',
  UNKNOWN: '映射待核实', UNAVAILABLE: '映射暂不可读取',
};

/** Presents the existing publication decision without changing its authorization. */
export default function DwdPublicationReview({ readiness, onOpenStandards, onOpenLogical }: Props) {
  const mapping = readiness?.mapping;
  return <section className="dwd-publication-review" aria-label="DWD 发布条件核对">
    {!readiness ? <Alert type="error" showIcon message="发布条件暂不可读取，请重新打开发布确认" /> : <>
      {!readiness.authorizedForPublication && <Alert type="warning" showIcon className="mb-3"
        message="当前设计尚未满足发布条件"
        description={readiness.findings.length ? <ul className="!mb-0 !pl-4">
          {readiness.findings.map((finding, index) => <li key={index}>{finding}</li>)}
        </ul> : '请核对字段标准与逻辑映射，处理后重新发起发布。'} />}
      <div className="dwd-publication-checks">
        <div>
          <Space wrap><Typography.Text strong>字段标准</Typography.Text>
            <Tag>{statusLabels[readiness.standardsVerdict]}</Tag></Space>
          <Typography.Paragraph type="secondary" className="!mt-1 !mb-2">
            已核对 {readiness.columns.length} 个字段
            {readiness.columns.some(column => column.status !== 'VALID')
              ? `，${readiness.columns.filter(column => column.status !== 'VALID').length} 项需处理` : ''}。
          </Typography.Paragraph>
          <Button size="small" onClick={onOpenStandards}>查看标准字段</Button>
        </div>
        <div>
          <Space wrap><Typography.Text strong>逻辑映射</Typography.Text>
            <Tag>{mappingLabels[mapping?.status || 'UNKNOWN']}</Tag></Space>
          <Typography.Paragraph type="secondary" className="!mt-1 !mb-2">
            {mapping?.finding || '请选择逻辑设计版本并核对字段映射。'}
          </Typography.Paragraph>
          <Button size="small" onClick={onOpenLogical}>查看逻辑依据</Button>
        </div>
      </div>
      <Collapse ghost className="mt-3" items={[{ key: 'details', label: '发布核验详情', children: <>
        <Typography.Paragraph type="secondary">发布资格：{readiness.authorizationStatus}</Typography.Paragraph>
        {readiness.authorizedForPublication && readiness.findings.map((finding, index) =>
          <Typography.Paragraph type="secondary" key={index}>{finding}</Typography.Paragraph>)}
        {mapping?.mappingRevisionId ? <Typography.Paragraph type="secondary">
          逻辑模型 #{mapping.logicalModelId} · V{mapping.logicalVersionNo} · 映射版本 #{mapping.mappingRevisionId}
        </Typography.Paragraph> : null}
        <Table size="small" rowKey="columnName" pagination={{ pageSize: 8, hideOnSinglePage: true }} scroll={{ x: 560 }}
          dataSource={readiness.columns} columns={[
            { title: '物理字段', dataIndex: 'columnName' },
            { title: '类型', dataIndex: 'physicalType', width: 100 },
            { title: '标准字段 ID', dataIndex: 'standardFieldId', width: 110, render: value => value ?? '未关联' },
            { title: '核对结果', dataIndex: 'status', width: 100,
              render: (value: keyof typeof statusLabels) => <Tag>{statusLabels[value]}</Tag> },
            { title: '详情', dataIndex: 'safeReasonCode', render: value => value || '—' },
          ]} />
        {readiness.standardEvidenceSha256 && <Typography.Paragraph type="secondary" className="!mt-3">
          标准摘要：<Typography.Text copyable>{readiness.standardEvidenceSha256}</Typography.Text>
          {' · '}修订 {readiness.semanticRevision ?? '未知'}
        </Typography.Paragraph>}
        {mapping?.mappingContentSha256 && <Typography.Paragraph type="secondary">
          映射摘要：<Typography.Text copyable>{mapping.mappingContentSha256}</Typography.Text>
        </Typography.Paragraph>}
      </> }]} />
    </>}
  </section>;
}
