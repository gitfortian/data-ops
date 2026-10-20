import { Alert, Button, Collapse, Descriptions, Empty, Modal, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import { history } from '@umijs/max';
import { useEffect, useState } from 'react';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { inspectPhysicalDataDevHandoff, listModelingVersions, type PhysicalDataDevHandoffReadiness } from '@/services/modeling/api';
import { getPublishedDwdMapping, type DwdPublishedMapping } from '@/services/modeling/dwdMapping';
import type { ModelingVersionSummary } from '@/services/modeling/types';
import { assessDwdMappingEvidence } from '@/utils/dwdHandoffEvidence';
import { physicalDataDevHandoffUrl } from '@/utils/physicalDataDevHandoff';

interface Props { modelId: number; processId?: number; open: boolean; onClose: () => void; }
const dependencyLabels: Record<string, string> = {
  EXACT_MODEL_VERSION: '设计版本', FROZEN_STRUCTURE: '版本结构', PUBLISHED_POINTER: '当前发布版本',
  CURRENT_STANDARD: '字段标准', HISTORIC_STANDARD_PROVENANCE: '历史标准快照',
  LOGICAL_PHYSICAL_MAPPING: '逻辑与物理映射', DATADEV_PROVENANCE: '开发关联',
  TARGET_ENVIRONMENT: '目标环境', TASK_PLUGIN_AND_RUNTIME: '任务与运行配置',
};
const statusLabels: Record<string, string> = {
  PINNED: '已固定', MATCHES_CURRENT: '与当前设计一致', DRIFTED: '当前设计有变更',
  LATEST: '当前发布版本', NOT_LATEST: '历史版本', COVERED_NOW: '已检查', NEEDS_REVIEW: '待完善',
  NOT_FROZEN: '未冻结', NOT_APPROVED: '待确认', NOT_CHECKED: '待开发核对',
};
type Inspection = { scope: string; versionNo: number; report?: PhysicalDataDevHandoffReadiness;
  mapping?: DwdPublishedMapping; reportProblem?: string; mappingProblem?: string };
const errorText = (error: unknown, fallback: string) => error instanceof Error ? error.message : fallback;

/** DWD-specific presentation over the existing read-only version and mapping APIs. */
export default function DwdDataDevHandoffPanel({ modelId, processId, open, onClose }: Props) {
  const { currentProject } = useSecurityProject();
  const { canAll } = usePermissionAccess();
  const canRead = canAll(['modeling:read']);
  const scope = JSON.stringify([currentProject?.id, modelId, open, canRead]);
  const [catalog, setCatalog] = useState<{ scope: string; items: ModelingVersionSummary[]; problem?: string }>();
  const [selection, setSelection] = useState<{ scope: string; versionNo: number }>();
  const [inspection, setInspection] = useState<Inspection>();
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [catalogAttempt, setCatalogAttempt] = useState(0);
  const [inspectionAttempt, setInspectionAttempt] = useState(0);
  const versions = catalog?.scope === scope ? catalog.items : [];
  const versionNo = selection?.scope === scope ? selection.versionNo : undefined;
  const selectedVersion = versions.find(item => item.versionNo === versionNo);
  const current = inspection?.scope === scope && inspection.versionNo === versionNo ? inspection : undefined;
  const report = current?.report;
  const mapping = current?.mapping;
  const evidence = assessDwdMappingEvidence(modelId, versionNo, report, versions, mapping);
  const canContinue = Boolean(processId && canAll(['data-development:read', 'modeling:read'])
    && report && evidence.ready && !current?.reportProblem && !current?.mappingProblem && !loading);

  useEffect(() => {
    let active = true;
    setCatalog(undefined); setSelection(undefined); setInspection(undefined);
    setVersionsLoading(false); setLoading(false);
    if (!open || !currentProject?.id || !canRead) return () => { active = false; };
    setVersionsLoading(true);
    void listModelingVersions(modelId).then(items => {
      if (!active) return;
      const scoped = (items ?? []).filter(item => item.modelId === modelId && Number.isSafeInteger(item.id) && item.id > 0
        && Number.isSafeInteger(item.versionNo) && item.versionNo > 0).sort((a, b) => b.versionNo - a.versionNo);
      setCatalog({ scope, items: scoped });
      if (scoped[0]) setSelection({ scope, versionNo: scoped[0].versionNo });
    }).catch((error: unknown) => {
      if (active) setCatalog({ scope, items: [], problem: errorText(error, '发布版本暂时无法读取') });
    }).finally(() => { if (active) setVersionsLoading(false); });
    return () => { active = false; };
  }, [scope, modelId, open, canRead, currentProject?.id, catalogAttempt]);

  useEffect(() => {
    let active = true;
    setInspection(undefined); setLoading(false);
    if (!open || !selectedVersion || versionNo == null) return () => { active = false; };
    setLoading(true);
    void Promise.allSettled([
      inspectPhysicalDataDevHandoff(modelId, versionNo), getPublishedDwdMapping(modelId, versionNo),
    ]).then(([readiness, publication]) => {
      if (!active) return;
      const next: Inspection = { scope, versionNo };
      if (readiness.status === 'fulfilled') {
        const value = readiness.value;
        if (value?.modelId === modelId && value.layerCode === 'DWD' && value.version?.modelId === modelId
            && value.version.versionNo === versionNo && value.version.versionId === selectedVersion.id
            && Array.isArray(value.dependencies) && Array.isArray(value.blockers) && Array.isArray(value.nextActions)) next.report = value;
        else next.reportProblem = '返回的设计版本与当前选择不一致，请重新核对。';
      } else next.reportProblem = errorText(readiness.reason, '版本信息暂时无法核对');
      if (publication.status === 'fulfilled') next.mapping = publication.value;
      else next.mappingProblem = errorText(publication.reason, '映射记录暂时无法读取');
      setInspection(next);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [scope, modelId, versionNo, selectedVersion?.id, open, inspectionAttempt]);

  const catalogProblem = catalog?.scope === scope ? catalog.problem : undefined;
  const retryInspection = () => setInspectionAttempt(value => value + 1);
  return <Modal title="DWD 开发交接核对" width={1000} open={open} destroyOnHidden onCancel={onClose}
    className="modeling-modal" classNames={{ content: 'modeling-modal-content', header: 'modeling-modal-header',
      body: 'modeling-modal-body', footer: 'modeling-modal-footer' }}
    footer={<Space wrap><Button onClick={onClose}>关闭</Button>
      <Button type="primary" disabled={!canContinue} onClick={() => {
        if (canContinue && processId && versionNo) history.push(physicalDataDevHandoffUrl({ processId, modelId, modelVersionNo: versionNo }));
      }}>进入开发工作台</Button></Space>}>
    {!canRead || !currentProject?.id ? <Alert type="error" showIcon message="无法读取当前项目的模型版本"
      description="请确认已选择项目并具有建模查看权限。" /> : <>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Typography.Text>设计版本</Typography.Text>
        <Select<number> aria-label="DWD开发设计版本" style={{ width: 240, maxWidth: '100%' }}
          value={versionNo} loading={versionsLoading} disabled={versionsLoading || !versions.length}
          placeholder="选择已发布版本" onChange={value => setSelection({ scope, versionNo: value })}
          options={versions.map(item => ({ value: item.versionNo, label: `V${item.versionNo} · ${item.columnCount} 个字段` }))} />
        <Button loading={versionsLoading} onClick={() => setCatalogAttempt(value => value + 1)}>刷新版本</Button>
      </div>
      {catalogProblem && <Alert type="error" showIcon message="发布版本读取失败" description={catalogProblem}
        action={<Button size="small" onClick={() => setCatalogAttempt(value => value + 1)}>重新加载</Button>} />}
      {!versionsLoading && catalog?.scope === scope && !catalogProblem && !versions.length
        && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已发布的 DWD 设计版本">
          <Button onClick={onClose}>返回模型设计</Button>
        </Empty>}
      <Spin spinning={loading}>
        {current?.reportProblem && <Alert className="mb-3" type="error" showIcon message="设计版本核对失败" description={current.reportProblem}
          action={<Button size="small" onClick={retryInspection}>重新核对</Button>} />}
        {current?.mappingProblem && <Alert className="mb-3" type="error" showIcon message="映射记录读取失败" description={current.mappingProblem}
          action={<Button size="small" onClick={retryInspection}>重新读取</Button>} />}
        {mapping?.state === 'LEGACY_UNBOUND' && <Alert className="mb-3" type="warning" showIcon
          message={`设计版本 V${versionNo} 未记录逻辑映射`} description="请返回模型设计核对映射，保存并发布对应版本后再继续。" />}
        {report && mapping && !current?.mappingProblem && mapping.state !== 'LEGACY_UNBOUND' && !evidence.ready
          && <Alert className="mb-3" type="error" showIcon message="设计版本与映射不一致" description={evidence.reason}
            action={<Button size="small" onClick={retryInspection}>重新核对</Button>} />}
        {report && <>
          <div className="mb-4 rounded-lg border border-[#eaecf0] bg-[#f8fafc] p-4">
            <Space wrap className="mb-2"><Typography.Text strong>{report.modelCode}</Typography.Text>
              <Tag>设计版本 V{report.version.versionNo}</Tag>
              {!report.selectedLatestVersion && <Tag>历史版本</Tag>}
              {evidence.ready && <Tag color="blue">映射已核对</Tag>}</Space>
            <Typography.Paragraph className="!mb-0" type="secondary">
              {report.frozenColumnCount} 个物理字段{mapping?.mapping ? ` · ${mapping.mapping.rules.length} 项字段映射` : ''}
              {report.frozenUnboundColumnCount > 0 ? ` · ${report.frozenUnboundColumnCount} 个字段未记录标准引用` : ''}
            </Typography.Paragraph>
          </div>
          {report.blockers.length > 0 && <Alert type="warning" showIcon className="mb-3" message="请核对以下设计问题"
            description={<ul className="!mb-0 !pl-4">{report.blockers.map((reason, index) => <li key={index}>{reason}</li>)}</ul>} />}
          <Table size="small" rowKey="code" pagination={false} scroll={{ x: 560 }}
            dataSource={report.dependencies.filter(item => !['HISTORIC_STANDARD_PROVENANCE', 'LOGICAL_PHYSICAL_MAPPING', 'DATADEV_PROVENANCE'].includes(item.code))}
            columns={[
              { title: '核对项', dataIndex: 'code', render: (value: string) => dependencyLabels[value] || value },
              { title: '当前结果', dataIndex: 'status', render: (value: string) =>
                <Tag>{statusLabels[value] || value}</Tag> },
            ]} />
          <Typography.Paragraph type="secondary" className="!mt-4 !mb-1">
            进入开发工作台后，继续核对目标环境、执行身份和任务配置。
          </Typography.Paragraph>
          {!processId && <Typography.Paragraph type="secondary" className="!mb-1">请先在模型基本信息中关联业务过程。</Typography.Paragraph>}
          <Collapse ghost items={[{ key: 'details', label: '核验详情', children: <>
            <Descriptions size="small" column={1} items={[
              { key: 'version', label: '物理版本 ID', children: report.version.versionId },
              { key: 'structure', label: '版本结构摘要', children: <Typography.Text copyable>{report.version.frozenStructureSha256}</Typography.Text> },
              { key: 'inspection', label: '本次核验摘要', children: <Typography.Text copyable>{report.inspectionSha256}</Typography.Text> },
              ...(mapping?.mapping ? [
                { key: 'mapping', label: '映射版本', children: `V${mapping.mapping.revisionNo} · #${mapping.mapping.mappingRevisionId}` },
                { key: 'logical', label: '逻辑模型', children: `#${mapping.mapping.logicalModelId} · V${mapping.mapping.logicalVersionNo}` },
                { key: 'digest', label: '映射内容摘要', children: <Typography.Text copyable>{mapping.mapping.contentSha256}</Typography.Text> },
              ] : []),
            ]} />
            <Table className="mt-3" size="small" rowKey="code" pagination={false} scroll={{ x: 600 }} dataSource={report.dependencies}
              columns={[{ title: '核对项', dataIndex: 'code', render: (value: string) => dependencyLabels[value] || value },
                { title: '详细结果', dataIndex: 'explanation' }]} />
            <Typography.Paragraph type="secondary" className="!mt-3">此入口只带入设计版本供核对，任务创建和运行由开发工作台处理。</Typography.Paragraph>
            {report.nextActions.map((action, index) => <Typography.Paragraph type="secondary" className="!mb-1" key={index}>{action}</Typography.Paragraph>)}
          </> }]} />
        </>}
      </Spin>
    </>}
  </Modal>;
}
