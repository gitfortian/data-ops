import { Alert, Button, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  inspectPhysicalDataDevHandoff, listModelingVersions,
  type PhysicalDataDevHandoffReadiness,
} from '@/services/modeling/api';
import type { ModelingVersionSummary } from '@/services/modeling/types';

interface Props {
  modelId: number;
  open: boolean;
  onClose: () => void;
}

/** No cross-domain write or DataDev task creation here. */
export default function PhysicalDataDevHandoffPanel({ modelId, open, onClose }: Props) {
  const { currentProject } = useSecurityProject();
  const [versions, setVersions] = useState<ModelingVersionSummary[]>([]);
  const [versionNo, setVersionNo] = useState<number>();
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState<PhysicalDataDevHandoffReadiness>();

  useEffect(() => {
    let current = true;
    setReport(undefined);
    setVersions([]);
    setVersionNo(undefined);
    if (!open) return () => { current = false; };
    setVersionsLoading(true);
    void listModelingVersions(modelId).then((all) => {
      if (!current) return;
      setVersions(all ?? []);
      setVersionNo(all?.[0]?.versionNo);
    }).catch((error: unknown) => {
      if (current) message.error(error instanceof Error ? error.message : '获取发布版本失败');
    }).finally(() => {
      if (current) setVersionsLoading(false);
    });
    return () => { current = false; };
  }, [modelId, open, currentProject?.id]);

  useEffect(() => {
    let current = true;
    setReport(undefined);
    if (!open || !versionNo) return () => { current = false; };
    setLoading(true);
    void inspectPhysicalDataDevHandoff(modelId, versionNo).then((result) => {
      if (current && result.modelId === modelId && result.version.versionNo === versionNo) {
        setReport(result);
      }
    }).catch((error: unknown) => {
      if (current) message.error(error instanceof Error ? error.message
        : '版本或当前标准证据不可访问，不能认定交接已就绪');
    }).finally(() => {
      if (current) setLoading(false);
    });
    return () => { current = false; };
  }, [modelId, versionNo, open, currentProject?.id]);

  return (
    <Modal title="物理设计 → DataDev 版本交接证据（只读）"
      open={open} width={1050} destroyOnHidden onCancel={onClose}
      footer={<Button onClick={onClose}>关闭</Button>}>
      <Space wrap className="mb-3">
        <Typography.Text>选择精确已发布物理版本：</Typography.Text>
        <Select<number> value={versionNo} onChange={setVersionNo}
          style={{ width: 210 }} loading={versionsLoading}
          placeholder="选择版本" options={versions.map((v) => ({
            value: v.versionNo, label: `V${v.versionNo} · ${v.columnCount} 列`,
          }))} />
      </Space>
      {!versionsLoading && !versions.length && (
        <Alert type="info" showIcon message="此模型尚无已发布物理版本，无法生成精确交接证据。" />
      )}
      {loading && <Typography.Paragraph type="secondary">正在重读冻结版本与当前 Semantic 标准证据…</Typography.Paragraph>}
      {report && <>
        <Alert type="warning" showIcon className="mb-3"
          message="仅是 Modeling 的交接设计预检，不是 DataDev 建任务许可"
          description="DR-07 持久 ModelVersionRef、DataDev 运行环境/插件和任务权限尚未经独立校验；不会创建任务、执行 SQL 或更新任何版本。" />
        <Space wrap className="mb-3">
          <Tag>{report.layerCode} · V{report.version.versionNo}</Tag>
          <Tag color={report.designStatus === 'DESIGN_REVIEWABLE' ? 'green' : 'warning'}>
            {report.designStatus === 'DESIGN_REVIEWABLE' ? '设计可人工审阅' : '设计证据待处理'}
          </Tag>
          {!report.selectedLatestVersion && <Tag color="orange">不是当前发布指针</Tag>}
          <Tag>冻结列 {report.frozenColumnCount}</Tag>
          <Tag>历史缺标准引用 {report.frozenUnboundColumnCount}</Tag>
          <Tag color="orange">DataDev：未授权建任务</Tag>
        </Space>
        <Typography.Paragraph type="secondary">
          冻结结构证据：
          <Typography.Text copyable={{ text: report.version.frozenStructureSha256 }}>
            {report.version.frozenStructureSha256.slice(0, 20)}…
          </Typography.Text>
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary">
          本次交接核验指纹：
          <Typography.Text copyable={{ text: report.inspectionSha256 }}>
            {report.inspectionSha256.slice(0, 20)}…
          </Typography.Text>
        </Typography.Paragraph>
        {report.blockers.map((reason, i) => (
          <Typography.Paragraph key={i} type="danger" className="!mb-1">{reason}</Typography.Paragraph>
        ))}
        <Typography.Title level={5}>交接证据与缺失的授权合同</Typography.Title>
        <Table size="small" rowKey="code" pagination={false}
          dataSource={report.dependencies} columns={[
            { title: '证据项', dataIndex: 'code', width: 220 },
            { title: '当前状态', dataIndex: 'status', width: 175,
              render: (value: string) => <Tag color={['PINNED', 'LATEST', 'MATCHES_CURRENT', 'COVERED_NOW'].includes(value)
                ? 'green' : 'orange'}>{value}</Tag> },
            { title: '解释与责任边界', dataIndex: 'explanation' },
          ]} />
        <Typography.Title level={5}>下一步（仍需人工处理）</Typography.Title>
        {report.nextActions.map((action, i) => (
          <Typography.Paragraph key={i} className="!mb-1">{i + 1}. {action}</Typography.Paragraph>
        ))}
      </>}
    </Modal>
  );
}
