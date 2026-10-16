import { Alert, Button, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { listModelingVersions } from '@/services/modeling/api';
import type { ModelingVersionSummary } from '@/services/modeling/types';
import {
  getPublishedDwdMapping, type DwdPublishedMapping,
} from '@/services/modeling/dwdMapping';

interface Props { open: boolean; modelId: number; onClose: () => void; }

/** Read-only, exact historical evidence; never consult the current editable MappingSet. */
// R12-1: Modeling exposes design evidence only; execution remains outside this surface.
export default function DwdPublishedMappingHistoryPanel({ open, modelId, onClose }: Props) {
  const { currentProject } = useSecurityProject();
  const [versions, setVersions] = useState<ModelingVersionSummary[]>([]);
  const [versionNo, setVersionNo] = useState<number>();
  const [report, setReport] = useState<DwdPublishedMapping>();
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [problem, setProblem] = useState<string>();

  useEffect(() => {
    let current = true;
    setVersions([]);
    setVersionNo(undefined);
    setReport(undefined);
    setProblem(undefined);
    if (!open) return () => { current = false; };
    setVersionsLoading(true);
    void listModelingVersions(modelId).then((rows) => {
      if (!current) return;
      setVersions(rows ?? []);
      setVersionNo(rows?.[0]?.versionNo);
    }).catch((error: unknown) => {
      if (current) setProblem(error instanceof Error ? error.message : '无法查询历史版本');
    }).finally(() => {
      if (current) setVersionsLoading(false);
    });
    return () => { current = false; };
  }, [open, modelId, currentProject?.id]);

  useEffect(() => {
    let current = true;
    setReport(undefined);
    setProblem(undefined);
    if (!open || versionNo == null) return () => { current = false; };
    setLoading(true);
    void getPublishedDwdMapping(modelId, versionNo).then((value) => {
      if (!current) return;
      if (!value || value.versionNo !== versionNo
          || !versions.some((v) => v.versionNo === versionNo && v.id === value.modelVersionId)
          || (value.state === 'DESIGN_PUBLISHED_NOT_EXECUTED' && !value.mapping)
          || (value.state === 'LEGACY_UNBOUND' && !!value.mapping)) {
        setProblem('服务端历史版本身份或映射证据不一致，不能展示为已核验版本');
        return;
      }
      setReport(value);
    }).catch((error: unknown) => {
      if (current) setProblem(error instanceof Error ? error.message : '冻结映射证据不可访问');
    }).finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [modelId, versionNo, versions, open, currentProject?.id]);

  return (
    <Modal title="DWD 设计版本与映射历史（只读）" open={open}
      width={1000} onCancel={onClose} destroyOnHidden
      footer={<Button onClick={onClose}>关闭</Button>}>
      <Space wrap className="mb-3">
        <Typography.Text>选择已发布物理版本：</Typography.Text>
        <Select<number> style={{ width: 220 }} value={versionNo} onChange={setVersionNo}
          loading={versionsLoading} placeholder="选择版本"
          options={versions.map((v) => ({
            value: v.versionNo, label: `V${v.versionNo} · ${v.columnCount} 列`,
          }))} />
      </Space>
      {!versionsLoading && versions.length === 0 && !problem && (
        <Alert showIcon type="info" message="尚无已发布的 DWD 设计版本" />
      )}
      {loading && <Typography.Paragraph type="secondary">读取精确历史发布版本与冻结映射…</Typography.Paragraph>}
      {problem && <Alert showIcon type="error" message="无法核验历史版本" description={problem} />}
      {report?.state === 'LEGACY_UNBOUND' && (
        <Alert showIcon type="warning" message={`物理版本 V${report.versionNo} 未绑定冻结逻辑映射`}
          description="这是旧物理设计版本；不能反推其历史逻辑模型、映射规则或标准身份。" />
      )}
      {report?.mapping && <>
        <Alert showIcon type="success" className="mb-3"
          message={`DWD 设计 V${report.versionNo} 具有已冻结映射证据`}
          description="仅表示物理设计版本发布；没有验证建表、任务发布、数据加工或质量验收。" />
        <Space wrap className="mb-3">
          <Tag>物理版本 #{report.modelVersionId}</Tag>
          <Tag>MappingRevision #{report.mapping.mappingRevisionId}</Tag>
          <Tag>逻辑模型 #{report.mapping.logicalModelId} · V{report.mapping.logicalVersionNo}</Tag>
        </Space>
        <Typography.Paragraph type="secondary">
          映射冻结摘要：
          <Typography.Text copyable={{ text: report.mapping.contentSha256 }}>
            {report.mapping.contentSha256.slice(0, 20)}…
          </Typography.Text>
        </Typography.Paragraph>
        <Table size="small" pagination={{ pageSize: 15 }}
          rowKey={(row) => `${row.targetColumn}-${row.logicalAttributeId}`}
          dataSource={report.mapping.rules} columns={[
            { title: 'DWD 物理字段', dataIndex: 'targetColumn' },
            { title: '逻辑属性 ID', dataIndex: 'logicalAttributeId' },
            { title: '标准字段 ID', dataIndex: 'stdFieldId' },
            { title: '映射方式', dataIndex: 'kind',
              render: (value: string) => <Tag>{value}</Tag> },
          ]} />
      </>}
    </Modal>
  );
}
