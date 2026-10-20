import { Alert, Card, Collapse, Descriptions, Select, Spin, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
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

const RESULT_LABELS: Record<string, string> = {
  CANDIDATE_ONLY: '可供对照',
  CANDIDATE: '可供对照',
  MISSING_STANDARD: '未关联标准字段',
  INVALID_STANDARD: '标准字段不可用',
  AMBIGUOUS_PHYSICAL_REFERENCE: '物理字段待确认',
  NO_LOGICAL_REFERENCE: '缺少逻辑属性',
  AMBIGUOUS_LOGICAL_REFERENCE: '逻辑属性待确认',
  MISSING_LOGICAL_STANDARD: '逻辑属性未关联标准',
  INVALID_LOGICAL_STANDARD: '逻辑标准不可用',
  LOGICAL_ATTRIBUTE_UNMAPPED: '缺少物理字段',
};
const isCandidate = (result: string) => result === 'CANDIDATE_ONLY' || result === 'CANDIDATE';

/** Compares exact Owner versions without persisting or approving a mapping. */
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
  const [catalogAttempt, setCatalogAttempt] = useState(0);
  const [versionAttempt, setVersionAttempt] = useState(0);
  const previewRequest = useRef(0);
  const activeScope = useRef(scope);
  activeScope.current = scope;

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

  const clearPreview = () => {
    previewRequest.current += 1;
    setPreview(undefined);
    setPreviewError(undefined);
    setPreviewLoading(false);
  };

  useEffect(() => {
    setCatalog(undefined);
    setSelection(undefined);
    setVersions(undefined);
    clearPreview();
    setVersionLoading(false);
    if (!validModel) { setLoading(false); return; }
    let live = true;
    setLoading(true);
    void listLogicalDrafts().then((items) => {
      if (!live) return;
      const sameProcess = (items || []).filter(item => item.processId === processId);
      setCatalog({ scope, drafts: sameProcess });
      // A deep link must resolve to the requested identity, never a same-named replacement.
      if (hinted && hinted.processId === processId && hinted.physicalId === physicalId
          && sameProcess.some(item => item.id === hinted.logicalId)) {
        setSelection({ scope, logicalId: hinted.logicalId, versionNo: hinted.logicalVersionNo });
      }
    }).catch(() => {
      if (live) setCatalog({ scope, drafts: [], error: '逻辑模型加载失败，请重试。' });
    }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; previewRequest.current += 1; };
  }, [scope, validModel, physicalId, processId, hinted?.logicalId, hinted?.logicalVersionNo, hinted?.processId, catalogAttempt]);

  useEffect(() => {
    setVersions(undefined);
    clearPreview();
    if (!validModel || selected == null) { setVersionLoading(false); return; }
    let live = true;
    setVersionLoading(true);
    // Re-read the exact Owner draft before accepting its design versions.
    void Promise.all([getLogicalDraft(selected), listLogicalVersions(selected)])
      .then(([draft, items]) => {
        if (!live) return;
        if (draft.model.id !== selected || draft.model.processId !== processId
            || !drafts.some(x => x.id === selected)) throw new Error('Logical Owner scope mismatch');
        const versionsForDraft = (items || []).filter(x =>
          Number.isSafeInteger(x.versionNo) && x.versionNo > 0)
          .sort((a, b) => b.versionNo - a.versionNo);
        setVersions({ scope, logicalId: selected, items: versionsForDraft });
      }).catch(() => {
        if (live) setVersions({ scope, logicalId: selected, items: [], error: '设计版本加载失败，请重试。' });
      }).finally(() => { if (live) setVersionLoading(false); });
    return () => { live = false; };
  }, [scope, validModel, selected, processId, drafts, versionAttempt]);

  const prepare = async () => {
    if (!validModel || selected == null || version == null
        || !frozenVersions.some(x => x.versionNo === version)) return;
    const requestScope = scope;
    const requestId = ++previewRequest.current;
    setPreview(undefined);
    setPreviewError(undefined);
    setPreviewLoading(true);
    try {
      const evidence = await previewLogicalPhysical(selected, version, physicalId);
      if (requestScope !== activeScope.current || requestId !== previewRequest.current) return;
      if (evidence.logicalModelId !== selected || evidence.logicalVersionNo !== version
          || evidence.physicalModelId !== physicalId
          || !/^[a-fA-F0-9]{64}$/.test(evidence.logicalSnapshotSha256)
          || !/^[a-fA-F0-9]{64}$/.test(evidence.physicalStructureSha256)
          || !Array.isArray(evidence.columns) || !Array.isArray(evidence.blockers)) {
        throw new Error('Owner evidence scope mismatch');
      }
      setPreview({ scope, logicalId: selected, versionNo: version, data: evidence });
    } catch {
      if (requestScope === activeScope.current && requestId === previewRequest.current) {
        setPreviewError({ scope, text: '模型或版本信息已变化，请核对后重新对照。' });
      }
    } finally {
      if (requestScope === activeScope.current && requestId === previewRequest.current) setPreviewLoading(false);
    }
  };

  const openLogical = () => {
    const path = selected == null ? `/modeling/logical?processId=${processId}`
      : logicalDraftNavigationPath(processId!, selected);
    if (path) onNavigateLogical(path);
  };

  if (!supported) return <YakEmpty compact title="该分层无需逻辑模型对照"
    description={(model?.layerCode || '').toUpperCase() === 'ODS' ? 'ODS 模型可直接从来源表设计。' : '此处支持 DWD、DIM 模型。'} />;
  if (!allowed || !currentProject?.id) return <Alert showIcon type="warning"
    message="暂无当前项目的建模查看权限" />;
  if (!validModel) return <YakEmpty compact title="请先关联业务过程"
    description="在模型基本信息中选择业务过程后，可对照相关逻辑模型。" />;

  const selectedDraft = drafts.find(x => x.id === selected);
  const catalogError = catalog?.scope === scope ? catalog.error : undefined;
  const versionError = versions?.scope === scope ? versions.error : undefined;
  const invalidHint = hasHint && (!hinted || hinted.processId !== processId
    || (catalog?.scope === scope && !drafts.some(x => x.id === hinted.logicalId)));
  const missingVersion = hasHint && selected != null && version != null && versions?.scope === scope
    && !versionLoading && !versionError && !frozenVersions.some(x => x.versionNo === version);
  return <Card className="max-w-[1050px]" title="逻辑模型对照" extra={<Tag>只读</Tag>}>
    <Typography.Paragraph type="secondary" className="!mb-5">
      选择逻辑设计版本，查看物理字段对应的业务实体与属性。
    </Typography.Paragraph>
    {invalidHint ? <Alert className="mb-4" showIcon type="warning"
      message="指定逻辑模型不可用，请重新选择" /> : null}
    {catalogError ? <Alert className="mb-4" showIcon type="error" message={catalogError}
      action={<YakButton size="small" onClick={() => setCatalogAttempt(x => x + 1)}>重新加载</YakButton>} /> : null}
    <div className="flex flex-wrap items-end gap-3">
      <div className="min-w-[230px] flex-[2]">
        <label htmlFor="logical-evidence-model" className="mb-2 block text-sm">逻辑模型</label>
        <Select id="logical-evidence-model" showSearch optionFilterProp="label" className="w-full" loading={loading}
          placeholder={loading ? '正在加载…' : '选择当前业务过程的逻辑模型'}
          value={selected} disabled={loading || !!catalogError}
          options={drafts.map(x => ({ value: x.id, label: `${x.name} · ${x.code}` }))}
          onChange={id => { clearPreview(); setSelection({ scope, logicalId: id }); }} />
      </div>
      <div className="min-w-[170px] flex-1">
        <label htmlFor="logical-evidence-version" className="mb-2 block text-sm">设计版本</label>
        <Select id="logical-evidence-version" className="w-full" placeholder="选择已保存版本" loading={versionLoading}
          value={version && frozenVersions.some(x => x.versionNo === version) ? version : undefined}
          disabled={!selected || versionLoading || !frozenVersions.length}
          options={frozenVersions.map(x => ({ value: x.versionNo, label: `V${x.versionNo}` }))}
          onChange={n => { clearPreview(); setSelection({ scope, logicalId: selected, versionNo: n }); }} />
      </div>
      <YakButton type="primary" loading={previewLoading}
        disabled={!selected || !version || !frozenVersions.some(x => x.versionNo === version) || previewLoading}
        onClick={() => void prepare()}>开始对照</YakButton>
      {selectedDraft ? <YakButton onClick={openLogical}>打开逻辑模型</YakButton> : null}
    </div>
    {versionError ? <Alert className="mt-4" showIcon type="error" message={versionError}
      action={<YakButton size="small" onClick={() => setVersionAttempt(x => x + 1)}>重新加载</YakButton>} /> : null}
    {missingVersion ? <Alert className="mt-4" showIcon type="warning" message="该设计版本不可用，请重新选择" /> : null}
    {catalog?.scope === scope && !loading && !drafts.length && !catalogError
      ? <YakEmpty compact className="mt-4" title="暂无相关逻辑模型" description="先为当前业务过程建立逻辑设计。">
        <YakButton onClick={openLogical}>前往逻辑建模</YakButton>
      </YakEmpty> : null}
    {selectedDraft && versions?.scope === scope && !versionLoading && !frozenVersions.length && !versionError
      ? <div className="mt-4 rounded-lg border border-dashed border-[#dfe3e8] px-4 py-3 text-sm text-[#667085]">
        该模型还没有设计版本，请打开逻辑模型并保存设计版本后再对照。
      </div> : null}
    {previewError?.scope === scope ? <Alert className="mt-4" type="error" showIcon message={previewError.text} /> : null}
    <Spin spinning={previewLoading}>
      {ready ? <div className="mt-5">
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-[#f7f8fa] px-4 py-3" role="status">
          <div className="flex flex-wrap items-center gap-2">
            <Typography.Text strong>V{ready.logicalVersionNo} 对照结果</Typography.Text>
            <Tag color={ready.readyForReview && !ready.blockers.length ? 'blue' : 'gold'}>
              {ready.readyForReview && !ready.blockers.length ? '可供人工对照' : '待完善'}
            </Tag>
          </div>
          <Typography.Text type="secondary">{ready.columns.length} 项 · {ready.columns.filter(x => !isCandidate(x.result)).length} 项待处理</Typography.Text>
        </div>
        {ready.blockers.length ? <Alert className="mt-3" showIcon type="warning" message="请处理以下问题后重新对照"
          description={<ul className="m-0 pl-4">{ready.blockers.map((x, i) => <li key={`${i}-${x}`}>{x}</li>)}</ul>} /> : null}
        <Table className="mt-4" size="small" scroll={{ x: 790 }}
          rowKey={(x, i) => String(x.physicalColumnId ?? `candidate-${i}`)}
          pagination={{ pageSize: 10, hideOnSinglePage: true }} dataSource={ready.columns}
          columns={[
            { title: '物理字段', dataIndex: 'physicalColumn', width: 170, render: (x?: string) => x || '—' },
            { title: '逻辑实体', dataIndex: 'logicalEntity', width: 140, render: (x?: string) => x || '未关联' },
            { title: '逻辑属性', dataIndex: 'logicalAttribute', width: 140, render: (x?: string) => x || '未关联' },
            { title: '对照结果', dataIndex: 'result', width: 160, render: (x: string) => <Tag color={isCandidate(x) ? 'blue' : 'gold'}>{RESULT_LABELS[x] || x}</Tag> },
            { title: '说明', dataIndex: 'reason' },
          ]} />
        <Typography.Paragraph type="secondary" className="!mt-3 !mb-3 text-xs">
          对照结果供设计参考，不保存字段映射。
        </Typography.Paragraph>
        <Collapse ghost size="small" items={[{ key: 'identity', label: '版本与核验信息', children: (
          <Descriptions size="small" column={1}>
            <Descriptions.Item label="逻辑模型">{selectedDraft?.name} · #{ready.logicalModelId} · V{ready.logicalVersionNo}</Descriptions.Item>
            <Descriptions.Item label="物理模型">{model?.name} · #{ready.physicalModelId}</Descriptions.Item>
            <Descriptions.Item label="业务过程">#{processId}</Descriptions.Item>
            <Descriptions.Item label="逻辑版本摘要"><Typography.Text className="break-all" copyable>{ready.logicalSnapshotSha256}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label="物理结构摘要"><Typography.Text className="break-all" copyable>{ready.physicalStructureSha256}</Typography.Text></Descriptions.Item>
          </Descriptions>
        ) }]} />
      </div> : null}
    </Spin>
  </Card>;
}
