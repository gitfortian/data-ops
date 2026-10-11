import { Alert, Button, Modal, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  inspectPhysicalStandardCoverage,
  type PhysicalStandardCoverageInspection,
} from '@/services/modeling/api';

interface Props {
  modelId: number;
  open: boolean;
  onClose: () => void;
  /** Navigation only; report column identity is revalidated in the editor. */
  onLocateColumn?: (column: PhysicalStandardCoverageInspection['columns'][number]) => void;
}

/** Read-only evidence of current physical-field standards. NOT a publish gate. */
export default function PhysicalStandardCoveragePanel({ modelId, open, onClose, onLocateColumn }: Props) {
  const { currentProject } = useSecurityProject();
  const [report, setReport] = useState<PhysicalStandardCoverageInspection>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setReport(undefined);
    if (!open) return () => { active = false; };
    setLoading(true);
    void inspectPhysicalStandardCoverage(modelId).then((result) => {
      if (active && result.modelId === modelId) setReport(result);
    }).catch((error: unknown) => {
      if (active) message.error(error instanceof Error ? error.message
        : '标准目录或项目模型不可访问，不能将错误视作覆盖合格');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [open, modelId, currentProject?.id]);

  return (
    <Modal title="DWD/DIM 当前字段标准覆盖与治理缺口（只读）"
      width={1080} open={open} destroyOnHidden
      footer={<Button onClick={onClose}>关闭</Button>} onCancel={onClose}>
      {loading && <Typography.Paragraph type="secondary">正在核验已保存的模型与正式标准字段…</Typography.Paragraph>}
      {report && <>
        <Alert showIcon className="mb-3"
          type={report.status === 'COVERED_NOW' ? 'info' : 'warning'}
          message={report.status === 'COVERED_NOW'
            ? '当前模型所有字段已关联可用标准；此结果仅供人工设计审阅'
            : '当前物理设计存在标准覆盖或业务含义待核实项'}
          description="核验的是已保存的当前物理结构及 Semantic 标准引用。尚未批准非 ODS 发布强门禁及技术列豁免，不会自动改动任何发布状态。" />
        <Space wrap className="mb-3">
          <Tag>{report.layerCode} · {report.modelStatus}</Tag>
          <Tag>正式业务过程：{report.processName || '未绑定或不可访问'}</Tag>
          <Tag>物理列 {report.columnCount}</Tag>
          <Tag color="green">当前可用 {report.enabledCount}</Tag>
          <Tag color={report.missingCount ? 'orange' : 'default'}>缺少标准 {report.missingCount}</Tag>
          <Tag color={report.invalidCount ? 'red' : 'default'}>失效引用 {report.invalidCount}</Tag>
          <Tag color={report.reviewCount ? 'orange' : 'default'}>聚合待确认 {report.reviewCount}</Tag>
        </Space>
        <Typography.Paragraph type="secondary">
          本次核验指纹：
          <Typography.Text copyable={{ text: report.evidenceSha256 }}>
            {report.evidenceSha256.slice(0, 20)}…
          </Typography.Text>
        </Typography.Paragraph>
        {report.findings.map((finding, i) => (
          <Typography.Paragraph key={i} type="danger" className="!mb-1">{finding}</Typography.Paragraph>
        ))}
        <Table size="small" rowKey="columnId" pagination={{ pageSize: 15 }}
          dataSource={report.columns} columns={[
            { title: '物理列', dataIndex: 'columnName', width: 130 },
            { title: '物理类型', dataIndex: 'dataType', width: 105 },
            { title: '正式标准 ID', dataIndex: 'stdFieldId', width: 120,
              render: (id?: number) => id ?? '未绑定' },
            { title: '当前标准名称', dataIndex: 'standardName', width: 150,
              render: (name?: string) => name || '—' },
            { title: 'Semantic 角色', dataIndex: 'semanticRole', width: 115,
              render: (role?: string) => role || '—' },
            { title: '物理分析角色', dataIndex: 'physicalRole', width: 115,
              render: (role?: string) => role || '—' },
            { title: '覆盖证据', dataIndex: 'status', width: 155,
              render: (status: string) => <Tag color={status === 'ENABLED_NOW'
                ? 'green' : status === 'INVALID_STANDARD' ? 'red' : 'orange'}>{status}</Tag> },
            { title: '为何需要复核', dataIndex: 'explanation' },
            { title: '下一步', key: 'action', width: 135,
              render: (_: unknown, column: PhysicalStandardCoverageInspection['columns'][number]) =>
                onLocateColumn && column.status !== 'ENABLED_NOW'
                  ? <Button type="link" size="small"
                      onClick={() => onLocateColumn(column)}>定位当前物理列</Button>
                  : <Typography.Text type="secondary">—</Typography.Text> },
          ]} />
        <Typography.Title level={5}>核验适用范围</Typography.Title>
        {report.limitations.map((value, i) => (
          <Typography.Paragraph key={i} type="secondary" className="!mb-1">{value}</Typography.Paragraph>
        ))}
      </>}
    </Modal>
  );
}
