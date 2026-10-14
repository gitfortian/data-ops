import { Alert, Button, Card, Empty, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  getLogicalDraft, listLogicalDrafts, listLogicalVersions, previewLogicalPhysical,
} from '@/services/modeling/logical';
import type {
  LogicalModelDraft, LogicalDraftVersion, LogicalPhysicalPreview,
} from '@/services/modeling/logical';
import type { ModelingModelRecord } from '@/services/modeling/types';
import { logicalDraftNavigationPath, parseLogicalPhysicalHint } from '@/utils/logicalPhysicalJourney';

/**
 * FE-A3: uses existing Modeling Owner read-only version / physical-preview APIs.
 * No creation, persistence, "mapping approved" or DataDev task write endpoint.
 */
export default function LogicalPhysicalEvidencePanel({
  model, search, onNavigateLogical,
}: {
  model?: ModelingModelRecord;
  search: string;
  onNavigateLogical: (path: string) => void;
}) {
  const { currentProject } = useSecurityProject();
  const { can } = usePermissionAccess();
  const allowed = can('modeling:read');
  const physicalId = Number(model?.id);
  const processId = model?.processId;
  const supported = ['DWD', 'DIM'].includes((model?.layerCode || '').toUpperCase());
  const scope = JSON.stringify([currentProject?.id, allowed, physicalId, processId, model?.layerCode, search]);
  const [catalog, setCatalog] = useState<{ scope: string; drafts: LogicalModelDraft[]; error?: string }>();
  const [selection, setSelection] = useState<{ scope: string; logicalId?: number; versionNo?: number }>();
  const [versions, setVersions] = useState<{ scope: string; logicalId: number; items: LogicalDraftVersion[]; error?: string }>();
  const [preview, setPreview] = useState<{ scope: string; logicalId: number; versionNo: number; data: LogicalPhysicalPreview }>();
  const [previewError, setPreviewError] = useState<{ scope: string; text: string }>();
  const [loading, setLoading] = useState(false);
  const [versionLoading, setVersionLoading] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);

  const q = useMemo(() => new URLSearchParams(search), [search]);
  const hinted = parseLogicalPhysicalHint(q, physicalId);
  const hasHint = ['logicalId', 'logicalVersion'].some(k => q.has(k));
  const validModel = supported && allowed && Boolean(currentProject?.id)
    && Number.isSafeInteger(physicalId) && physicalId > 0
    && Number.isSafeInteger(processId) && Number(processId) > 0;
  const drafts = catalog?.scope === scope ? catalog.drafts : [];
  const selected = selection?.scope === scope ? selection.logicalId : undefined;
  const version = selection?.scope === scope ? selection.versionNo : undefined;
  const frozenVersions = versions?.scope === scope && versions.logicalId === selected ? versions.items : [];
  const ready = preview?.scope === scope && preview.logicalId === selected && preview.versionNo === version
    ? preview.data : undefined;

  useEffect(() => {
    setCatalog(undefined);
    setSelection(undefined);
    setVersions(undefined);
    setPreview(undefined);
    setPreviewError(undefined);
    if (!validModel) return;
    let live = true;
    setLoading(true);
    void listLogicalDrafts().then((items) => {
      if (!live) return;
      const sameProcess = (items || []).filter(item => item.processId === processId);
      setCatalog({ scope, drafts: sameProcess });
      // Deep links are hints only. Never fall back to the first/same-named
      // logical draft when the explicitly requested ID is unavailable.
      if (hinted && hinted.processId === processId && hinted.physicalId === physicalId
          && sameProcess.some(item => item.id === hinted.logicalId)) {
        setSelection({ scope, logicalId: hinted.logicalId, versionNo: hinted.logicalVersionNo });
      }
    }).catch(() => {
      if (live) setCatalog({ scope, drafts: [], error: '当前项目的逻辑草稿目录读取失败；不会猜测映射关系。' });
    }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [scope, validModel, physicalId, processId, hinted?.logicalId, hinted?.logicalVersionNo, hinted?.processId]);

  useEffect(() => {
    setVersions(undefined);
    setPreview(undefined);
    if (!validModel || selected == null) return;
    let live = true;
    setVersionLoading(true);
    // The list alone cannot establish this draft's process identity. Re-read
    // the exact Owner draft before accepting its frozen versions.
    void Promise.all([getLogicalDraft(selected), listLogicalVersions(selected)])
      .then(([draft, items]) => {
        if (!live) return;
        if (draft.model.id !== selected || draft.model.processId !== processId
            || !drafts.some(x => x.id === selected)) throw new Error('Logical Owner scope mismatch');
        const versionsForDraft = (items || []).filter(x =>
          Number.isSafeInteger(x.versionNo) && x.versionNo > 0);
        setVersions({ scope, logicalId: selected, items: versionsForDraft });
      }).catch(() => {
        if (live) setVersions({ scope, logicalId: selected, items: [], error: '无法核验指定逻辑草稿及其冻结版本。' });
      }).finally(() => { if (live) setVersionLoading(false); });
    return () => { live = false; };
  }, [scope, validModel, selected, processId, drafts]);

  const prepare = async () => {
    if (!validModel || selected == null || version == null
        || !frozenVersions.some(x => x.versionNo === version)) return;
    const requestScope = scope;
    setPreview(undefined);
    setPreviewError(undefined);
    setPreviewLoading(true);
    try {
      const evidence = await previewLogicalPhysical(selected, version, physicalId);
      if (requestScope !== scope) return;
      if (evidence.logicalModelId !== selected || evidence.logicalVersionNo !== version
          || evidence.physicalModelId !== physicalId
          || !/^[a-fA-F0-9]{64}$/.test(evidence.logicalSnapshotSha256)
          || !/^[a-fA-F0-9]{64}$/.test(evidence.physicalStructureSha256)
          || !Array.isArray(evidence.columns) || !Array.isArray(evidence.blockers)) {
        throw new Error('Owner evidence scope mismatch');
      }
      setPreview({ scope, logicalId: selected, versionNo: version, data: evidence });
    } catch {
      setPreviewError({ scope, text: '模型证据已变化、缺失或未通过身份校验；请核对版本后重新预检。' });
    } finally { setPreviewLoading(false); }
  };

  if (!supported) return <Alert showIcon type="info" message="ODS 或其他分层遵循独立的建模前置路径"
    description="ODS 以已核验源表直接建模；本区域的冻结逻辑版本到物理模型预检仅适用于 DWD/DIM，不新增发布门禁。" />;
  if (!allowed || !currentProject?.id) return <Alert showIcon type="warning"
    message="没有当前项目的建模读取权限，不能打开逻辑与物理审阅" />;
  if (!validModel) return <Alert showIcon type="warning"
    message="当前物理模型没有可核验的业务过程关联"
    description="先在真实模型 Owner 中确认过程及 DWD/DIM 身份，不使用 URL 推断其逻辑依据。" />;

  const selectedDraft = drafts.find(x => x.id === selected);
  return <Card className="max-w-[1050px]" title="业务逻辑依据 · 冻结版本对照"
    extra={<Tag color="blue">只读审阅 · 不保存映射</Tag>}>
    <Alert className="mb-4" showIcon type="info"
      message={`当前物理模型 #${physicalId} · 业务过程 #${processId}`}
      description="先确认业务逻辑模型及其冻结版本，再与当前物理结构对照。相同业务过程或标准字段不代表已建立正式逻辑↔物理映射；预检不能创建物理表、发布模型或触发数据加工。" />
    {hasHint && (!hinted || hinted.processId !== processId
        || (catalog?.scope === scope && !drafts.some(x => x.id === hinted?.logicalId))) ? (
      <Alert className="mb-4" showIcon type="warning"
        message="指定逻辑模型或版本来源不符合当前物理模型的业务过程"
        description="不会自动降级到其他同名逻辑草稿。请从有权访问的当前过程模型中手动选择。" />
    ) : null}
    {catalog?.scope === scope && catalog.error ? <Alert className="mb-3" showIcon type="error" message={catalog.error} /> : null}
    <div className="flex flex-wrap items-end gap-3">
      <div className="min-w-[230px] flex-1">
        <Typography.Text className="mb-2 block" type="secondary">当前业务过程的逻辑草稿</Typography.Text>
        <Select showSearch optionFilterProp="label" className="w-full" loading={loading}
          placeholder={loading ? '正在读取逻辑草稿…' : '明确选择一个逻辑草稿'}
          value={selected} disabled={loading || !!catalog?.error}
          options={drafts.map(x => ({ value: x.id, label: `${x.name} · ${x.code} (#${x.id})` }))}
          onChange={id => { setSelection({ scope, logicalId: id }); setPreview(undefined); setPreviewError(undefined); }}
        />
      </div>
      <div className="min-w-[180px] flex-1">
        <Typography.Text className="mb-2 block" type="secondary">真实冻结版本</Typography.Text>
        <Select className="w-full" placeholder="选择冻结版本" loading={versionLoading}
          value={version && frozenVersions.some(x => x.versionNo === version) ? version : undefined}
          disabled={!selected || versionLoading || !frozenVersions.length}
          options={frozenVersions.map(x => ({ value: x.versionNo, label: `逻辑 V${x.versionNo} · #${x.id}` }))}
          onChange={n => { setSelection({ scope, logicalId: selected, versionNo: n }); setPreview(undefined); setPreviewError(undefined); }} />
      </div>
      <Button type="primary" loading={previewLoading}
        disabled={!selected || !version || !frozenVersions.some(x => x.versionNo === version) || previewLoading}
        onClick={() => void prepare()}>预检逻辑↔物理（只读）</Button>
      {selectedDraft ? <Button onClick={() => {
        const path = logicalDraftNavigationPath(processId!, selectedDraft.id);
        if (path) onNavigateLogical(path);
      }}>返回此逻辑草稿</Button> : null}
    </div>
    {versions?.scope === scope && versions.error ? <Alert className="mt-4" showIcon type="warning" message={versions.error} /> : null}
    {hasHint && selected != null && version != null && versions?.scope === scope
      && !versionLoading && !frozenVersions.some(x => x.versionNo === version)
      ? <Alert className="mt-4" showIcon type="warning" message="指定冻结版本不存在于当前逻辑模型；不能替换成最新版本。" /> : null}
    {catalog?.scope === scope && !loading && drafts.length === 0 && !catalog.error
      ? <Empty description="该业务过程尚无可读取的逻辑设计；请先在业务逻辑模型页建立草稿并冻结版本。" /> : null}
    {previewError?.scope === scope ? <Alert className="mt-4" type="error" showIcon message={previewError.text} /> : null}
    <Spin spinning={previewLoading}>
      {ready ? <div className="mt-5">
        <Alert showIcon type={ready.readyForReview && !ready.blockers.length ? 'info' : 'warning'}
          message={ready.readyForReview && !ready.blockers.length ? '当前证据可供人工对照（不是正式映射）' : '存在阻断项，需要返回逻辑或物理设计核对'}
          description={`冻结逻辑 V${ready.logicalVersionNo} 与当前物理 ${ready.physicalStatus} 结构的只读比较；只有明确的模型和版本身份能够进入此报告。`} />
        <div className="mt-3 flex flex-wrap gap-3 text-xs text-[#667085]">
          <span>逻辑快照：<Typography.Text copyable={{ text: ready.logicalSnapshotSha256 }}>{ready.logicalSnapshotSha256.slice(0,16)}…</Typography.Text></span>
          <span>物理结构：<Typography.Text copyable={{ text: ready.physicalStructureSha256 }}>{ready.physicalStructureSha256.slice(0,16)}…</Typography.Text></span>
        </div>
        {ready.blockers.map((x,i) => <Alert key={i} className="mt-2" showIcon type="warning" message={x} />)}
        <Table className="mt-4" size="small" scroll={{ x: 790 }}
          rowKey={(x,i) => String(x.physicalColumnId ?? `candidate-${i}`)}
          pagination={{ pageSize: 10 }}
          dataSource={ready.columns}
          columns={[
            { title: '物理字段', dataIndex: 'physicalColumn', width: 180 },
            { title: '逻辑实体', dataIndex: 'logicalEntity', width: 140, render: (x?: string) => x || '未确认' },
            { title: '逻辑属性', dataIndex: 'logicalAttribute', width: 140, render: (x?: string) => x || '未确认' },
            { title: '对照结果', dataIndex: 'result', width: 130, render: (x: string) => <Tag>{x}</Tag> },
            { title: '原因与限制', dataIndex: 'reason' },
          ]} />
        <Typography.Paragraph type="secondary" className="!mt-3 !mb-0">
          仅展示实时证据，不持久化逐字段选择。人工正式映射、版本绑定、审批及加工任务来源需由 DR-06/DR-07 独立合同批准。
        </Typography.Paragraph>
      </div> : null}
    </Spin>
  </Card>;
}
