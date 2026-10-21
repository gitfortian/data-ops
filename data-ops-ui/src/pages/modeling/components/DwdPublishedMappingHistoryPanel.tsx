import { Alert, Button, Collapse, Descriptions, Empty, Input, Modal, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { listModelingVersions } from '@/services/modeling/api';
import type { ModelingVersionSummary } from '@/services/modeling/types';
import {
  getPublishedDwdMapping, type DwdPublishedMapping,
} from '@/services/modeling/dwdMapping';

interface Props { open: boolean; modelId: number; onClose: () => void; }

/** Read-only, exact historical evidence; never consult the current editable MappingSet. */
export default function DwdPublishedMappingHistoryPanel({ open, modelId, onClose }: Props) {
  const { currentProject } = useSecurityProject();
  const [versions, setVersions] = useState<ModelingVersionSummary[]>([]);
  const [versionNo, setVersionNo] = useState<number>();
  const [report, setReport] = useState<DwdPublishedMapping>();
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [problem, setProblem] = useState<string>();
  const [versionsProblem, setVersionsProblem] = useState<string>();
  const [catalogAttempt, setCatalogAttempt] = useState(0);
  const [reportAttempt, setReportAttempt] = useState(0);
  const [keyword, setKeyword] = useState('');

  useEffect(() => {
    let current = true;
    setVersions([]);
    setVersionNo(undefined);
    setReport(undefined);
    setProblem(undefined);
    setVersionsProblem(undefined);
    setKeyword('');
    setLoading(false);
    setVersionsLoading(false);
    if (!open) return () => { current = false; };
    setVersionsLoading(true);
    void listModelingVersions(modelId).then((rows) => {
      if (!current) return;
      const sorted = (rows ?? []).filter(row => row.modelId === modelId && Number.isSafeInteger(row.versionNo) && row.versionNo > 0)
        .sort((a, b) => b.versionNo - a.versionNo);
      setVersions(sorted);
      setVersionNo(sorted[0]?.versionNo);
    }).catch((error: unknown) => {
      if (current) setVersionsProblem(error instanceof Error ? error.message : '无法查询历史版本');
    }).finally(() => {
      if (current) setVersionsLoading(false);
    });
    return () => { current = false; };
  }, [open, modelId, currentProject?.id, catalogAttempt]);

  useEffect(() => {
    let current = true;
    setReport(undefined);
    setProblem(undefined);
    setKeyword('');
    setLoading(false);
    if (!open || versionNo == null) return () => { current = false; };
    setLoading(true);
    void getPublishedDwdMapping(modelId, versionNo).then((value) => {
      if (!current) return;
      if (!value || value.versionNo !== versionNo
          || !versions.some((v) => v.modelId === modelId && v.versionNo === versionNo && v.id === value.modelVersionId)
          || !['DESIGN_PUBLISHED_NOT_EXECUTED', 'LEGACY_UNBOUND'].includes(value.state)
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
  }, [modelId, versionNo, versions, open, currentProject?.id, reportAttempt]);

  return (
    <Modal title="DWD 版本与映射历史" open={open}
      width={1000} onCancel={onClose} destroyOnHidden
      className="modeling-modal" classNames={{ content: 'modeling-modal-content', header: 'modeling-modal-header',
        body: 'modeling-modal-body', footer: 'modeling-modal-footer' }}
      footer={<Button onClick={onClose}>关闭</Button>}>
      <Space wrap className="mb-3">
        <Typography.Text>设计版本</Typography.Text>
        <Select<number> aria-label="DWD历史设计版本" style={{ width: 240, maxWidth: '100%' }} value={versionNo} onChange={setVersionNo}
          disabled={versionsLoading || !versions.length}
          loading={versionsLoading} placeholder="选择版本"
          options={versions.map((v) => ({
            value: v.versionNo, label: `V${v.versionNo} · ${v.columnCount} 个字段`,
          }))} />
        <Button loading={versionsLoading} onClick={() => setCatalogAttempt(value => value + 1)}>刷新版本</Button>
      </Space>
      {versionsProblem && <Alert showIcon type="error" message="历史版本读取失败" description={versionsProblem}
        action={<Button size="small" onClick={() => setCatalogAttempt(value => value + 1)}>重新加载</Button>} />}
      {!versionsLoading && versions.length === 0 && !versionsProblem && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已发布的 DWD 设计版本">
          <Button onClick={onClose}>返回模型设计</Button>
        </Empty>
      )}
      <Spin spinning={loading}>
      {problem && <Alert showIcon type="error" message="映射记录读取失败" description={problem}
        action={<Button size="small" onClick={() => setReportAttempt(value => value + 1)}>重新读取</Button>} />}
      {report?.state === 'LEGACY_UNBOUND' && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`设计版本 V${report.versionNo} 未记录逻辑映射`}>
          <Typography.Paragraph type="secondary">该历史版本的逻辑映射需在当前设计中核对。</Typography.Paragraph>
          <Button onClick={onClose}>返回模型设计</Button>
        </Empty>
      )}
      {report?.mapping && <>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Space wrap><Typography.Text strong>设计版本 V{report.versionNo}</Typography.Text>
            <Tag>逻辑版本 V{report.mapping.logicalVersionNo}</Tag>
            <Typography.Text type="secondary">{report.mapping.rules.length} 项字段映射</Typography.Text></Space>
          <Input.Search aria-label="搜索历史映射字段" placeholder="搜索物理字段" allowClear style={{ width: 230, maxWidth: '100%' }}
            value={keyword} onChange={event => setKeyword(event.target.value)} />
        </div>
        <Table size="small" pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }} scroll={{ x: 600 }}
          locale={{ emptyText: '没有匹配的字段' }}
          rowKey={(row) => `${row.targetColumn}-${row.logicalAttributeId}`}
          dataSource={report.mapping.rules.filter(rule => rule.targetColumn.toLowerCase().includes(keyword.trim().toLowerCase()))} columns={[
            { title: '物理字段', dataIndex: 'targetColumn' },
            { title: '逻辑属性引用', dataIndex: 'logicalAttributeId', render: (value: number) => `#${value}` },
            { title: '标准字段引用', dataIndex: 'stdFieldId', render: (value: number) => `#${value}` },
            { title: '映射方式', dataIndex: 'kind',
              render: (value: string) => <Tag>{value === 'DIRECT' ? '原样映射' : value === 'RENAME' ? '重命名' : value}</Tag> },
          ]} />
        <Collapse ghost className="mt-3" items={[{ key: 'details', label: '核验详情', children: <>
          <Descriptions size="small" column={1} items={[
            { key: 'physical', label: '物理版本 ID', children: report.modelVersionId },
            { key: 'mapping', label: '映射版本', children: `V${report.mapping.revisionNo} · #${report.mapping.mappingRevisionId}` },
            { key: 'logical', label: '逻辑模型', children: `#${report.mapping.logicalModelId} · V${report.mapping.logicalVersionNo}` },
            { key: 'digest', label: '映射内容摘要', children: <Typography.Text copyable>{report.mapping.contentSha256}</Typography.Text> },
          ]} />
          <Typography.Paragraph type="secondary" className="!mb-0 !mt-2">此处查看已发布设计的历史记录，运行结果请在开发工作台核对。</Typography.Paragraph>
        </> }]} />
      </>}
      </Spin>
    </Modal>
  );
}
