import { history, useSearchParams } from '@umijs/max';
import {
  Alert, Button, Card, Empty, Form, Input, Modal, Select, Space, Table, Tag, Typography, message,
} from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { matchesSavedDwdMapping, restoreSavedDwdMapping } from './dwdMappingReview';
import { getDwdMappingDraft, saveDwdMappingDraft, freezeDwdMappingDraft,
  publishDwdWithMapping, getPublishedDwdMapping,
  getDwdPublicationPolicy, submitDwdMappingPublishApproval, listDwdMappingRevisions,
  type DwdMappingDraft, type DwdMappingFrozen, type DwdBoundPublication,
} from '@/services/modeling/dwdMapping';
import { getSemanticProcess, listSemanticProcessFields, pageSemanticProcesses } from '@/services/semantic/api';
import { pageModelingModels } from '@/services/modeling/api';
import { physicalEvidencePath } from '@/utils/logicalPhysicalJourney';
import type { ModelingModelRecord } from '@/services/modeling/types';
import type { SemanticFieldRecord, SemanticProcessRecord } from '@/services/semantic/types';
import { locateCurrentLogicalAttribute, locateCurrentLogicalRelation } from './governanceRepairLocator';
import { compareLogicalGovernance, logicalGovernanceBaseline,
  type LogicalGovernanceBaseline } from './governanceRecheck';
import {
  chooseJourneyLogicalModel, exactJourneyLogicalDraft, latestJourneyFrozenVersion,
  logicalDraftsForProcess, parseJourneyProcessId,
  processToPhysical, processToSemantic,
} from '@/utils/journeyNavigation';
import {
  addLogicalAttribute, addLogicalEntity, addLogicalRelation, createLogicalDraft,
  freezeLogicalDraft, getLogicalDraft, getLogicalVersionSnapshot, listLogicalDrafts,
  listLogicalVersions, updateLogicalEntity, updateLogicalAttribute, updateLogicalRelation,
  previewLogicalPhysical, reviewLogicalPhysical, reviewLogicalVersionSemantics,
  type LogicalPhysicalPreview, type LogicalPhysicalReview, type LogicalVersionSemanticReview,
  type LogicalDraftDetail, type LogicalDraftVersion, type LogicalModelDraft,
  type LogicalRelation,
} from '@/services/modeling/logical';

type CreateValues = { processId: number; code: string; name: string; description?: string };
type EntityValues = { code: string; name: string; businessName?: string; description?: string };
type AttributeValues = { code: string; name: string; stdFieldId?: number; logicalType?: string;
  primaryFlag?: boolean; nullable?: boolean; description?: string };
type RelationValues = { sourceEntityId: number; targetEntityId: number;
  cardinality: LogicalRelation['cardinality']; description?: string };

const errorText = (error: unknown): string =>
  error instanceof Error ? error.message : '操作失败，请检查项目权限及数据状态';

/**
 * Draft-first business modeling. Semantic owns field/process definitions;
 * this page never claims that snapshots are deployed/published physical tables.
 */
