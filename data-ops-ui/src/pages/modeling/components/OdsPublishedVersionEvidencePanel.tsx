import { Alert, Button, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  getOdsPublishedVersionEvidence, listModelingVersions,
  type OdsPublishedVersionEvidence,
} from '@/services/modeling/api';
import type { ModelingVersionSummary } from '@/services/modeling/types';

interface Props {
  open: boolean;
  modelId: number;
  onClose: () => void;
}

/** Read-only historical version versus today's source evidence, never a publish or deploy action. */
export default function OdsPublishedVersionEvidencePanel({ open, modelId, onClose }: Props) {
  const { currentProject } = useSecurityProject();
  const [versions, setVersions] = useState<ModelingVersionSummary[]>([]);
  const [versionNo, setVersionNo] = useState<number>();
  const [report, setReport] = useState<OdsPublishedVersionEvidence>();
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [reviewLoading, setReviewLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setVersions([]);
    setVersionNo(undefined);
    setReport(undefined);
    if (!open) return () => { active = false; };
    setVersionsLoading(true);
    void listModelingVersions(modelId).then((list) => {
      if (!active) return;
      setVersions(list ?? []);
      setVersionNo(list?.[0]?.versionNo);
    }).catch((err: unknown) => {
      if (active) message.error(err instanceof Error ? err.message : '读取模型历史版本失败');
    }).finally(() => {
      if (active) setVersionsLoading(false);
    });
    return () => { active = false; };
  }, [open, modelId, currentProject?.id]);

  useEffect(() => {
    let active = true;
    setReport(undefined);
    if (!open || versionNo == null) return () => { active = false; };
    setReviewLoading(true);
    void getOdsPublishedVersionEvidence(modelId, versionNo).then((data) => {
      if (active && data.modelId === modelId && data.versionNo === versionNo) {
        setReport(data);
      }
    }).catch((err: unknown) => {
      if (active) message.error(err instanceof Error ? err.message
        : '当前来源不可访问，不能把异常当作历史版本已核验');
    }).finally(() => {
      if (active) setReviewLoading(false);
    });
    return () => { active = false; };
  }, [open, modelId, versionNo, currentProject?.id]);

  return (
    <Modal open={open} width={1020} title="ODS 设计版本与当前来源证据核对（只读）"
      footer={<Button onClick={onClose}>关闭</Button>} onCancel={onClose}
      destroyOnHidden>
      <Space wrap className="mb-3">
        <Typography.Text>指定冻结结构版本：</Typography.Text>
        <Select<number> style={{ width: 210 }} loading={versionsLoading}
          placeholder={versionsLoading ? '读取历史版本' : '选择发布版本'}
          value={versionNo} onChange={setVersionNo}
          options={versions.map((version) => ({
            value: version.versionNo,
            label: `V${version.versionNo} · ${version.columnCount} 列`,
          }))} />
        {report && <Tag color={report.structureStatus === 'MATCHES_CURRENT' ? 'green' : 'warning'}>
          {report.structureStatus === 'MATCHES_CURRENT'
            ? '当前结构与该版本内容一致' : '当前结构与该版本存在差异'}
        </Tag>}
      </Space>
      {!versionsLoading && versions.length === 0 && (
        <Alert type="info" showIcon message="当前模型尚无已发布设计版本，不存在可核验的冻结结构。" />
      )}
      {reviewLoading && <Typography.Paragraph type="secondary">正在读取精确版本与当前授权来源证据…</Typography.Paragraph>}
      {report && <>
        <Alert type="warning" showIcon className="mb-3"
          message="历史来源映射未随该结构版本冻结，无法证明发布当时的来源身份"
          description="这里的「当前来源一致」只反映本次实时读取，不是历史发布版本的来源快照，也不是建库、运行或数据质量验证结果。" />
        <Space wrap className="mb-3">
          <Tag>物理版本 V{report.versionNo}</Tag>
          {!report.latestPublishedVersion && <Tag color="orange">历史版本（非最新）</Tag>}
          <Tag>冻结物理列 {report.frozenColumnCount}</Tag>
          <Tag>当前物理列 {report.currentColumnCount}</Tag>
          <Tag color={report.currentSourceStatus === 'CONSISTENT_NOW' ? 'green' : 'warning'}>
            当前来源：{report.currentSourceStatus}
          </Tag>
          <Tag color="orange">历史来源：未冻结</Tag>
        </Space>
        {report.currentSourceEvidenceSha256 && (
          <Typography.Paragraph type="secondary">
            当前来源证据指纹：
            <Typography.Text copyable={{ text: report.currentSourceEvidenceSha256 }}>
              {report.currentSourceEvidenceSha256.slice(0, 20)}…
            </Typography.Text>
          </Typography.Paragraph>
        )}
        <Table size="small" rowKey={(row) => row.column} pagination={{ pageSize: 20 }}
          dataSource={report.differences} locale={{ emptyText: '当前表结构与选中冻结版本一致；来源证据须单独审阅' }}
          columns={[
            { title: '对象/字段', dataIndex: 'column' },
            { title: '差异类型', dataIndex: 'status', render: (v: string) => <Tag color="warning">{v}</Tag> },
            { title: '冻结版本类型', dataIndex: 'frozenType', render: (v?: string) => v || '—' },
            { title: '当前类型', dataIndex: 'currentType', render: (v?: string) => v || '—' },
          ]} />
        {report.limitations.map((limitation, index) => (
          <Typography.Paragraph key={index} type="secondary" className="!mb-1">
            {limitation}
          </Typography.Paragraph>
        ))}
      </>}
    </Modal>
  );
}
