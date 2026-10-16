import { Alert, Button, Card, Select, Space, Table, Tag, Typography } from 'antd';
import { history } from '@umijs/max';
import { useEffect, useMemo, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import {
  getModelingModel, inspectPhysicalDataDevHandoff, listModelingVersions,
  type PhysicalDataDevHandoffReadiness,
} from '@/services/modeling/api';
import type { ModelingModelRecord } from '@/services/modeling/types';
import { getPublishedDwdMapping, type DwdPublishedMapping } from '@/services/modeling/dwdMapping';
import { assessDwdMappingEvidence } from '@/utils/dwdHandoffEvidence';
import { getSemanticProcess } from '@/services/semantic/api';
import { getDevelopmentTaskDraft } from '@/services/data-development';
import type { DevelopmentResourceNode, DevelopmentTaskDraft } from '@/services/data-development';
import DevelopmentTaskDeliveryEvidencePanel from './DevelopmentTaskDeliveryEvidencePanel';
import DatasetConsumptionProofPanel from './DatasetConsumptionProofPanel';
import {
  isCurrentProjectSqlCandidate, parsePhysicalDataDevHandoff,
  verifyPhysicalDataDevEvidence,
} from '@/utils/physicalDataDevHandoff';

interface Props {
  search: string;
  nodes: DevelopmentResourceNode[];
  onFocusNode: (id: string) => void;
}

interface VerifiedEvidence {
  owner: ModelingModelRecord;
  report: PhysicalDataDevHandoffReadiness;
  mappedDwd?: DwdPublishedMapping;
}

/** C5: DataDev consumes Modeling evidence. No creation, binding, SQL or run. */
export default function ModelingHandoffEvidencePanel({ search, nodes, onFocusNode }: Props) {
  const { currentProject } = useSecurityProject();
  const { canAll } = usePermissionAccess();
  const parsed = useMemo(() => parsePhysicalDataDevHandoff(search), [search]);
  const [refresh, setRefresh] = useState(0);
  const [verified, setVerified] = useState<VerifiedEvidence>();
  const [problem, setProblem] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [nodeId, setNodeId] = useState<string>();
  const [draft, setDraft] = useState<DevelopmentTaskDraft>();
  const [draftProblem, setDraftProblem] = useState<string>();
  const [draftLoading, setDraftLoading] = useState(false);
  const projectId = currentProject?.id;

  useEffect(() => {
    let active = true;
    setVerified(undefined);
    setProblem(undefined);
    setNodeId(undefined);
    setDraft(undefined);
    setDraftProblem(undefined);
    if (parsed.kind === 'NONE') return () => { active = false; };
    if (parsed.kind === 'INVALID') {
      setProblem(parsed.problem);
      return () => { active = false; };
    }
    if (projectId == null || !canAll(['modeling:read', 'data-development:read'])) {
      setProblem('当前项目或 Modeling / DataDev 读取权限不足，拒绝展示交接证据');
      return () => { active = false; };
    }
    const { modelId, processId, modelVersionNo } = parsed.value;
    setLoading(true);
    // Never consume a URL's model/version as authoritative; re-read each owner.
    void Promise.all([
      getModelingModel(modelId),
      getSemanticProcess(processId),
      listModelingVersions(modelId),
      inspectPhysicalDataDevHandoff(modelId, modelVersionNo),
    ]).then(async ([owner, process, versions, report]) => {
      if (!active) return;
      const checked = verifyPhysicalDataDevEvidence({
        hint: parsed.value, owner, processId: Number(process?.id),
        versions, report,
      });
      if (!checked.valid) { setProblem(checked.reason); return; }
      // DataDev revalidates the immutable DWD mapping itself, never trusting URL or sender state.
      if (owner.layerCode?.toUpperCase() === 'DWD') {
        const mappedDwd = await getPublishedDwdMapping(modelId, modelVersionNo);
        if (!active) return;
        const evidence = assessDwdMappingEvidence(modelId, modelVersionNo, report, versions, mappedDwd);
        if (!evidence.ready) {
          setProblem(`DWD 冻结映射证据无法核验：${evidence.reason}`);
          return;
        }
        setVerified({ owner, report, mappedDwd });
        return;
      }
      setVerified({ owner, report });
    }).catch((error: unknown) => {
      if (active) setProblem(error instanceof Error ? error.message
        : '业务过程、物理模型或冻结版本读取失败，不能确认交接来源');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [parsed, projectId, canAll, refresh]);

  const candidates = useMemo(
    () => nodes.filter(node => isCurrentProjectSqlCandidate(node, projectId)),
    [nodes, projectId],
  );
  const selected = candidates.find(node => node.id === nodeId);

  useEffect(() => {
    let active = true;
    setDraft(undefined);
    setDraftProblem(undefined);
    if (!verified || !selected) return () => { active = false; };
    setDraftLoading(true);
    // DataDev itself owns the draft. No model field is written to the task.
    void getDevelopmentTaskDraft(selected.id).then(result => {
      if (!active) return;
      if (result.nodeId !== selected.id || result.definition.taskType !== 'SQL') {
        setDraftProblem('DataDev 返回的草稿身份或任务类型不匹配，不能审阅');
        return;
      }
      setDraft(result);
    }).catch((error: unknown) => {
      if (active) setDraftProblem(error instanceof Error ? error.message : '当前任务草稿不可访问');
    }).finally(() => { if (active) setDraftLoading(false); });
    return () => { active = false; };
  }, [verified, selected?.id, projectId]);

  if (parsed.kind === 'NONE') return null;
  const hint = parsed.kind === 'HINT' ? parsed.value : undefined;
  return (
    <Card size="small" className="m-3 !mb-0"
      title="C5 · 已发布物理版本 → DataDev 人工交接核对（只读）"
      extra={<Button size="small" disabled={!hint}
        onClick={() => hint && history.push(`/modeling/models/${hint.modelId}?processId=${hint.processId}&review=datadev-handoff`)}>
        返回 Modeling 核对
      </Button>}>
      <Alert className="mb-3" showIcon type="warning"
        message="这是来自 Modeling 的设计版本证据，不是 DataDev 的任务关联或运行授权"
        description="不得将 URL、选择现有 SQL 节点、设计就绪或已发布物理版本当作持久 ModelVersionRef、目标实例部署、任务发布、成功运行或合法消费。" />
      {loading && <Typography.Paragraph>正在向 Semantic / Modeling 重新核对项目内真实对象和冻结版本…</Typography.Paragraph>}
      {problem && <Alert className="mb-3" showIcon type="error"
        message="交接来源无法核实" description={problem}
        action={hint && <Button size="small" onClick={() => setRefresh(x => x + 1)}>重新核验</Button>} />}
      {verified && !loading && (
        <>
          <Space wrap className="mb-3">
            <Tag>模型 #{verified.report.modelId} · {verified.owner.name || verified.report.modelCode}</Tag>
            <Tag>{verified.report.layerCode} · 冻结 V{verified.report.version.versionNo}</Tag>
            <Tag>Modeling 版本记录 #{verified.report.version.versionId}</Tag>
            <Tag color={verified.report.designStatus === 'DESIGN_REVIEWABLE' ? 'blue' : 'orange'}>
              {verified.report.designStatus === 'DESIGN_REVIEWABLE' ? '设计可人工审阅' : '设计治理待处理'}
            </Tag>
            <Tag color="orange">DataDev 尚未授权持久版本交接</Tag>
          </Space>
          <Typography.Paragraph type="secondary">
            冻结结构指纹：<Typography.Text copyable={{ text: verified.report.version.frozenStructureSha256 }}>
              {verified.report.version.frozenStructureSha256.slice(0, 20)}…
            </Typography.Text>
            {' · '}本次核验证据指纹：<Typography.Text copyable={{ text: verified.report.inspectionSha256 }}>
              {verified.report.inspectionSha256.slice(0, 20)}…
            </Typography.Text>
          </Typography.Paragraph>
          {verified.mappedDwd?.mapping && <>
            <Alert showIcon className="mb-3" type="info"
              message="DWD 冻结逻辑与映射修订已回读确认"
              description="这是 Modeling 发布的不可变设计证据；DataDev 尚未持久关联 TaskRevision，也没有执行 SQL。" />
            <Space wrap className="mb-3">
              <Tag>MappingRevision #{verified.mappedDwd.mapping.mappingRevisionId}</Tag>
              <Tag>MappingSet #{verified.mappedDwd.mapping.mappingSetId}</Tag>
              <Tag>逻辑模型 #{verified.mappedDwd.mapping.logicalModelId} · V{verified.mappedDwd.mapping.logicalVersionNo}</Tag>
              <Tag>字段映射 {verified.mappedDwd.mapping.rules.length}</Tag>
            </Space>
            <Typography.Paragraph type="secondary">
              冻结映射摘要：<Typography.Text
                copyable={{ text: verified.mappedDwd.mapping.contentSha256 }}>
                {verified.mappedDwd.mapping.contentSha256.slice(0, 20)}…
              </Typography.Text>
            </Typography.Paragraph>
            <Table size="small" rowKey="targetColumn" className="mb-3"
              pagination={{ pageSize: 10 }} scroll={{ x: 630 }}
              dataSource={verified.mappedDwd.mapping.rules} columns={[
                { title: 'DWD 物理目标字段', dataIndex: 'targetColumn' },
                { title: '冻结逻辑属性 ID', dataIndex: 'logicalAttributeId', width: 150 },
                { title: '绑定标准字段 ID', dataIndex: 'stdFieldId', width: 150 },
                { title: '人工映射方式', dataIndex: 'kind', width: 120,
                  render: (value: string) => <Tag>{value}</Tag> },
              ]} />
            <Typography.Paragraph type="secondary">
              这是发布时冻结的映射规则，只用于审阅；DataDev 不据此自动推导 SQL、
              JOIN、业务粒度或运行目标，任务发布仍须 DataDev 原域确认。
            </Typography.Paragraph>
          </>}
          {!verified.report.selectedLatestVersion && <Alert className="mb-3" showIcon type="warning"
            message="当前选择的是历史物理版本，不是最新发布指针；不会自动替换为最新版本" />}
          {verified.report.blockers.map((blocker, i) =>
            <Typography.Paragraph key={i} type="danger" className="!mb-1">{blocker}</Typography.Paragraph>)}
          <Table size="small" rowKey="code" pagination={false}
            dataSource={verified.report.dependencies} columns={[
              { title: '原域证据', dataIndex: 'code', width: 220 },
              { title: '状态', dataIndex: 'status', width: 170,
                render: (value: string) => <Tag>{value}</Tag> },
              { title: '责任边界/缺口', dataIndex: 'explanation' },
            ]} />
          <Space className="mt-3" wrap>
            <Button onClick={() => setRefresh(x => x + 1)} disabled={loading}>
              重新读取当前模型版本证据
            </Button>
            <Typography.Text type="secondary">
              后续目标连接、SQL 变换、版本持久引用和执行权限由 DataDev 正式校验
            </Typography.Text>
          </Space>
          <Typography.Title level={5} className="!mt-4">已有 SQL 任务 · 人工接管（不建立模型关联）</Typography.Title>
          <Typography.Paragraph type="secondary">
            仅列出当前项目数据开发工作台可访问的 SQL 节点。请选择任务查看其真实草稿；不会根据模型名称自动匹配、不回写模型版本。
          </Typography.Paragraph>
          <Space wrap className="mb-3">
            <Select<string> showSearch optionFilterProp="label" allowClear
              style={{ width: 340 }} placeholder="选择当前已有 SQL 节点"
              value={nodeId} onChange={setNodeId}
              options={candidates.map(node => ({ value: node.id, label: node.name }))} />
            <Button disabled={!selected || !draft || Boolean(draftProblem)}
              onClick={() => { if (selected) onFocusNode(selected.id); }}>
              打开所选现有任务（不绑定版本）
            </Button>
          </Space>
          {!candidates.length && <Alert type="info" showIcon
            message="当前项目没有可见的现有 SQL 任务；不自动创建新任务" />}
          {draftLoading && <Typography.Paragraph>正在读取 DataDev 原域草稿…</Typography.Paragraph>}
          {draftProblem && <Alert type="warning" showIcon description={draftProblem}
            message="已有 SQL 任务草稿不可核实" />}
          {draft && <Alert showIcon type="info"
            message={`SQL 任务 #${draft.nodeId}，草稿 Revision ${draft.draftRevision}`}
            description={`DataDev 独立草稿：SQL ${draft.definition.content.trim() ? '已有内容' : '尚无内容'}，配置 ${draft.definition.configJson.trim() ? '已填写' : '待填写'}；与冻结物理 V${verified.report.version.versionNo} 尚无正式持久引用，执行目标、权限和版本映射仍待专业人工核验。`} />}
          {draft && selected && !draftProblem &&
            <DevelopmentTaskDeliveryEvidencePanel
              key={`${String(projectId)}:${selected.id}`}
              nodeId={selected.id} projectId={projectId} />}
          {draft && selected && !draftProblem &&
            <DatasetConsumptionProofPanel
              key={`dataset:${String(projectId)}:${selected.id}`}
              nodeId={selected.id} projectId={projectId} />}
        </>
      )}
    </Card>
  );
}