export default function LogicalWorkspace() {
  const [searchParams] = useSearchParams();
  const processParam = searchParams.get('processId');
  const requestedProcessId = parseJourneyProcessId(processParam);
  const reviewRequested = searchParams.get('review') === 'semantic';
  const physicalReturnRequested = searchParams.get('review') === 'physical';
  const draftParam = searchParams.get('draftId');
  const requestedDraftId = parseJourneyProcessId(draftParam);
  const { currentProject } = useSecurityProject();
  const { can } = usePermissionAccess();
  const canSaveMapping = can('modeling:update');
  const [reviewEntryProblem, setReviewEntryProblem] = useState<string>();
  const [journeyProcess, setJourneyProcess] = useState<SemanticProcessRecord>();
  const [journeyProblem, setJourneyProblem] = useState<string>();
  const loadEpoch = useRef(0);
  const [models, setModels] = useState<LogicalModelDraft[]>([]);
  const [processes, setProcesses] = useState<SemanticProcessRecord[]>([]);
  const [selected, setSelected] = useState<number>();
  const [detail, setDetail] = useState<LogicalDraftDetail>();
  const [versions, setVersions] = useState<LogicalDraftVersion[]>([]);
  const [fields, setFields] = useState<SemanticFieldRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [entityOpen, setEntityOpen] = useState(false);
  const [editEntityId, setEditEntityId] = useState<number>();
  const [editAttributeId, setEditAttributeId] = useState<number>();
  const [editRelationId, setEditRelationId] = useState<number>();
  const [attributeEntityId, setAttributeEntityId] = useState<number>();
  const [relationOpen, setRelationOpen] = useState(false);
  const [snapshotOpen, setSnapshotOpen] = useState(false);
  const [snapshotText, setSnapshotText] = useState('');
  const [semanticReview, setSemanticReview] = useState<LogicalVersionSemanticReview>();
  const [reviewFeedback, setReviewFeedback] = useState<{
    projectId?: number | string; baseline: LogicalGovernanceBaseline;
  }>();
  const [semanticReviewLoading, setSemanticReviewLoading] = useState(false);
  const semanticReviewEpoch = useRef(0);
  const [physicalModelId, setPhysicalModelId] = useState<number | null>(null);
  const [physicalSearch, setPhysicalSearch] = useState('');
  const [physicalTargets, setPhysicalTargets] = useState<ModelingModelRecord[]>([]);
  const [physicalTargetsLoading, setPhysicalTargetsLoading] = useState(false);
  const [handoffPreview, setHandoffPreview] = useState<LogicalPhysicalPreview>();
  const [handoffLoading, setHandoffLoading] = useState(false);
  const [mappingSelections, setMappingSelections] = useState<Record<number, number>>({});
  const [mappingKinds, setMappingKinds] = useState<Record<number, 'DIRECT' | 'RENAME'>>({});
  const [mappingDraft, setMappingDraft] = useState<DwdMappingDraft>();
  const [mappingFrozen, setMappingFrozen] = useState<DwdMappingFrozen>();
  const [mappingPublished, setMappingPublished] = useState<DwdBoundPublication>();
  const [mappingApprovalPending, setMappingApprovalPending] = useState(false);
  const [mappingPersistBusy, setMappingPersistBusy] = useState(false);
  const [mappingPersistError, setMappingPersistError] = useState<string>();
  const mappingEpoch = useRef(0);
  const [reviewResult, setReviewResult] = useState<LogicalPhysicalReview>();
  const [reviewSelectionSnapshot, setReviewSelectionSnapshot] = useState('');
  const [reviewLoading, setReviewLoading] = useState(false);
  const [createForm] = Form.useForm<CreateValues>();
  const [entityForm] = Form.useForm<EntityValues>();
  const [attributeForm] = Form.useForm<AttributeValues>();
  const [relationForm] = Form.useForm<RelationValues>();

  const reload = useCallback(async (id?: number) => {
    const epoch = ++loadEpoch.current;
    semanticReviewEpoch.current += 1; setSemanticReview(undefined);
    setSemanticReviewLoading(false);
    setReviewEntryProblem(undefined);
    setReviewFeedback(undefined);
    setHandoffPreview(undefined);
    setReviewResult(undefined);
    setMappingSelections({});
    setMappingKinds({});
    mappingEpoch.current += 1;
    setMappingDraft(undefined);
    setMappingFrozen(undefined);
    setMappingPublished(undefined);
    setMappingApprovalPending(false);
    setMappingPersistError(undefined);
    setJourneyProblem(undefined);
    setJourneyProcess(undefined);
    setModels([]);
    setProcesses([]);
    setLoading(true);
    setDetail(undefined);
    setVersions([]);
    setSelected(undefined);
    try {
      if (processParam !== null && requestedProcessId === undefined) {
        throw new Error('业务过程 URL 参数不是有效的正整数，请返回业务过程列表重新选择');
      }
      if ((reviewRequested || physicalReturnRequested) && (!requestedProcessId || !requestedDraftId || draftParam === null)) {
        throw new Error('治理入口缺少合法的业务过程或逻辑草稿身份，请从业务过程重新选择');
      }
      // The URL only carries a hint. Resolve with Semantic's authenticated,
      // current-project owner API before selecting any existing logical draft.
      const [next, all, authorizedProcess] = await Promise.all([
        listLogicalDrafts(),
        pageSemanticProcesses({ pageNo: 1, pageSize: 200 }),
        requestedProcessId ? getSemanticProcess(requestedProcessId) : Promise.resolve(undefined),
      ]);
      if (epoch !== loadEpoch.current) return;
      if (requestedProcessId && (!authorizedProcess
          || Number(authorizedProcess.id) !== requestedProcessId)) {
        throw new Error('业务过程身份与当前项目不一致');
      }
      const eligible = logicalDraftsForProcess(next, requestedProcessId);
      setJourneyProcess(authorizedProcess);
      setModels(eligible);
      setProcesses(authorizedProcess ? [authorizedProcess] : (all.bizData ?? []));
      const active = (reviewRequested || physicalReturnRequested)
        ? exactJourneyLogicalDraft(eligible, requestedProcessId!, requestedDraftId!)?.id
        : chooseJourneyLogicalModel(eligible, requestedProcessId, id);
      if ((reviewRequested || physicalReturnRequested) && active === undefined) {
        throw new Error('目标逻辑草稿不存在于当前有权访问的业务过程中，不会选中其他草稿');
      }
      setSelected(active);
      if (active !== undefined) {
        const [data, historyVersions] = await Promise.all([
          getLogicalDraft(active), listLogicalVersions(active),
        ]);
        if (epoch !== loadEpoch.current) return;
        if (requestedProcessId && data.model.processId !== requestedProcessId) {
          throw new Error('逻辑草稿不属于指定业务过程');
        }
        setDetail(data);
        setVersions(historyVersions);
        if (reviewRequested && active === requestedDraftId) {
          const versionNo = latestJourneyFrozenVersion(historyVersions);
          if (versionNo === undefined) {
            setReviewEntryProblem('该逻辑草稿没有可审阅的冻结版本，请先人工冻结设计；当前草稿不是历史版本。');
          } else {
            const reviewEpoch = semanticReviewEpoch.current;
            setSemanticReviewLoading(true);
            try {
              const evidence = await reviewLogicalVersionSemantics(active, versionNo);
              if (epoch === loadEpoch.current && reviewEpoch === semanticReviewEpoch.current
                  && evidence.logicalModelId === active && evidence.versionNo === versionNo) {
                setSemanticReview(evidence);
              }
            } catch (error) {
              if (epoch === loadEpoch.current && reviewEpoch === semanticReviewEpoch.current) {
                setReviewEntryProblem(`冻结版本 V${versionNo} 的治理核验暂不可读取：${errorText(error)}`);
              }
            } finally {
              if (epoch === loadEpoch.current && reviewEpoch === semanticReviewEpoch.current) {
                setSemanticReviewLoading(false);
              }
            }
          }
        }
      }
    } catch (err) {
      if (epoch !== loadEpoch.current) return;
      setModels([]);
      setProcesses([]);
      setSelected(undefined);
      setDetail(undefined);
      setVersions([]);
      setJourneyProblem(errorText(err));
      message.error(errorText(err));
    } finally {
      if (epoch === loadEpoch.current) setLoading(false);
    }
  }, [processParam, requestedProcessId, requestedDraftId, draftParam,
    reviewRequested, physicalReturnRequested, currentProject?.id]);

  useEffect(() => { void reload(); }, [reload]);

  // Reuse Modeling's project-scoped catalog rather than expecting builders to know
  // physical-model database IDs. Server-side search also covers later pages.
  useEffect(() => {
    if (!detail) {
      setPhysicalTargets([]);
      setPhysicalTargetsLoading(false);
      return;
    }
    let active = true;
    setPhysicalTargetsLoading(true);
    const timeout = setTimeout(() => {
      void pageModelingModels({
        pageNo: 1, pageSize: 50, keyword: physicalSearch.trim() || undefined,
      }).then((page) => {
        if (active) setPhysicalTargets(page.bizData ?? []);
      }).catch(() => {
        if (active) {
          setPhysicalTargets([]);
          message.error('物理模型列表加载失败，请检查当前项目和读取权限');
        }
      }).finally(() => {
        if (active) setPhysicalTargetsLoading(false);
      });
    }, 250);
    return () => { active = false; clearTimeout(timeout); };
  }, [detail?.model.id, physicalSearch]);

  useEffect(() => {
    if (!detail?.process?.id) { setFields([]); return; }
    let active = true;
    listSemanticProcessFields(detail.process.id)
      .then((result) => { if (active) setFields((result || []).filter((field) => field.status === 'ENABLED')); })
      .catch(() => { if (active) setFields([]); });
    return () => { active = false; };
  }, [detail?.process?.id]);

  const selectModel = useCallback(async (id: number) => {
    if (!models.some((model) => model.id === id)) return;
    const epoch = ++loadEpoch.current;
    semanticReviewEpoch.current += 1; setSemanticReview(undefined);
    setReviewFeedback(undefined);
    setHandoffPreview(undefined);
    setReviewResult(undefined);
    setMappingSelections({});
    setPhysicalModelId(null);
    setPhysicalSearch('');
    setSelected(id);
    setLoading(true);
    try {
      const [data, previous] = await Promise.all([getLogicalDraft(id), listLogicalVersions(id)]);
      if (epoch !== loadEpoch.current) return;
      if (requestedProcessId && data.model.processId !== requestedProcessId) {
        throw new Error('逻辑草稿不属于所选业务过程');
      }
      setDetail(data);
      setVersions(previous);
    } catch (err) {
      if (epoch === loadEpoch.current) {
        message.error(errorText(err));
        setDetail(undefined);
      }
    } finally { if (epoch === loadEpoch.current) setLoading(false); }
  }, [models, requestedProcessId]);

  const perform = async (action: () => Promise<LogicalDraftDetail>, successText: string,
    close: () => void): Promise<LogicalDraftDetail | undefined> => {
    setSaving(true);
    try {
      const data = await action();
      setDetail(data);
      setHandoffPreview(undefined);
      setReviewResult(undefined);
      setMappingSelections({});
      close();
      message.success(successText);
      const next = await listLogicalDrafts();
      setModels(logicalDraftsForProcess(next, requestedProcessId));
      return data;
    } catch (err) {
      message.error(errorText(err));
      return undefined;
    } finally { setSaving(false); }
  };

  const entities = useMemo(() => detail?.entities.map((item) => item.entity) ?? [], [detail]);
  const openCreate = () => {
    createForm.resetFields();
    if (journeyProcess) createForm.setFieldsValue({ processId: journeyProcess.id });
    setCreateOpen(true);
  };

  const create = async () => {
    const values = await createForm.validateFields();
    const created = await perform(() => createLogicalDraft(values), '已创建逻辑设计草稿，尚未发布或生成物理表', () => {
      setCreateOpen(false);
      createForm.resetFields();
    });
    if (created) await reload(created.model.id);
  };
  const addEntity = async () => {
    if (selected == null || !detail) return;
    const values = await entityForm.validateFields();
    await perform(() => editEntityId == null
      ? addLogicalEntity(selected, values, detail.model.draftRevision)
      : updateLogicalEntity(selected, editEntityId, values, detail.model.draftRevision),
    '已保存逻辑实体', () => {
      setEntityOpen(false); setEditEntityId(undefined); entityForm.resetFields();
    });
  };
  const addAttribute = async () => {
    if (selected == null || attributeEntityId == null || !detail) return;
    const values = await attributeForm.validateFields();
    await perform(() => editAttributeId == null
      ? addLogicalAttribute(selected, attributeEntityId, values, detail.model.draftRevision)
      : updateLogicalAttribute(selected, attributeEntityId, editAttributeId, values, detail.model.draftRevision),
    '已保存业务属性', () => {
      setAttributeEntityId(undefined); setEditAttributeId(undefined); attributeForm.resetFields();
    });
  };
  const addRelation = async () => {
    if (selected == null || !detail) return;
    const values = await relationForm.validateFields();
    await perform(() => editRelationId == null
      ? addLogicalRelation(selected, values, detail.model.draftRevision)
      : updateLogicalRelation(selected, editRelationId, values, detail.model.draftRevision),
    '已保存待确认或已确认的逻辑关系', () => {
      setRelationOpen(false); setEditRelationId(undefined); relationForm.resetFields();
    });
  };
  const editEntity = (id?: number) => {
    setEditEntityId(id);
    entityForm.resetFields();
    const current = detail?.entities.find(x => x.entity.id === id)?.entity;
    if (current) entityForm.setFieldsValue(current);
    setEntityOpen(true);
  };
  const editAttribute = (entityId: number, id?: number) => {
    setAttributeEntityId(entityId);
    setEditAttributeId(id);
    attributeForm.resetFields();
    const current = detail?.entities.find(x => x.entity.id === entityId)
      ?.attributes.find(x => x.id === id);
    if (current) attributeForm.setFieldsValue(current);
  };
  const editRelation = (id?: number) => {
    setEditRelationId(id);
    relationForm.resetFields();
    const current = detail?.relations.find(x => x.id === id);
    if (current) relationForm.setFieldsValue(current);
    setRelationOpen(true);
  };

  const freeze = async () => {
    if (selected == null) return;
    setSaving(true);
    try {
      const version = await freezeLogicalDraft(selected, detail?.model.draftRevision ?? -1);
      setVersions(await listLogicalVersions(selected));
      message.success(`已冻结第 ${version.versionNo} 个逻辑设计草稿快照；未发布、未部署`);
    } catch (err) { message.error(errorText(err)); }
    finally { setSaving(false); }
  };
  const previewPhysical = async (versionNo: number) => {
    if (selected == null || physicalModelId == null) {
      message.warning('请先从项目模型目录中选择要对照的物理模型');
      return;
    }
    setHandoffPreview(undefined);
    setReviewResult(undefined);
    setMappingSelections({});
    setHandoffLoading(true);
    try {
      setHandoffPreview(await previewLogicalPhysical(selected, versionNo, physicalModelId));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setHandoffLoading(false);
    }
  };

  // Persisted MappingSet is a DESIGN DRAFT. Never equate it to physical publishing.
  useEffect(() => {
    const epoch = ++mappingEpoch.current;
    setMappingDraft(undefined);
    setMappingFrozen(undefined);
    setMappingPublished(undefined);
    setMappingApprovalPending(false);
    setMappingPersistError(undefined);
    if (handoffPreview?.physicalLayer !== 'DWD' || physicalModelId == null) return;
    let live = true;
    // Restore server-persisted freeze evidence as well as the MappingSet draft.
    // Never infer a frozen version from local browser state or a different revision.
    void Promise.all([
      getDwdMappingDraft(physicalModelId),
      listDwdMappingRevisions(physicalModelId),
      getDwdPublicationPolicy(physicalModelId),
    ]).then(([draft, revisions, policy]) => {
      if (!live || epoch !== mappingEpoch.current) return;
      if (draft && draft.physicalModelId === physicalModelId
          && draft.logicalModelId === handoffPreview.logicalModelId
          && draft.logicalVersionNo === handoffPreview.logicalVersionNo) {
        setMappingDraft(draft);
        // A pending approval outlives browser reloads: derive the state from
        // the current project's approval owner, not only local React state.
        setMappingApprovalPending(policy.approvalPending);
        const latest = revisions[0];
        if (draft.logicalSnapshotSha256 === handoffPreview.logicalSnapshotSha256
            && draft.physicalStructureSha256 === handoffPreview.physicalStructureSha256
            && latest?.mappingSetId === draft.mappingSetId
            && latest?.draftRevision === draft.draftRevision
            && latest?.mappingRevisionId > 0
            && /^[a-f0-9]{64}$/.test(latest.contentSha256)) {
          setMappingFrozen(latest);
        }
      } else if (draft) {
        setMappingPersistError('目标 DWD 已保存另一份逻辑设计映射；请在对应物理模型中核对，不允许覆盖。');
      }
    }).catch(() => {
      if (live && epoch === mappingEpoch.current) {
        setMappingPersistError('无法完整读取已保存映射与冻结历史；禁止盲目覆盖或发布。');
      }
    });
    return () => { live = false; };
  }, [currentProject?.id, physicalModelId, handoffPreview?.logicalModelId,
      handoffPreview?.logicalVersionNo, handoffPreview?.physicalStructureSha256]);

  const saveMappingEvidence = async () => {
    if (!canSaveMapping || !handoffPreview || handoffPreview.physicalLayer !== 'DWD'
        || physicalModelId == null || selected == null
        || reviewSelectionSnapshot !== JSON.stringify(mappingSelections)
        || !reviewResult?.readyForManualDesign || mappingPersistError
        || Object.keys(mappingSelections).some(k => !mappingKinds[Number(k)])) return;
    const epoch = mappingEpoch.current;
    const reloadEpoch = loadEpoch.current;
    setMappingPersistBusy(true);
    try {
      const data = await saveDwdMappingDraft(physicalModelId, {
        logicalModelId: selected,
        logicalVersionNo: handoffPreview.logicalVersionNo,
        logicalSnapshotSha256: handoffPreview.logicalSnapshotSha256,
        physicalStructureSha256: handoffPreview.physicalStructureSha256,
        expectedDraftRevision: mappingDraft?.draftRevision ?? 0,
        rules: Object.entries(mappingSelections).map(([key, logicalAttributeId]) => ({
          physicalColumnId: Number(key), logicalAttributeId,
          kind: mappingKinds[Number(key)],
        })),
      });
      if (epoch !== mappingEpoch.current || reloadEpoch !== loadEpoch.current) return;
      if (data.logicalModelId !== selected || data.physicalModelId !== physicalModelId
          || data.logicalSnapshotSha256 !== handoffPreview.logicalSnapshotSha256
          || data.physicalStructureSha256 !== handoffPreview.physicalStructureSha256) {
        throw new Error('服务端返回的映射身份与当前工作区不一致');
      }
      setMappingDraft(data);
      setMappingFrozen(undefined);
      setMappingPublished(undefined);
      // Never clear an in-flight approval from a local save. Re-read the
      // server-owned policy after a successful save so approval completion or
      // rejection is reflected immediately without trusting stale browser state.
      try {
        const policy = await getDwdPublicationPolicy(physicalModelId);
        if (epoch === mappingEpoch.current && reloadEpoch === loadEpoch.current) {
          setMappingApprovalPending(policy.approvalPending);
        }
      } catch {
        // Keep the existing approval state when the supplementary policy read
        // is unavailable; reload will reconcile it from the server.
      }
      message.success('MappingSet 草稿已持久保存；尚未冻结或发布物理版本');
    } catch (error) {
      if (epoch === mappingEpoch.current && reloadEpoch === loadEpoch.current) {
        message.error(errorText(error));
      }
    } finally {
      if (epoch === mappingEpoch.current) setMappingPersistBusy(false);
    }
  };

  const freezeMappingEvidence = async () => {
    if (!canSaveMapping || !mappingDraft || !handoffPreview || physicalModelId == null
        || mappingDraft.physicalStructureSha256 !== handoffPreview.physicalStructureSha256
        || mappingDraft.logicalSnapshotSha256 !== handoffPreview.logicalSnapshotSha256) return;
    const epoch = mappingEpoch.current;
    const reloadEpoch = loadEpoch.current;
    setMappingPersistBusy(true);
    try {
      const frozen = await freezeDwdMappingDraft(physicalModelId, mappingDraft.draftRevision);
      if (epoch !== mappingEpoch.current || reloadEpoch !== loadEpoch.current) return;
      if (frozen.mappingSetId !== mappingDraft.mappingSetId
          || frozen.draftRevision !== mappingDraft.draftRevision) {
        throw new Error('冻结修订身份或草稿序号不一致，拒绝显示成功');
      }
      setMappingFrozen(frozen);
      message.success('映射冻结成功。需要另行确认才会发布 DWD 设计版本。');
    } catch (error) {
      if (epoch === mappingEpoch.current && reloadEpoch === loadEpoch.current) {
        message.error(errorText(error));
      }
    } finally {
      if (epoch === mappingEpoch.current) setMappingPersistBusy(false);
    }
  };

  /** Explicit, human-confirmed write; then re-read the canonical immutable version. */
  const publishMappingDesign = () => {
    if (!canSaveMapping || !mappingDraft || !mappingFrozen || !handoffPreview
        || !mappingMatchesEditor || physicalModelId == null
        || mappingFrozen.mappingSetId !== mappingDraft.mappingSetId
        || mappingFrozen.draftRevision !== mappingDraft.draftRevision
        || handoffPreview.physicalStatus !== 'DRAFT'
        || mappingPersistBusy || mappingPublished || mappingApprovalPending) return;
    const physicalId = physicalModelId;
    const revisionId = mappingFrozen.mappingRevisionId;
    const epoch = mappingEpoch.current;
    const reloadEpoch = loadEpoch.current;
    Modal.confirm({
      title: '确认发布 DWD 设计版本？',
      content: `将 DWD 模型 #${physicalId} 与 MappingRevision #${revisionId} 显式绑定发布。仅保存不可变设计版本；不会创建物理表、DataDev 任务或执行 SQL。此入口仍属 R12 受控交付，标准发布门禁待专项签收。`,
      okText: '确认发布设计',
      cancelText: '返回审阅',
      onOk: async () => {
        setMappingPersistBusy(true);
        try {
          const policy = await getDwdPublicationPolicy(physicalId);
          if (policy.approvalPending) {
            if (epoch === mappingEpoch.current && reloadEpoch === loadEpoch.current) {
              setMappingApprovalPending(true);
              message.info('该物理模型已有在途发布审批，请从审批中心继续');
            }
            return;
          }
          if (policy.approvalRequired) {
            const submitted = await submitDwdMappingPublishApproval(physicalId, revisionId);
            if (submitted.status !== 'PENDING' || submitted.bizId !== String(physicalId)
                || submitted.flowCode !== 'MODEL_PUBLISH') {
              throw new Error('审批回执身份或状态异常，不能标记为已提交');
            }
            if (epoch === mappingEpoch.current && reloadEpoch === loadEpoch.current) {
              setMappingApprovalPending(true);
              message.success('DWD 设计发布审批已提交；审批通过前不会发布物理版本');
            }
            return;
          }
          const receipt = await publishDwdWithMapping(physicalId, revisionId);
          const history = await getPublishedDwdMapping(physicalId, receipt.versionNo);
          if (receipt.state !== 'DESIGN_PUBLISHED_NOT_EXECUTED'
              || receipt.mappingRevisionId !== revisionId
              || history.modelVersionId !== receipt.modelVersionId
              || history.mapping?.mappingRevisionId !== revisionId
              || history.mapping.contentSha256 !== mappingFrozen.contentSha256
              || history.state !== 'DESIGN_PUBLISHED_NOT_EXECUTED') {
            throw new Error('发布回执与不可变历史映射不一致，禁止显示成功');
          }
          if (epoch !== mappingEpoch.current || reloadEpoch !== loadEpoch.current) return;
          setMappingPublished(receipt);
          message.success(`DWD 设计版本 V${receipt.versionNo} 已发布并回读确认；未执行数据加工`);
        } catch (error) {
          if (epoch === mappingEpoch.current && reloadEpoch === loadEpoch.current) {
            message.error(errorText(error));
          }
          throw error;
        } finally {
          if (epoch === mappingEpoch.current) setMappingPersistBusy(false);
        }
      },
    });
  };

  const logicalReviewOptions = useMemo(() => {
    const values = new Map<number, { id: number; stdFieldId?: number; label: string }>();
    handoffPreview?.columns.forEach((row) => {
      if (row.logicalAttributeId != null) {
        values.set(row.logicalAttributeId, {
          id: row.logicalAttributeId,
          stdFieldId: row.physicalStdFieldId,
          label: `${row.logicalEntity || '未命名实体'} · ${row.logicalAttribute || '未命名属性'} (#${row.logicalAttributeId})`,
        });
      }
    });
    return [...values.values()];
  }, [handoffPreview]);

  const validateExplicitReview = async () => {
    if (selected == null || physicalModelId == null || !handoffPreview) return;
    setReviewResult(undefined);
    setReviewSelectionSnapshot('');
    setReviewLoading(true);
    const selectionSnapshot = JSON.stringify(mappingSelections);
    try {
      const receipt = await reviewLogicalPhysical(selected, handoffPreview.logicalVersionNo, physicalModelId, {
        logicalSnapshotSha256: handoffPreview.logicalSnapshotSha256,
        physicalStructureSha256: handoffPreview.physicalStructureSha256,
        selections: Object.entries(mappingSelections).map(([physicalColumnId, logicalAttributeId]) => ({
          physicalColumnId: Number(physicalColumnId), logicalAttributeId,
        })),
      });
      setReviewSelectionSnapshot(selectionSnapshot);
      setReviewResult(receipt);
      if (!receipt.evidenceUnchanged) {
        setHandoffPreview(undefined);
        setMappingSelections({});
        message.warning('版本证据已变化，请重新预检并选择映射');
      }
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setReviewLoading(false);
    }
  };

  const showSemanticReview = async (versionNo: number) => {
    if (selected == null) return;
    const targetModelId = selected;
    const loadRequest = loadEpoch.current;
    const request = ++semanticReviewEpoch.current;
    setSemanticReview(undefined);
    setSemanticReviewLoading(true);
    try {
      const report = await reviewLogicalVersionSemantics(targetModelId, versionNo);
      if (request !== semanticReviewEpoch.current || loadRequest !== loadEpoch.current) return;
      if (report.logicalModelId !== targetModelId || report.versionNo !== versionNo) {
        message.warning('逻辑治理报告身份或冻结版本不匹配，不能作为当前证据');
        return;
      }
      setSemanticReview(report);
    } catch (error) {
      if (request === semanticReviewEpoch.current && loadRequest === loadEpoch.current) {
        message.error(errorText(error));
      }
    } finally {
      if (request === semanticReviewEpoch.current && loadRequest === loadEpoch.current) {
        setSemanticReviewLoading(false);
      }
    }
  };

  /** Re-read Modeling owner before opening an authoring modal from frozen evidence. */
  const openCurrentReviewTarget = async (
    target: { kind: 'attribute'; entityId: number; attributeId: number }
      | { kind: 'relation'; relationId: number },
  ) => {
    if (!semanticReview || selected == null || semanticReview.logicalModelId !== selected) return;
    const requestEpoch = loadEpoch.current;
    const reviewEpoch = semanticReviewEpoch.current;
    const targetModelId = selected;
    try {
      const fresh = await getLogicalDraft(targetModelId);
      if (requestEpoch !== loadEpoch.current || reviewEpoch !== semanticReviewEpoch.current) return;
      if (fresh.model.id !== targetModelId
          || (requestedProcessId && fresh.model.processId !== requestedProcessId)) {
        message.warning('所选逻辑草稿已不属于当前业务过程或项目，请重新打开');
        return;
      }
      if (target.kind === 'attribute') {
        const located = locateCurrentLogicalAttribute(
          targetModelId, fresh, target.entityId, target.attributeId);
        if (!located.ok) { message.warning(located.reason); return; }
        const current = fresh.entities.find(e => e.entity.id === target.entityId)
          ?.attributes.find(a => a.id === target.attributeId);
        if (!current) { message.warning('无法读取当前逻辑属性，请重新审阅'); return; }
        setDetail(fresh);
        attributeForm.resetFields();
        attributeForm.setFieldsValue(current);
        setEditAttributeId(target.attributeId);
        setAttributeEntityId(target.entityId);
      } else {
        const located = locateCurrentLogicalRelation(targetModelId, fresh, target.relationId);
        if (!located.ok) { message.warning(located.reason); return; }
        const current = fresh.relations.find(r => r.id === target.relationId);
        if (!current) { message.warning('无法读取当前逻辑关系，请重新审阅'); return; }
        setDetail(fresh);
        relationForm.resetFields();
        relationForm.setFieldsValue(current);
        setEditRelationId(target.relationId);
        setRelationOpen(true);
      }
      setReviewFeedback({ projectId: currentProject?.id,
        baseline: logicalGovernanceBaseline(semanticReview) });
      setSemanticReview(undefined);
      message.info('已打开当前逻辑草稿对象；冻结历史版本不会改变，请人工核对后再保存');
    } catch (error) {
      if (requestEpoch === loadEpoch.current && reviewEpoch === semanticReviewEpoch.current) {
        message.error(errorText(error));
      }
    }
  };

  const showSnapshot = async (versionNo: number) => {
    if (selected == null) return;
    try {
      const snapshot = await getLogicalVersionSnapshot(selected, versionNo);
      setSnapshotText(snapshot);
      setSnapshotOpen(true);
    } catch (err) { message.error(errorText(err)); }
  };

  const mappingMatchesEditor = matchesSavedDwdMapping(
    mappingDraft, handoffPreview, mappingSelections, mappingKinds,
  );

  const reviewComparison = semanticReview && reviewFeedback
    && reviewFeedback.projectId === currentProject?.id
    && reviewFeedback.baseline.logicalModelId === selected
      ? compareLogicalGovernance(reviewFeedback.baseline, semanticReview) : undefined;

  return (
    <div className="min-h-screen bg-[#f8fafc] p-6">
      <Space className="mb-4" wrap>
        <Button onClick={() => history.push(
          journeyProcess ? processToPhysical(journeyProcess.id) : '/modeling',
        )}>返回物理模型工作台</Button>
        <Typography.Title level={4} className="!mb-0">业务逻辑模型</Typography.Title>
      </Space>
      {journeyProblem && <Alert className="mb-4" showIcon type="error"
        message="无法使用所请求的业务过程上下文" description={journeyProblem}
        action={<Button onClick={() => history.push('/semantic/processes')}>重新选择业务过程</Button>} />}
      {reviewFeedback && reviewFeedback.projectId === currentProject?.id
        && reviewFeedback.baseline.logicalModelId === selected && <Alert className="mb-4" showIcon type="info"
        message={`已定位冻结 V${reviewFeedback.baseline.versionNo} 的当前业务对象`}
        description="修改属性或关系后请先显式保存，再重新核验当前业务设计与标准。冻结历史版本不会被改写，也不会自动审批。"
        action={<Button disabled={saving || loading || semanticReviewLoading}
          onClick={() => void showSemanticReview(reviewFeedback.baseline.versionNo)}>
          修正后重新核验
        </Button>} />}
      {reviewEntryProblem && <Alert className="mb-4" showIcon type="warning"
        message="当前冻结版本治理核验尚不可完成" description={reviewEntryProblem} />}
      {semanticReviewLoading && reviewRequested && <Alert className="mb-4" showIcon type="info"
        message="正在核验指定逻辑草稿最新冻结版本的业务语义及差异…" />}
      {journeyProcess && <Alert className="mb-4" showIcon type="info"
        message={`正在处理业务过程：${journeyProcess.name}（${journeyProcess.code}）`}
        description="仅显示该过程所属逻辑草稿；URL 不授予新权限，也不会自动创建物理模型。"
        action={<Space>
          <Button onClick={() => history.push(processToSemantic(journeyProcess.id))}>返回业务过程</Button>
          <Button onClick={() => history.push(processToPhysical(journeyProcess.id))}>查看该过程物理模型</Button>
          <Button onClick={() => history.push('/modeling/logical')}>查看全部逻辑草稿</Button>
        </Space>} />}
      <Alert className="mb-4" type="info" showIcon
        message="逻辑设计是业务语义与物理模型之间的桥梁"
        description="这里创建和冻结的都是逻辑设计草稿。业务过程、标准字段由 Semantic 正式管理；保存草稿不代表物理模型已经发布、Doris 已建表或数据已执行。" />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[280px_minmax(0,1fr)]">
        <Card title={journeyProcess ? '该业务过程的逻辑草稿' : '项目逻辑模型'}
          extra={<Button type="primary" disabled={Boolean(journeyProblem) || loading}
            onClick={openCreate}>新建草稿</Button>}>
          {models.length === 0 ? <Empty description={journeyProcess
            ? '该业务过程尚无逻辑草稿，可创建第一份草稿' : '尚无逻辑模型，选择业务过程开始'} /> : (
            <div className="flex flex-col gap-2">
              {models.map((model) => (
                <Button key={model.id} block
                  type={model.id === selected ? 'primary' : 'default'}
                  onClick={() => void selectModel(model.id)}>
                  {model.name} · {model.code}
                </Button>
              ))}
            </div>
          )}
        </Card>
        <div className="flex min-w-0 flex-col gap-4">
          {!detail ? <Card loading={loading}><Empty description="选择逻辑草稿查看实体及字段" /></Card> : (
            <>
              <Card title={<Space>{detail.model.name}<Tag>DRAFT</Tag></Space>}
                extra={<Space wrap>
                  <Button onClick={() => editEntity()}>添加业务实体</Button>
                  <Button disabled={entities.length < 2} onClick={() => editRelation()}>定义实体关系</Button>
                  <Button onClick={() => void selectModel(detail.model.id)}>刷新草稿</Button>
                  <Tag>修订 {detail.model.draftRevision}</Tag>
                  <Button type="primary" loading={saving} onClick={() => void freeze()}>冻结草稿快照</Button>
                </Space>}>
                <Typography.Text>业务过程：{detail.process.name}（{detail.process.code}）</Typography.Text>
                <Typography.Paragraph className="!mt-2 !mb-0" type="secondary">
                  业务粒度：{detail.process.grain || '尚未由业务负责人确认'}。
                  实体主键、关系基数、物理目标都需要独立审阅，系统不会按同名自动推断。
                </Typography.Paragraph>
              </Card>
              {detail.entities.map(({ entity, attributes }) => (
                <Card key={entity.id} title={entity.businessName || entity.name}
                  extra={<Space>
                    <Button onClick={() => editEntity(entity.id)}>编辑实体</Button>
                    <Button onClick={() => editAttribute(entity.id)}>添加属性</Button>
                  </Space>}>
                  <Typography.Text type="secondary">{entity.code} · {entity.description || '暂无业务说明'}</Typography.Text>
                  <Table size="small" rowKey="id" pagination={false} className="mt-3"
                    dataSource={attributes} columns={[
                      { title: '属性', dataIndex: 'name', key: 'name' },
                      { title: '编码', dataIndex: 'code', key: 'code' },
                      { title: '逻辑类型', dataIndex: 'logicalType', key: 'logicalType' },
                      { title: '标准字段 ID', dataIndex: 'stdFieldId', key: 'stdFieldId',
                        render: (v?: number) => v == null ? <Tag>待治理</Tag> : <Tag color="blue">{v}</Tag> },
                      { title: '业务主标识', dataIndex: 'primaryFlag', key: 'primaryFlag',
                        render: (v?: boolean) => v ? '用户确认' : '否' },
                      { title: '操作', key: 'op', render: (_, row) =>
                        <Button type="link" onClick={() => editAttribute(entity.id, row.id)}>编辑</Button> },
                    ]} />
                </Card>
              ))}
              <Card title="实体关系（仅用户确认，未知允许保留）">
                <Table rowKey="id" size="small" pagination={false} dataSource={detail.relations}
                  columns={[
                    { title: '来源实体', dataIndex: 'sourceEntityId', key: 'sourceEntityId',
                      render: (id: number) => entities.find((e) => e.id === id)?.businessName || entities.find((e) => e.id === id)?.name || id },
                    { title: '目标实体', dataIndex: 'targetEntityId', key: 'targetEntityId',
                      render: (id: number) => entities.find((e) => e.id === id)?.businessName || entities.find((e) => e.id === id)?.name || id },
                    { title: '业务关系基数', dataIndex: 'cardinality', key: 'cardinality' },
                    { title: '说明', dataIndex: 'description', key: 'description' },
                    { title: '操作', key: 'op', render: (_, row) =>
                      <Button type="link" onClick={() => editRelation(row.id)}>编辑</Button> },
                  ]} />
              </Card>
              <Card title="独立设计快照（不是部署版本）"
                extra={<Space wrap>
                  <Typography.Text type="secondary">对照项目内已有物理模型</Typography.Text>
                  <Select<number>
                    className="min-w-[280px]"
                    style={{ width: 320 }}
                    showSearch
                    allowClear
                    filterOption={false}
                    loading={physicalTargetsLoading}
                    placeholder="按物理模型名称或编码搜索"
                    notFoundContent={physicalTargetsLoading ? '正在查询项目物理模型' : '无匹配模型，可通过旁边入口新建 DWD 草稿'}
                    value={physicalModelId ?? undefined}
                    onSearch={setPhysicalSearch}
                    onClear={() => {
                      setPhysicalModelId(null); setHandoffPreview(undefined);
                      setReviewResult(undefined); setMappingSelections({});
                    }}
                    onChange={(value) => {
                      setPhysicalModelId(value ?? null); setHandoffPreview(undefined);
                      setReviewResult(undefined); setMappingSelections({});
                    }}
                    options={physicalTargets
                      .filter((target) => target.id != null
                        && Number.isSafeInteger(Number(target.id)) && Number(target.id) > 0)
                      .map((target) => ({
                        value: Number(target.id),
                        label: `${target.name || target.code}（${target.code || target.id}） · ${target.layerCode || '未分层'} · ${target.status || '未知状态'}`,
                      }))}
                  />
                  <Button onClick={() => history.push(processToPhysical(detail.model.processId))}>
                    查找或新建 DWD 物理草稿
                  </Button>
                  <Button disabled={physicalModelId == null}
                    onClick={() => history.push(`/modeling/models/${physicalModelId}`)}>
                    打开物理模型
                  </Button>
                </Space>}>
                <Table size="small" rowKey="id" pagination={false} dataSource={versions}
                  columns={[
                    { title: '快照版本', dataIndex: 'versionNo', key: 'versionNo' },
                    { title: '状态', dataIndex: 'status', key: 'status', render: (s: string) => <Tag>{s}</Tag> },
                    { title: '冻结人', dataIndex: 'createdBy', key: 'createdBy' },
                    { title: '查看', key: 'action', render: (_, version: LogicalDraftVersion) =>
                      <Space>
                        <Button type="link" onClick={() => void showSnapshot(version.versionNo)}>原始 JSON</Button>
                        <Button type="link" loading={semanticReviewLoading}
                          onClick={() => void showSemanticReview(version.versionNo)}>
                          审阅语义与变更
                        </Button>
                        <Button loading={handoffLoading} type="link"
                          onClick={() => void previewPhysical(version.versionNo)}>预检物理关联</Button>
                      </Space> },
                  ]} />
              </Card>
            </>
          )}
        </div>
      </div>

      <Modal open={createOpen} title="基于已确认业务过程创建逻辑草稿"
        okText="创建草稿" okButtonProps={{ loading: saving }}
        onOk={() => void create()} onCancel={() => setCreateOpen(false)} destroyOnHidden>
        <Form layout="vertical" form={createForm}>
          <Form.Item label="正式业务过程" name="processId" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" disabled={Boolean(journeyProcess)}
              options={processes.map(p => ({
              label: `${p.name} · ${p.grain || '粒度待确认'}`, value: p.id,
            }))} />
          </Form.Item>
          <Form.Item label="模型编码" name="code" rules={[{ required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_]{0,127}$/, message: '英文字母开头，最多128字符' }]}>
            <Input placeholder="order_domain" />
          </Form.Item>
          <Form.Item label="模型名称" name="name" rules={[{ required: true }]}><Input placeholder="订单业务逻辑模型" /></Form.Item>
          <Form.Item label="业务说明" name="description"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal open={entityOpen} title={editEntityId == null ? '新增业务实体' : '编辑业务实体'} onOk={() => void addEntity()}
        okText="保存实体" okButtonProps={{ loading: saving }} onCancel={() => { setEntityOpen(false); setEditEntityId(undefined); }} destroyOnHidden>
        <Form layout="vertical" form={entityForm}>
          <Form.Item name="code" label="实体编码" rules={[{ required: true }]}><Input placeholder="order" disabled={editEntityId != null} /></Form.Item>
          <Form.Item name="name" label="实体名称" rules={[{ required: true }]}><Input placeholder="Order" /></Form.Item>
          <Form.Item name="businessName" label="业务名称"><Input placeholder="订单" /></Form.Item>
          <Form.Item name="description" label="业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal open={attributeEntityId != null} title={editAttributeId == null ? '新增业务属性' : '编辑业务属性'} okText="保存属性"
        okButtonProps={{ loading: saving }} onOk={() => void addAttribute()}
        onCancel={() => { setAttributeEntityId(undefined); setEditAttributeId(undefined); }} destroyOnHidden>
        <Form layout="vertical" form={attributeForm} initialValues={{ primaryFlag: false, nullable: true }}>
          <Form.Item label="属性编码" name="code" rules={[{ required: true }]}><Input placeholder="order_id" disabled={editAttributeId != null} /></Form.Item>
          <Form.Item label="属性名称" name="name" rules={[{ required: true }]}><Input placeholder="订单标识" /></Form.Item>
          <Form.Item label="标准字段（可先留待确认）" name="stdFieldId">
            <Select allowClear showSearch optionFilterProp="label"
              options={fields.map(f => ({ label: `${f.name} · ${f.code}`, value: f.id }))} />
          </Form.Item>
          <Form.Item label="逻辑类型" name="logicalType"><Input placeholder="STRING / DECIMAL / DATE" /></Form.Item>
          <Form.Item label="是否已确认的业务主标识" name="primaryFlag">
            <Select options={[{ label: '待确认/不是', value: false }, { label: '已确认', value: true }]} />
          </Form.Item>
          <Form.Item label="是否可空" name="nullable">
            <Select options={[{ label: '可空', value: true }, { label: '不可空', value: false }]} />
          </Form.Item>
          <Form.Item name="description" label="业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal open={relationOpen} title={editRelationId == null ? '明确业务关系（绝不自动推断）' : '编辑业务关系'} okText="保存关系"
        okButtonProps={{ loading: saving }} onOk={() => void addRelation()}
        onCancel={() => { setRelationOpen(false); setEditRelationId(undefined); }} destroyOnHidden>
        <Form form={relationForm} layout="vertical">
          <Form.Item name="sourceEntityId" label="来源实体" rules={[{ required: true }]}>
            <Select disabled={editRelationId != null} options={entities.map(e => ({ label: e.businessName || e.name, value: e.id }))} />
          </Form.Item>
          <Form.Item name="targetEntityId" label="目标实体" rules={[{ required: true }]}>
            <Select disabled={editRelationId != null} options={entities.map(e => ({ label: e.businessName || e.name, value: e.id }))} />
          </Form.Item>
          <Form.Item name="cardinality" label="业务关系基数" rules={[{ required: true }]}>
            <Select options={[
              { label: '尚未确认', value: 'UNKNOWN' },
              { label: '一对一', value: 'ONE_TO_ONE' },
              { label: '一对多', value: 'ONE_TO_MANY' },
              { label: '多对一', value: 'MANY_TO_ONE' },
              { label: '多对多', value: 'MANY_TO_MANY' },
            ]} />
          </Form.Item>
          <Form.Item name="description" label="关系的业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>

      {handoffPreview && handoffPreview.logicalModelId === selected
        && handoffPreview.physicalModelId === physicalModelId
        && <Card title={`逻辑 v${handoffPreview.logicalVersionNo} → 物理 #${handoffPreview.physicalModelId} · 只读候选预检`}>
        <Alert type={handoffPreview.blockers.length ? 'warning' : 'info'} showIcon
          message={handoffPreview.blockers.length ? '仍有交接阻断项' : '仅已达到人工审阅条件'}
          description="字段的 stdFieldId 一致只代表可核对候选，不代表 SQL 映射、逻辑版本已发布、物理模型已生成或数据已执行。" />
        <div className="mt-3">
          <Button onClick={() => {
            const target = detail && physicalEvidencePath({
              processId: detail.model.processId,
              logicalId: handoffPreview.logicalModelId,
              logicalVersionNo: handoffPreview.logicalVersionNo,
              physicalId: handoffPreview.physicalModelId,
            });
            if (target) history.push(target);
          }}>打开此物理模型 · 核对逻辑依据</Button>
        </div>
        <div className="mt-3 flex flex-wrap gap-4">
          <Typography.Text type="secondary">冻结逻辑 v{handoffPreview.logicalVersionNo} · SHA-256：
            <Typography.Text copyable={{ text: handoffPreview.logicalSnapshotSha256 }}>
              {handoffPreview.logicalSnapshotSha256.slice(0, 16)}…
            </Typography.Text>
          </Typography.Text>
          <Typography.Text type="secondary">物理 {handoffPreview.physicalStatus} 当前结构 · SHA-256：
            <Typography.Text copyable={{ text: handoffPreview.physicalStructureSha256 }}>
              {handoffPreview.physicalStructureSha256.slice(0, 16)}…
            </Typography.Text>
          </Typography.Text>
        </div>
        <Typography.Paragraph className="!mt-2" type="secondary">
          指纹只标识本次读取的内容，不是物理模型正式版本。任何未来的人工确认必须重新读取并比对两端指纹，发生变化则拒绝提交。
        </Typography.Paragraph>
        {handoffPreview.blockers.map((blocker, i) => (
          <Typography.Paragraph key={i} type="danger" className="!mt-2 !mb-0">{blocker}</Typography.Paragraph>
        ))}
        <Table className="mt-3" size="small"
          rowKey={(row) => row.physicalColumnId == null
            ? `logical-${row.logicalAttributeId ?? row.logicalAttribute}`
            : `physical-${row.physicalColumnId}`}
          pagination={false}
          dataSource={handoffPreview.columns} columns={[
            { title: '物理字段', dataIndex: 'physicalColumn', render: (v?: string) => v || '未覆盖' },
            { title: '物理列 ID', dataIndex: 'physicalColumnId', render: (v?: number) => v ?? '—' },
            { title: '标准字段 ID', dataIndex: 'physicalStdFieldId', render: (id?: number) => id ?? '缺失' },
            { title: '逻辑实体', dataIndex: 'logicalEntity', render: (v?: string) => v || '待确认' },
            { title: '逻辑属性', dataIndex: 'logicalAttribute', render: (v?: string) => v || '待确认' },
            { title: '逻辑属性 ID', dataIndex: 'logicalAttributeId', render: (v?: number) => v ?? '—' },
            { title: '状态', dataIndex: 'result', render: (v: string) => <Tag>{v}</Tag> },
            { title: '原因', dataIndex: 'reason' },
            { title: '规则类型', key: 'ruleKind',
              render: (_, row) => row.physicalColumnId == null ? '—' : (
                <Select<'DIRECT' | 'RENAME'> disabled={mappingApprovalPending} placeholder="请选择规则"
                  className="min-w-[120px]" style={{ width: 130 }}
                  value={mappingKinds[row.physicalColumnId]}
                  options={[{ label: 'DIRECT 原样', value: 'DIRECT' },
                    { label: 'RENAME 改名', value: 'RENAME' }]}
                  onChange={value => {
                    setMappingKinds(prev => ({ ...prev, [row.physicalColumnId!]: value }));
                    setMappingFrozen(undefined);
                    setMappingPublished(undefined);
                  }} />
              ) },
            { title: '人工选择（仅本次浏览器会话）', key: 'selection',
              render: (_, row) => row.physicalColumnId == null ? '—' : (
                <Select<number> disabled={mappingApprovalPending} showSearch optionFilterProp="label" allowClear
                  placeholder="选择逻辑属性" className="min-w-[240px]" style={{ width: 260 }}
                  value={mappingSelections[row.physicalColumnId]}
                  options={logicalReviewOptions
                    .filter((a) => a.stdFieldId != null && a.stdFieldId === row.physicalStdFieldId)
                    .map((a) => ({ label: a.label, value: a.id }))}
                  onChange={(value) => {
                    setMappingSelections((prev) => {
                      const next = { ...prev };
                      if (value == null) delete next[row.physicalColumnId!];
                      else next[row.physicalColumnId!] = value;
                      return next;
                    });
                    setReviewResult(undefined);
                    setMappingFrozen(undefined);
                    setMappingPublished(undefined);
                  }}
                />
              ) },
          ]} />
        <Space wrap className="mt-3">
          <Button type="primary" loading={reviewLoading}
            disabled={Object.keys(mappingSelections).length === 0}
            onClick={() => void validateExplicitReview()}>
            校验本次人工选择
          </Button>
          <Typography.Text type="secondary">
            已选 {Object.keys(mappingSelections).length} 项。先核验身份，再显式选择规则类型并保存 MappingSet；冻结不代表物理版本发布。
          </Typography.Text>
        </Space>
        {handoffPreview.physicalLayer === 'DWD' && <>
          {mappingPersistError && <Alert className="mt-3" type="warning" showIcon
            message="映射持久化暂不可用" description={mappingPersistError} />}
          <Space className="mt-3" wrap>
            <Button loading={mappingPersistBusy}
              disabled={!canSaveMapping || mappingApprovalPending || !!mappingPersistError || !reviewResult?.readyForManualDesign
                || reviewSelectionSnapshot !== JSON.stringify(mappingSelections)
                || Object.keys(mappingSelections).some(k => !mappingKinds[Number(k)])}
              onClick={() => void saveMappingEvidence()}>保存正式 MappingSet 草稿</Button>
            <Button loading={mappingPersistBusy}
              disabled={!canSaveMapping || mappingApprovalPending || !mappingDraft || !mappingMatchesEditor
                || !reviewResult?.readyForManualDesign
                || mappingDraft.logicalSnapshotSha256 !== handoffPreview.logicalSnapshotSha256
                || mappingDraft.physicalStructureSha256 !== handoffPreview.physicalStructureSha256}
              onClick={() => void freezeMappingEvidence()}>冻结 MappingRevision</Button>
          </Space>
          {mappingDraft && <>
            <Alert className="mt-3" showIcon type="info"
              message={`已保存 MappingSet #${mappingDraft.mappingSetId} · 草稿修订 R${mappingDraft.draftRevision}`}
              description="真实持久草稿；不是已批准逻辑版本、物理发布版本或 DataDev 任务。" />
            <Button className="mt-2" onClick={() => {
              const restored = restoreSavedDwdMapping(mappingDraft, handoffPreview);
              if (!restored) {
                message.error('已保存映射与当前列/标准/冻结版本不一致，请重新核验');
                return;
              }
              setMappingSelections(restored.selections);
              setMappingKinds(restored.kinds);
              // Existing frozen revision is immutable and belongs to this exact
              // persisted draft. Revalidate again in the backend at publication.
              setMappingFrozen(previous => previous?.mappingSetId === mappingDraft.mappingSetId
                && previous.draftRevision === mappingDraft.draftRevision ? previous : undefined);
              setMappingPublished(undefined);
              // Preserve the server-owned pending approval status when loading
              // an old draft; only a fresh owner read may clear it.
              setReviewResult(undefined);
              message.info('已载入持久映射；请重新校验。匹配的历史冻结修订可继续用于发布确认。');
            }}>载入已保存映射并重新校验</Button>
          </>}
          {mappingFrozen && <>
            <Alert className="mt-3" showIcon type="success"
              message={`已冻结 MappingRevision #${mappingFrozen.mappingRevisionId} · V${mappingFrozen.revisionNo}`}
              description="冻结仅保存设计证据。正式发布必须显式二次确认；全局 DR-06/PD-010 门禁仍待签收。" />
            <Button className="mt-2" loading={mappingPersistBusy}
              disabled={!canSaveMapping || !!mappingPersistError || !mappingMatchesEditor
                || !mappingDraft || mappingFrozen.mappingSetId !== mappingDraft.mappingSetId
                || mappingFrozen.draftRevision !== mappingDraft.draftRevision
                || handoffPreview.physicalStatus !== 'DRAFT' || !!mappingPublished
                || mappingApprovalPending}
              onClick={publishMappingDesign}>确认发布 DWD 设计版本（不执行任务）</Button>
            {mappingApprovalPending && <Alert showIcon className="mt-2" type="info"
              message="DWD 设计发布审批进行中"
              description="审批尚未通过，不能显示物理版本已发布；请从审批中心继续。" />}
          </>}
          {mappingPublished && <Alert className="mt-3" showIcon type="success"
            message={`DWD 物理设计 V${mappingPublished.versionNo} 已发布 · Version #${mappingPublished.modelVersionId}`}
            description={`已从服务端历史版本核验 MappingRevision #${mappingPublished.mappingRevisionId}；未创建 DataDev 任务或执行数据加工。`} />}
        </>}
        {reviewResult
          && reviewSelectionSnapshot === JSON.stringify(mappingSelections)
          && reviewResult.logicalSnapshotSha256 === handoffPreview.logicalSnapshotSha256
          && reviewResult.physicalStructureSha256 === handoffPreview.physicalStructureSha256
          && reviewResult.logicalModelId === handoffPreview.logicalModelId
          && reviewResult.physicalModelId === handoffPreview.physicalModelId
          && (
          <div className="mt-4">
            <Alert showIcon type={reviewResult.readyForManualDesign ? 'info' : 'warning'}
              message={reviewResult.readyForManualDesign
                ? '本次人工选择通过只读校验（不是正式确认或发布）'
                : '本次选择存在阻断项，尚不能作为正式设计依据'}
              description={`校验状态：${reviewResult.status} · 检查通过的字段关联：${reviewResult.reviewedMappings.length} 项。服务端已重新校验逻辑与物理证据。`} />
            {reviewResult.blockers.map((blocker, idx) => (
              <Typography.Paragraph key={idx} type="danger" className="!mt-2 !mb-0">
                {blocker}
              </Typography.Paragraph>
            ))}
          </div>
        )}
      </Card>}
      <Modal title="冻结逻辑版本：业务语义与草稿差异（只读）"
        width={1000} open={Boolean(semanticReview)} destroyOnHidden
        footer={<Button onClick={() => setSemanticReview(undefined)}>关闭报告</Button>}
        onCancel={() => setSemanticReview(undefined)}>
        {semanticReview && <>
          {reviewComparison && <Alert showIcon className="mb-3"
            type={reviewComparison.state === 'WRONG_OWNER' ? 'error' : 'info'}
            message={reviewComparison.state === 'CHANGED' ? '已重新核验：逻辑治理证据有变化'
              : reviewComparison.state === 'UNCHANGED' ? '已重新核验：逻辑治理证据未变'
                : '证据身份不一致，不能比较'}
            description={reviewComparison.explanation} />}
          <Button className="mb-3" loading={semanticReviewLoading}
            onClick={() => void showSemanticReview(semanticReview.versionNo)}>
            重新读取本冻结版本治理证据
          </Button>
          <Alert className="mb-3" showIcon
            type={semanticReview.reviewStatus === 'NEEDS_REVIEW' ? 'warning' : 'info'}
            message={semanticReview.reviewStatus === 'NEEDS_REVIEW'
              ? '当前存在需要人工核对的业务语义证据'
              : '达到人工审阅条件，不代表逻辑模型已批准'}
            description={`逻辑草稿冻结 V${semanticReview.versionNo}，${semanticReview.designComparison === 'MATCHES_CURRENT'
              ? '与当前业务设计内容一致' : '当前业务设计已有变更'}。报告不会写入映射或发布模型。`} />
          <Space wrap className="mb-3">
            <Tag>实体 {semanticReview.entityCount}</Tag>
            <Tag>属性 {semanticReview.attributeCount}</Tag>
            <Tag>关系 {semanticReview.relationCount}</Tag>
            <Tag>冻结状态：{semanticReview.frozenStatus}</Tag>
          </Space>
          <Typography.Paragraph type="secondary">
            冻结版本证据：
            <Typography.Text copyable={{ text: semanticReview.frozenSnapshotSha256 }}>
              {semanticReview.frozenSnapshotSha256.slice(0, 20)}…
            </Typography.Text>
          </Typography.Paragraph>
          {semanticReview.blockers.map((reason, i) => (
            <Typography.Paragraph type="danger" key={i} className="!mb-1">{reason}</Typography.Paragraph>
          ))}
          <Typography.Title level={5}>业务设计差异</Typography.Title>
          <Table rowKey={(row) => `${row.objectType}-${row.objectId}`} size="small"
            pagination={{ pageSize: 10 }}
            locale={{ emptyText: '该冻结版本与当前逻辑草稿的实体、属性及关系内容一致' }}
            dataSource={semanticReview.changes} columns={[
              { title: '类型', dataIndex: 'objectType', width: 100 },
              { title: '业务对象', dataIndex: 'label' },
              { title: '变化', dataIndex: 'status', width: 150,
                render: (value: string) => <Tag color="warning">{value}</Tag> },
              { title: '冻结内容', dataIndex: 'frozenValue', ellipsis: true,
                render: (value?: string) => value
                  ? <Typography.Text ellipsis={{ tooltip: value }}>{value}</Typography.Text> : '—' },
              { title: '当前内容', dataIndex: 'currentValue', ellipsis: true,
                render: (value?: string) => value
                  ? <Typography.Text ellipsis={{ tooltip: value }}>{value}</Typography.Text> : '—' },
            ]} />
          <Typography.Title level={5}>标准字段当前有效性</Typography.Title>
          <Table size="small" pagination={{ pageSize: 10 }} rowKey="attributeId"
            dataSource={semanticReview.attributes} columns={[
              { title: '实体', dataIndex: 'entityName' },
              { title: '属性', dataIndex: 'attributeName' },
              { title: '标准字段 ID', dataIndex: 'stdFieldId',
                render: (value?: number) => value ?? '待治理' },
              { title: '当前标准', dataIndex: 'standardName',
                render: (value?: string) => value || '—' },
              { title: '标准角色', dataIndex: 'standardRole',
                render: (value?: string) => value || '—' },
              { title: '当前证据', dataIndex: 'standardStatus',
                render: (value: string) => <Tag color={value === 'ENABLED_NOW' ? 'green' : 'warning'}>{value}</Tag> },
              { title: '说明', dataIndex: 'explanation' },
              { title: '操作', key: 'repair', width: 136,
                render: (_: unknown, record: LogicalVersionSemanticReview['attributes'][number]) =>
                  record.standardStatus !== 'ENABLED_NOW'
                    ? <Button type="link" size="small"
                        onClick={() => void openCurrentReviewTarget({
                          kind: 'attribute', entityId: record.entityId, attributeId: record.attributeId,
                        })}>定位当前属性</Button> : '—' },
            ]} />
          <Typography.Title level={5}>关系基数与确认边界</Typography.Title>
          <Table size="small" pagination={{ pageSize: 10 }} rowKey="relationId"
            dataSource={semanticReview.relations} columns={[
              { title: '来源实体', dataIndex: 'sourceEntity' },
              { title: '目标实体', dataIndex: 'targetEntity' },
              { title: '声明基数', dataIndex: 'cardinality' },
              { title: '确认状态', dataIndex: 'status',
                render: (value: string) => <Tag color="warning">{value}</Tag> },
              { title: '说明', dataIndex: 'explanation' },
              { title: '操作', key: 'repair', width: 136,
                render: (_: unknown, record: LogicalVersionSemanticReview['relations'][number]) =>
                  <Button type="link" size="small"
                    onClick={() => void openCurrentReviewTarget({
                      kind: 'relation', relationId: record.relationId,
                    })}>定位当前关系</Button> },
            ]} />
          {semanticReview.limitations.map((reason, i) => (
            <Typography.Paragraph key={i} type="secondary" className="!mb-1">{reason}</Typography.Paragraph>
          ))}
        </>}
      </Modal>
      <Modal width={760} title="冻结的逻辑设计内容（只读）" open={snapshotOpen}
        footer={<Button onClick={() => setSnapshotOpen(false)}>关闭</Button>}
        onCancel={() => setSnapshotOpen(false)}>
        <pre style={{ maxHeight: 420, overflow: 'auto', whiteSpace: 'pre-wrap' }}>{snapshotText}</pre>
      </Modal>
    </div>
  );
}
