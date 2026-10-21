import { history, useSearchParams } from '@umijs/max';
import {
  Alert, Button, Card, Col, Empty, Form, Input, Modal, Row, Select, Space, Steps, Table, Tag, Typography, message,
} from 'antd';
import type { FormInstance } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { matchesSavedDwdMapping, restoreSavedDwdMapping } from './dwdMappingReview';
import DwdStandardReviewCard from './components/DwdStandardReviewCard';
import ModelingStudioHeader from './components/ModelingStudioHeader';
import LogicalDesignStudio from './components/LogicalDesignStudio';
import LogicalSemanticReviewModal from './components/LogicalSemanticReviewModal';
import './logical.less';
import { deriveManualJourney, MANUAL_JOURNEY_LABELS } from './manualModelingJourney';
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

const mappingResultLabels: Record<string, string> = {
  CANDIDATE: '可供对照', CANDIDATE_ONLY: '可供对照',
  MISSING_STANDARD: '未关联标准字段', INVALID_STANDARD: '标准字段不可用',
  AMBIGUOUS_PHYSICAL_REFERENCE: '物理字段待确认', NO_LOGICAL_REFERENCE: '缺少逻辑属性',
  AMBIGUOUS_LOGICAL_REFERENCE: '逻辑属性待确认', MISSING_LOGICAL_STANDARD: '逻辑属性未关联标准',
  INVALID_LOGICAL_STANDARD: '逻辑标准不可用', LOGICAL_ATTRIBUTE_UNMAPPED: '缺少物理字段',
};

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
  const [modelSearch, setModelSearch] = useState('');
  const [detailProblem, setDetailProblem] = useState<string>();
  const [processes, setProcesses] = useState<SemanticProcessRecord[]>([]);
  const [selected, setSelected] = useState<number>();
  const [detail, setDetail] = useState<LogicalDraftDetail>();
  const [versions, setVersions] = useState<LogicalDraftVersion[]>([]);
  const [fields, setFields] = useState<SemanticFieldRecord[]>([]);
  const [fieldsProblem, setFieldsProblem] = useState<string>();
  const [fieldsLoading, setFieldsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const submittingRef = useRef(false);
  const [saveProblem, setSaveProblem] = useState<string>();
  const formBaseline = useRef<Record<string, string>>({});
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
  const repairRequest = useRef(0);
  const [repairLoading, setRepairLoading] = useState(false);
  const snapshotRequest = useRef(0);
  const [physicalModelId, setPhysicalModelId] = useState<number | null>(null);
  const [physicalSearch, setPhysicalSearch] = useState('');
  const [physicalTargets, setPhysicalTargets] = useState<ModelingModelRecord[]>([]);
  const [physicalTargetsLoading, setPhysicalTargetsLoading] = useState(false);
  const [handoffPreview, setHandoffPreview] = useState<LogicalPhysicalPreview>();
  const [handoffLoading, setHandoffLoading] = useState(false);
  const handoffSequence = useRef(0);
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
    repairRequest.current += 1;
    snapshotRequest.current += 1;
    setRepairLoading(false);
    setSnapshotOpen(false);
    setPhysicalModelId(null);
    setPhysicalSearch('');
    setHandoffLoading(false);
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
    setDetailProblem(undefined);
    setSaveProblem(undefined);
    setCreateOpen(false); setEntityOpen(false); setAttributeEntityId(undefined); setRelationOpen(false);
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
        let data: LogicalDraftDetail;
        let historyVersions: LogicalDraftVersion[];
        try {
          [data, historyVersions] = await Promise.all([getLogicalDraft(active), listLogicalVersions(active)]);
        } catch (error) {
          if (epoch === loadEpoch.current) setDetailProblem(errorText(error));
          return;
        }
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

  useEffect(() => {
    void reload();
    return () => { loadEpoch.current += 1; };
  }, [reload]);

  useEffect(() => {
    handoffSequence.current += 1;
    setHandoffLoading(false);
  }, [physicalModelId]);

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
    setFields([]); setFieldsProblem(undefined);
    if (!detail?.process?.id) { setFieldsLoading(false); return; }
    let active = true;
    setFieldsLoading(true);
    listSemanticProcessFields(detail.process.id)
      .then((result) => { if (active) setFields((result || []).filter((field) => field.status === 'ENABLED')); })
      .catch((error) => { if (active) setFieldsProblem(errorText(error)); })
      .finally(() => { if (active) setFieldsLoading(false); });
    return () => { active = false; };
  }, [detail?.process?.id, currentProject?.id]);

  const selectModel = useCallback(async (id: number) => {
    if (savingRef.current || !models.some((model) => model.id === id)) return;
    const epoch = ++loadEpoch.current;
    repairRequest.current += 1;
    snapshotRequest.current += 1;
    setRepairLoading(false);
    setSnapshotOpen(false);
    setHandoffLoading(false);
    semanticReviewEpoch.current += 1; setSemanticReview(undefined);
    setReviewFeedback(undefined);
    setHandoffPreview(undefined);
    setReviewResult(undefined);
    setMappingSelections({});
    setPhysicalModelId(null);
    setPhysicalSearch('');
    setSelected(id);
    setDetail(undefined);
    setVersions([]);
    setDetailProblem(undefined);
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
        setDetailProblem(errorText(err));
        setDetail(undefined);
      }
    } finally { if (epoch === loadEpoch.current) setLoading(false); }
  }, [models, requestedProcessId]);

  const perform = async (action: () => Promise<LogicalDraftDetail>, successText: string,
    close: () => void): Promise<LogicalDraftDetail | undefined> => {
    if (savingRef.current) return undefined;
    savingRef.current = true;
    const epoch = loadEpoch.current;
    setSaveProblem(undefined);
    setSaving(true);
    try {
      const data = await action();
      if (epoch !== loadEpoch.current) return undefined;
      setDetail(data);
      setHandoffPreview(undefined);
      setReviewResult(undefined);
      setMappingSelections({});
      close();
      message.success(successText);
      setModels(previous => {
        const exists = previous.some(model => model.id === data.model.id);
        return logicalDraftsForProcess(exists
          ? previous.map(model => model.id === data.model.id ? data.model : model)
          : [...previous, data.model], requestedProcessId);
      });
      return data;
    } catch (err) {
      if (epoch === loadEpoch.current) setSaveProblem(errorText(err));
      return undefined;
    } finally { savingRef.current = false; setSaving(false); }
  };

  const entities = useMemo(() => detail?.entities.map((item) => item.entity) ?? [], [detail]);
  const openCreate = () => {
    createForm.resetFields();
    if (journeyProcess) createForm.setFieldsValue({ processId: journeyProcess.id });
    formBaseline.current.create = JSON.stringify(createForm.getFieldsValue(true));
    setSaveProblem(undefined);
    setCreateOpen(true);
  };

  const closeEditor = (kind: 'create' | 'entity' | 'attribute' | 'relation', next?: () => void) => {
    if (savingRef.current || submittingRef.current) return;
    const forms = { create: createForm, entity: entityForm, attribute: attributeForm, relation: relationForm };
    const close = () => {
      setSaveProblem(undefined);
      if (kind === 'create') setCreateOpen(false);
      if (kind === 'entity') { setEntityOpen(false); setEditEntityId(undefined); }
      if (kind === 'attribute') { setAttributeEntityId(undefined); setEditAttributeId(undefined); }
      if (kind === 'relation') { setRelationOpen(false); setEditRelationId(undefined); }
      next?.();
    };
    if (JSON.stringify(forms[kind].getFieldsValue(true)) === formBaseline.current[kind]) { close(); return; }
    Modal.confirm({ title: '放弃未保存的修改？',
      content: '离开会放弃当前表单的修改。已保存的草稿和冻结版本不受影响。',
      okText: '放弃修改', cancelText: '继续编辑', onOk: close,
    });
  };

  const validateEditor = async <T extends object,>(form: FormInstance<T>): Promise<T | undefined> => {
    if (savingRef.current || submittingRef.current) return undefined;
    submittingRef.current = true;
    const epoch = loadEpoch.current;
    try {
      const values = await form.validateFields();
      return epoch === loadEpoch.current ? values : undefined;
    } catch {
      // Field errors are displayed by Form; superseded validation must not submit.
      return undefined;
    } finally { submittingRef.current = false; }
  };

  const create = async () => {
    if (savingRef.current) return;
    const values = await validateEditor(createForm);
    if (!values) return;
    const created = await perform(() => createLogicalDraft(values), '逻辑模型已创建', () => {
      setCreateOpen(false);
      createForm.resetFields();
    });
    if (created) await reload(created.model.id);
  };
  const addEntity = async () => {
    if (savingRef.current || selected == null || !detail) return;
    const values = await validateEditor(entityForm);
    if (!values) return;
    await perform(() => editEntityId == null
      ? addLogicalEntity(selected, values, detail.model.draftRevision)
      : updateLogicalEntity(selected, editEntityId, values, detail.model.draftRevision),
    '已保存逻辑实体', () => {
      setEntityOpen(false); setEditEntityId(undefined); entityForm.resetFields();
    });
  };
  const addAttribute = async () => {
    if (savingRef.current || selected == null || attributeEntityId == null || !detail) return;
    const values = await validateEditor(attributeForm);
    if (!values) return;
    await perform(() => editAttributeId == null
      ? addLogicalAttribute(selected, attributeEntityId, values, detail.model.draftRevision)
      : updateLogicalAttribute(selected, attributeEntityId, editAttributeId, values, detail.model.draftRevision),
    '已保存业务属性', () => {
      setAttributeEntityId(undefined); setEditAttributeId(undefined); attributeForm.resetFields();
    });
  };
  const addRelation = async () => {
    if (savingRef.current || selected == null || !detail) return;
    const values = await validateEditor(relationForm);
    if (!values) return;
    await perform(() => editRelationId == null
      ? addLogicalRelation(selected, values, detail.model.draftRevision)
      : updateLogicalRelation(selected, editRelationId, values, detail.model.draftRevision),
    '业务关系已保存', () => {
      setRelationOpen(false); setEditRelationId(undefined); relationForm.resetFields();
    });
  };
  const editEntity = (id?: number) => {
    setEditEntityId(id);
    entityForm.resetFields();
    const current = detail?.entities.find(x => x.entity.id === id)?.entity;
    if (current) entityForm.setFieldsValue(current);
    formBaseline.current.entity = JSON.stringify(entityForm.getFieldsValue(true));
    setSaveProblem(undefined);
    setEntityOpen(true);
  };
  const editAttribute = (entityId: number, id?: number) => {
    setAttributeEntityId(entityId);
    setEditAttributeId(id);
    attributeForm.resetFields();
    const current = detail?.entities.find(x => x.entity.id === entityId)
      ?.attributes.find(x => x.id === id);
    if (current) attributeForm.setFieldsValue(current);
    else attributeForm.setFieldsValue({ primaryFlag: false, nullable: true });
    formBaseline.current.attribute = JSON.stringify(attributeForm.getFieldsValue(true));
    setSaveProblem(undefined);
  };
  const editRelation = (id?: number, sourceEntityId?: number) => {
    setEditRelationId(id);
    relationForm.resetFields();
    const current = detail?.relations.find(x => x.id === id);
    if (current) relationForm.setFieldsValue(current);
    else relationForm.setFieldsValue({ cardinality: 'UNKNOWN', sourceEntityId });
    formBaseline.current.relation = JSON.stringify(relationForm.getFieldsValue(true));
    setSaveProblem(undefined);
    setRelationOpen(true);
  };

  const freeze = async () => {
    if (selected == null || savingRef.current || !detail) return;
    savingRef.current = true;
    const epoch = loadEpoch.current;
    setSaving(true);
    try {
      const version = await freezeLogicalDraft(selected, detail?.model.draftRevision ?? -1);
      if (epoch !== loadEpoch.current) return;
      const historyVersions = await listLogicalVersions(selected);
      if (epoch !== loadEpoch.current) return;
      setVersions(historyVersions);
      message.success(`逻辑设计版本 V${version.versionNo} 已保存`);
    } catch (err) { if (epoch === loadEpoch.current) message.error(errorText(err)); }
    finally { savingRef.current = false; setSaving(false); }
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
    const epoch = loadEpoch.current;
    const sequence = ++handoffSequence.current;
    try {
      const preview = await previewLogicalPhysical(selected, versionNo, physicalModelId);
      if (epoch !== loadEpoch.current || sequence !== handoffSequence.current) return;
      setHandoffPreview(preview);
    } catch (error) {
      if (epoch === loadEpoch.current && sequence === handoffSequence.current) message.error(errorText(error));
    } finally {
      if (epoch === loadEpoch.current && sequence === handoffSequence.current) setHandoffLoading(false);
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
      message.success('字段映射草稿已保存');
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
      message.success(`映射版本 V${frozen.revisionNo} 已保存`);
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
      content: `发布 DWD 模型 #${physicalId}，引用映射版本 #${revisionId}。本次仅发布设计，数据加工需在开发工作台继续。`,
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
    if (!can('modeling:update') || !semanticReview || selected == null || semanticReview.logicalModelId !== selected) return;
    const request = ++repairRequest.current;
    setRepairLoading(true);
    const requestEpoch = loadEpoch.current;
    const reviewEpoch = semanticReviewEpoch.current;
    const targetModelId = selected;
    try {
      const fresh = await getLogicalDraft(targetModelId);
      if (request !== repairRequest.current || requestEpoch !== loadEpoch.current || reviewEpoch !== semanticReviewEpoch.current) return;
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
        formBaseline.current.attribute = JSON.stringify(attributeForm.getFieldsValue(true));
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
        formBaseline.current.relation = JSON.stringify(relationForm.getFieldsValue(true));
        setEditRelationId(target.relationId);
        setRelationOpen(true);
      }
      setReviewFeedback({ projectId: currentProject?.id,
        baseline: logicalGovernanceBaseline(semanticReview) });
      setSaveProblem(undefined);
      setSemanticReview(undefined);
      message.info('已定位当前草稿，请修改后保存');
    } catch (error) {
      if (request === repairRequest.current && requestEpoch === loadEpoch.current && reviewEpoch === semanticReviewEpoch.current) {
        message.error(errorText(error));
      }
    } finally {
      if (request === repairRequest.current) setRepairLoading(false);
    }
  };

  const showSnapshot = async (versionNo: number) => {
    if (selected == null) return;
    const epoch = loadEpoch.current;
    const request = ++snapshotRequest.current;
    try {
      const snapshot = await getLogicalVersionSnapshot(selected, versionNo);
      if (epoch !== loadEpoch.current || request !== snapshotRequest.current) return;
      setSnapshotText(snapshot);
      setSnapshotOpen(true);
    } catch (err) {
      if (epoch === loadEpoch.current && request === snapshotRequest.current) message.error(errorText(err));
    }
  };

  const mappingMatchesEditor = matchesSavedDwdMapping(
    mappingDraft, handoffPreview, mappingSelections, mappingKinds,
  );

  const selectedPreview = handoffPreview
    && handoffPreview.logicalModelId === selected
    && handoffPreview.physicalModelId === physicalModelId ? handoffPreview : undefined;
  const reviewMatchesCurrent = Boolean(selectedPreview && reviewResult?.readyForManualDesign
    && reviewSelectionSnapshot === JSON.stringify(mappingSelections)
    && reviewResult.logicalModelId === selectedPreview.logicalModelId
    && reviewResult.physicalModelId === selectedPreview.physicalModelId
    && reviewResult.logicalSnapshotSha256 === selectedPreview.logicalSnapshotSha256
    && reviewResult.physicalStructureSha256 === selectedPreview.physicalStructureSha256);
  const savedMappingMatchesCurrent = Boolean(selectedPreview && mappingDraft
    && mappingMatchesEditor
    && mappingDraft.logicalSnapshotSha256 === selectedPreview.logicalSnapshotSha256
    && mappingDraft.physicalStructureSha256 === selectedPreview.physicalStructureSha256);
  const frozenMappingMatchesCurrent = Boolean(savedMappingMatchesCurrent && mappingFrozen
    && mappingDraft
    && mappingFrozen.mappingSetId === mappingDraft.mappingSetId
    && mappingFrozen.draftRevision === mappingDraft.draftRevision);
  const manualJourney = deriveManualJourney({
    hasLogicalDraft: Boolean(detail),
    hasExistingDraftChoices: models.length > 0,
    hasFrozenLogicalVersion: latestJourneyFrozenVersion(versions) != null,
    hasPhysicalTarget: physicalModelId != null,
    hasMatchingPreview: Boolean(selectedPreview),
    physicalLayer: selectedPreview?.physicalLayer,
    previewBlocked: Boolean(selectedPreview?.blockers.length),
    reviewReady: reviewMatchesCurrent,
    mappingDraftMatches: savedMappingMatchesCurrent,
    mappingFrozenMatches: frozenMappingMatchesCurrent,
    verifiedPublishedReceipt: Boolean(frozenMappingMatchesCurrent && mappingPublished
      && mappingFrozen && mappingPublished.mappingRevisionId === mappingFrozen.mappingRevisionId
      && mappingPublished.modelVersionId > 0),
    approvalPending: mappingApprovalPending,
    mappingUnavailable: Boolean(mappingPersistError),
  });

  const openManualJourneyAction = () => {
    if (!detail) {
      if (models.length === 0) openCreate();
      else document.getElementById('manual-modeling-pick')
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    if (manualJourney.anchor === 'published' && physicalModelId != null) {
      history.push(`/modeling/models/${physicalModelId}`);
      return;
    }
    const target = manualJourney.anchor === 'logical' ? 'manual-modeling-logical'
      : manualJourney.anchor === 'physical' ? 'manual-modeling-physical'
      : 'manual-modeling-mapping';
    document.getElementById(target)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const reviewComparison = semanticReview && reviewFeedback
    && reviewFeedback.projectId === currentProject?.id
    && reviewFeedback.baseline.logicalModelId === selected
      ? compareLogicalGovernance(reviewFeedback.baseline, semanticReview) : undefined;

  return (
    <div className="modeling-studio-page modeling-studio-logical">
      <ModelingStudioHeader area="logical" title="逻辑建模"
        context={<>
          {journeyProcess ? <span>业务过程：{journeyProcess.name} · {journeyProcess.grain || '粒度待确认'}</span> : <span>当前项目逻辑模型</span>}
          {detail ? <span>草稿：{detail.model.name} · 修订 {detail.model.draftRevision}</span> : null}
        </>}
        actions={<Button onClick={() => history.push(journeyProcess
          ? processToPhysical(journeyProcess.id) : '/modeling')}>返回物理模型</Button>} />
      {journeyProblem && <Alert className="mb-4" showIcon type="error"
        message="无法使用所请求的业务过程上下文" description={journeyProblem}
        action={<Button onClick={() => history.push('/semantic/processes')}>重新选择业务过程</Button>} />}
      {reviewFeedback && reviewFeedback.projectId === currentProject?.id
        && reviewFeedback.baseline.logicalModelId === selected && <Alert className="mb-4" showIcon type="info"
        message={`已定位版本 V${reviewFeedback.baseline.versionNo} 的当前业务对象`}
        description="保存修改后，可重新检查此版本与当前设计的差异。"
        action={<Button disabled={saving || loading || semanticReviewLoading}
          onClick={() => void showSemanticReview(reviewFeedback.baseline.versionNo)}>
          修正后重新核验
        </Button>} />}
      {reviewEntryProblem && <Alert className="mb-4" showIcon type="warning"
        message="当前冻结版本治理核验尚不可完成" description={reviewEntryProblem} />}
      {semanticReviewLoading && reviewRequested && <Alert className="mb-4" showIcon type="info"
        message="正在核验指定逻辑草稿最新冻结版本的业务语义及差异…" />}
      {journeyProcess && <div className="logical-process-links">
        <Button type="link" onClick={() => history.push(processToSemantic(journeyProcess.id))}>返回业务过程</Button>
        <Button type="link" onClick={() => history.push('/modeling/logical')}>全部逻辑模型</Button>
      </div>}
      <Card size="small" className="logical-journey" title="建设进度"
        extra={<Tag color={manualJourney.complete ? 'success' : undefined}>
          {manualJourney.complete ? '设计已发布' : '当前设计'}
        </Tag>}>
        <Steps size="small" responsive current={manualJourney.step}
          items={MANUAL_JOURNEY_LABELS.map(title => ({ title }))} />
        <div className="logical-next-step">
          <Typography.Text>{manualJourney.action}</Typography.Text>
          <Button type="primary" onClick={openManualJourneyAction}
            disabled={loading || saving || mappingPersistBusy || Boolean(journeyProblem)
              || (!detail && models.length === 0 && !can('modeling:create'))}>
            继续建模
          </Button>
        </div>
      </Card>
      <div className="modeling-studio-logical-board grid grid-cols-1 gap-4 lg:grid-cols-[240px_minmax(0,1fr)]">
        <Card className="modeling-studio-logical-selector" id="manual-modeling-pick" title={journeyProcess ? '过程逻辑模型' : '逻辑模型'}
          extra={<Button type="primary" disabled={!can('modeling:create') || Boolean(journeyProblem) || loading || saving}
            onClick={openCreate}>新建模型</Button>}>
          <Input.Search aria-label="搜索逻辑模型" placeholder="搜索名称或编码" allowClear
            value={modelSearch} onChange={event => setModelSearch(event.target.value)} className="mb-3" />
          {models.length === 0 ? <Empty description={journeyProcess
            ? '该过程暂无逻辑模型' : '暂无逻辑模型'} /> : (
            <div className="flex flex-col gap-2">
              {models.filter(model => (model.name + ' ' + model.code).toLowerCase().includes(modelSearch.trim().toLowerCase())).map((model) => (
                <button key={model.id} type="button"
                  className={'modeling-studio-logical-choice' + (model.id === selected ? ' is-active' : '')}
                  aria-pressed={model.id === selected} disabled={saving || loading}
                  onClick={() => void selectModel(model.id)}>
                  <strong>{model.name}</strong><span>{model.code}</span>
                </button>
              ))}
              {!models.some(model => (model.name + ' ' + model.code).toLowerCase().includes(modelSearch.trim().toLowerCase()))
                && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有匹配的逻辑模型">
                  <Button onClick={() => setModelSearch('')}>清空搜索</Button>
                </Empty>}
            </div>
          )}
        </Card>
        <div className="flex min-w-0 flex-col gap-4">
          {!detail ? <Card loading={loading}>{detailProblem ? <Alert type="error" showIcon
            message={`草稿 #${selected} 暂时无法读取`} description={detailProblem}
            action={<Button onClick={() => selected != null && void selectModel(selected)}>重新读取草稿</Button>} />
            : <Empty description={models.length ? '选择逻辑模型开始设计' : '从业务实体开始，定义属性和关系'}>
              {!models.length && <Button type="primary" disabled={!can('modeling:create') || Boolean(journeyProblem)}
                onClick={openCreate}>新建模型</Button>}
            </Empty>}</Card> : (
            <>
              <Card id="manual-modeling-logical" className="logical-design-summary"
                title={<Space wrap>{detail.model.name}<Tag>草稿 · R{detail.model.draftRevision}</Tag></Space>}
                extra={<Space wrap>
                  <Button disabled={saving} onClick={() => void selectModel(detail.model.id)}>刷新</Button>
                  <Button type="primary" disabled={!can('modeling:update') || !entities.length}
                    loading={saving} onClick={() => void freeze()}>保存设计版本</Button>
                </Space>}>
                <Space wrap size={[24, 8]}>
                  <Typography.Text type="secondary">业务过程：{detail.process.name}</Typography.Text>
                  <Typography.Text type="secondary">粒度：{detail.process.grain || '待补充'}</Typography.Text>
                </Space>
                {detail.model.description && <Typography.Paragraph className="!mt-2 !mb-0" type="secondary">
                  {detail.model.description}
                </Typography.Paragraph>}
              </Card>
              <LogicalDesignStudio key={detail.model.id} detail={detail} standards={fields}
                canEdit={can('modeling:update')} busy={saving || loading}
                onEditEntity={editEntity} onEditAttribute={editAttribute} onEditRelation={editRelation} />
              <Card id="manual-modeling-physical" title="设计版本与物理模型">
                <Space wrap className="logical-physical-target">
                  <Typography.Text type="secondary">物理目标</Typography.Text>
                  <Select<number>
                    className="logical-target-select" aria-label="选择物理模型"
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
                    新建物理模型
                  </Button>
                  <Button disabled={physicalModelId == null}
                    onClick={() => history.push(`/modeling/models/${physicalModelId}`)}>
                    打开物理模型
                  </Button>
                </Space>
                <Table size="small" rowKey="id" pagination={false} dataSource={versions}
                  scroll={{ x: 640 }} locale={{ emptyText: '保存设计版本后，可查看差异并关联物理模型' }}
                  columns={[
                    { title: '设计版本', dataIndex: 'versionNo', key: 'versionNo', render: (value: number) => `V${value}` },
                    { title: '状态', dataIndex: 'status', key: 'status', render: () => <Tag>已保存快照</Tag> },
                    { title: '创建人', dataIndex: 'createdBy', key: 'createdBy' },
                    { title: '查看', key: 'action', render: (_, version: LogicalDraftVersion) =>
                      <Space>
                        <Button type="link" onClick={() => void showSnapshot(version.versionNo)}>设计内容</Button>
                        <Button type="link" loading={semanticReviewLoading}
                          onClick={() => void showSemanticReview(version.versionNo)}>
                          检查标准与差异
                        </Button>
                        <Button loading={handoffLoading} type="link"
                          disabled={physicalModelId == null}
                          onClick={() => void previewPhysical(version.versionNo)}>核对字段映射</Button>
                      </Space> },
                  ]} />
              </Card>
            </>
          )}
        </div>
      </div>

      <Modal className="logical-editor-modal" open={createOpen} title="新建逻辑模型" width={640}
        okText="创建模型" okButtonProps={{ loading: saving }}
        onOk={() => void create()} onCancel={() => closeEditor('create')} destroyOnHidden maskClosable={false}>
        {saveProblem && <Alert className="mb-3" showIcon type="error" message={saveProblem} />}
        <Form layout="vertical" form={createForm}>
          <Form.Item label="业务过程" name="processId" rules={[{ required: true }]}>
            <Select showSearch placeholder="选择关联的业务过程" optionFilterProp="label" disabled={Boolean(journeyProcess)}
              options={processes.map(p => ({
              label: `${p.name} · ${p.grain || '粒度待确认'}`, value: p.id,
            }))} />
          </Form.Item>
          {!processes.length && <Typography.Paragraph type="secondary">
            暂无可用业务过程。<Button type="link" size="small"
              onClick={() => closeEditor('create', () => history.push('/semantic/processes'))}>前往创建</Button>
          </Typography.Paragraph>}
          <Row gutter={16}>
          <Col xs={24} sm={12}>
          <Form.Item label="模型编码" name="code" rules={[{ required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_]{0,127}$/, message: '英文字母开头，最多128字符' }]}>
            <Input placeholder="order_domain" />
          </Form.Item>
          </Col><Col xs={24} sm={12}>
          <Form.Item label="模型名称" name="name" rules={[{ required: true }]}><Input placeholder="订单业务逻辑模型" /></Form.Item>
          </Col></Row>
          <Form.Item label="业务说明" name="description"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal className="logical-editor-modal" open={entityOpen} title={editEntityId == null ? '新增业务实体' : '编辑业务实体'} onOk={() => void addEntity()}
        width={640} maskClosable={false}
        okText="保存实体" okButtonProps={{ loading: saving }} onCancel={() => closeEditor('entity')} destroyOnHidden>
        {saveProblem && <Alert className="mb-3" showIcon type="error" message={saveProblem} />}
        <Form layout="vertical" form={entityForm}>
          <Row gutter={16}><Col xs={24} sm={12}>
          <Form.Item name="code" label="实体编码" rules={[{ required: true }]}><Input placeholder="order" disabled={editEntityId != null} /></Form.Item>
          </Col><Col xs={24} sm={12}>
          <Form.Item name="name" label="实体名称" rules={[{ required: true }]}><Input placeholder="Order" /></Form.Item>
          </Col></Row>
          <Form.Item name="businessName" label="业务名称"><Input placeholder="订单" /></Form.Item>
          <Form.Item name="description" label="业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal className="logical-editor-modal" open={attributeEntityId != null} title={editAttributeId == null ? '新增业务属性' : '编辑业务属性'} okText="保存属性" width={680} maskClosable={false}
        okButtonProps={{ loading: saving }} onOk={() => void addAttribute()}
        onCancel={() => closeEditor('attribute')} destroyOnHidden>
        {saveProblem && <Alert className="mb-3" showIcon type="error" message={saveProblem} />}
        <Form layout="vertical" form={attributeForm} initialValues={{ primaryFlag: false, nullable: true }}>
          <Row gutter={16}><Col xs={24} sm={12}>
          <Form.Item label="属性编码" name="code" rules={[{ required: true }]}><Input placeholder="order_id" disabled={editAttributeId != null} /></Form.Item>
          </Col><Col xs={24} sm={12}>
          <Form.Item label="属性名称" name="name" rules={[{ required: true }]}><Input placeholder="订单标识" /></Form.Item>
          </Col></Row>
          <Form.Item label="标准字段" name="stdFieldId">
            <Select allowClear showSearch optionFilterProp="label" loading={fieldsLoading}
              placeholder="选择标准字段" notFoundContent={fieldsLoading ? '加载中' : '暂无可用标准字段'}
              options={fields.map(f => ({ label: `${f.name} · ${f.code}`, value: f.id }))} />
          </Form.Item>
          {fieldsProblem && <Alert type="error" showIcon className="mb-3" message="标准字段读取失败" description={fieldsProblem} />}
          {!fieldsLoading && (!fields.length || fieldsProblem) && detail && <Typography.Paragraph type="secondary">
            可在业务过程中维护标准字段。
            <Button type="link" size="small" onClick={() => closeEditor('attribute',
              () => history.push(processToSemantic(detail.model.processId)))}>前往业务过程</Button>
          </Typography.Paragraph>}
          <Form.Item label="逻辑类型" name="logicalType"><Input placeholder="如 STRING、DECIMAL、DATE" /></Form.Item>
          <Row gutter={16}><Col xs={24} sm={12}>
          <Form.Item label="业务主标识" name="primaryFlag">
            <Select options={[{ label: '否', value: false }, { label: '是', value: true }]} />
          </Form.Item>
          </Col><Col xs={24} sm={12}>
          <Form.Item label="是否可空" name="nullable">
            <Select options={[{ label: '可空', value: true }, { label: '不可空', value: false }]} />
          </Form.Item>
          </Col></Row>
          <Form.Item name="description" label="业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal className="logical-editor-modal" open={relationOpen} title={editRelationId == null ? '新增业务关系' : '编辑业务关系'} width={640} maskClosable={false} okText="保存关系"
        okButtonProps={{ loading: saving }} onOk={() => void addRelation()}
        onCancel={() => closeEditor('relation')} destroyOnHidden>
        {saveProblem && <Alert className="mb-3" showIcon type="error" message={saveProblem} />}
        <Form form={relationForm} layout="vertical">
          <Row gutter={16}><Col xs={24} sm={12}>
          <Form.Item name="sourceEntityId" label="来源实体" rules={[{ required: true }]}>
            <Select disabled={editRelationId != null} options={entities.map(e => ({ label: e.businessName || e.name, value: e.id }))} />
          </Form.Item>
          </Col><Col xs={24} sm={12}>
          <Form.Item name="targetEntityId" label="目标实体" rules={[{ required: true }]}>
            <Select disabled={editRelationId != null} options={entities.map(e => ({ label: e.businessName || e.name, value: e.id }))} />
          </Form.Item>
          </Col></Row>
          <Form.Item name="cardinality" label="关系基数" rules={[{ required: true }]}>
            <Select options={[
              { label: '尚未确认', value: 'UNKNOWN' },
              { label: '一对一', value: 'ONE_TO_ONE' },
              { label: '一对多', value: 'ONE_TO_MANY' },
              { label: '多对一', value: 'MANY_TO_ONE' },
              { label: '多对多', value: 'MANY_TO_MANY' },
            ]} />
          </Form.Item>
          <Form.Item name="description" label="业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>

      {handoffPreview && handoffPreview.logicalModelId === selected
        && handoffPreview.physicalModelId === physicalModelId
        && <Card id="manual-modeling-mapping" title={`逻辑 v${handoffPreview.logicalVersionNo} → 物理 #${handoffPreview.physicalModelId} · 字段映射`}>
        {handoffPreview.blockers.length > 0 && <Alert type="warning" showIcon message="请处理以下映射问题" />}
        <div className="mt-3">
          <Button onClick={() => {
            const target = detail && physicalEvidencePath({
              processId: detail.model.processId,
              logicalId: handoffPreview.logicalModelId,
              logicalVersionNo: handoffPreview.logicalVersionNo,
              physicalId: handoffPreview.physicalModelId,
            });
            if (target) history.push(target);
          }}>打开物理模型</Button>
        </div>
        <details className="mt-3">
          <summary className="cursor-pointer text-xs text-gray-500">版本核对信息</summary>
          <div className="mt-2 flex flex-wrap gap-4">
          <Typography.Text type="secondary">逻辑 V{handoffPreview.logicalVersionNo} · 内容摘要：
            <Typography.Text copyable={{ text: handoffPreview.logicalSnapshotSha256 }}>
              {handoffPreview.logicalSnapshotSha256.slice(0, 16)}…
            </Typography.Text>
          </Typography.Text>
          <Typography.Text type="secondary">物理 {handoffPreview.physicalStatus} · 结构摘要：
            <Typography.Text copyable={{ text: handoffPreview.physicalStructureSha256 }}>
              {handoffPreview.physicalStructureSha256.slice(0, 16)}…
            </Typography.Text>
          </Typography.Text>
          </div>
        </details>
        {handoffPreview.blockers.map((blocker, i) => (
          <Typography.Paragraph key={i} type="danger" className="!mt-2 !mb-0">{blocker}</Typography.Paragraph>
        ))}
        <Table className="mt-3" size="small" scroll={{ x: 1120 }}
          rowKey={(row) => row.physicalColumnId == null
            ? `logical-${row.logicalAttributeId ?? row.logicalAttribute}`
            : `physical-${row.physicalColumnId}`}
          pagination={false}
          dataSource={handoffPreview.columns} columns={[
            { title: '物理字段', dataIndex: 'physicalColumn', render: (v: string | undefined, row) =>
              <span title={`物理列 ID：${row.physicalColumnId ?? '—'}`}>{v || '未覆盖'}</span> },
            { title: '标准字段', dataIndex: 'physicalStdFieldId', render: (id?: number) =>
              id == null ? '未关联' : <span title={`标准字段 ID：${id}`}>{fields.find(field => field.id === id)?.name || `#${id}`}</span> },
            { title: '逻辑实体', dataIndex: 'logicalEntity', render: (v?: string) => v || '待确认' },
            { title: '逻辑属性', dataIndex: 'logicalAttribute', render: (v: string | undefined, row) =>
              <span title={`逻辑属性 ID：${row.logicalAttributeId ?? '—'}`}>{v || '待选择'}</span> },
            { title: '状态', dataIndex: 'result', render: (v: string) => <Tag>{mappingResultLabels[v] || v}</Tag> },
            { title: '原因', dataIndex: 'reason' },
            { title: '规则类型', key: 'ruleKind',
              render: (_, row) => row.physicalColumnId == null ? '—' : (
                <Select<'DIRECT' | 'RENAME'> disabled={mappingApprovalPending} placeholder="请选择规则"
                  className="min-w-[120px]" style={{ width: 130 }}
                  value={mappingKinds[row.physicalColumnId]}
                  options={[{ label: '原样映射', value: 'DIRECT' },
                    { label: '重命名', value: 'RENAME' }]}
                  onChange={value => {
                    setMappingKinds(prev => ({ ...prev, [row.physicalColumnId!]: value }));
                    setMappingFrozen(undefined);
                    setMappingPublished(undefined);
                  }} />
              ) },
            { title: '关联逻辑属性', key: 'selection',
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
            校验映射
          </Button>
          <Typography.Text type="secondary">
            已选 {Object.keys(mappingSelections).length} 项。选择映射规则后，校验并保存。
          </Typography.Text>
        </Space>
        {handoffPreview.physicalLayer === 'DWD' && <>
          {mappingPersistError && <Alert className="mt-3" type="warning" showIcon
            message="映射暂时无法保存" description={mappingPersistError} />}
          <Space className="mt-3" wrap>
            <Button loading={mappingPersistBusy}
              disabled={!canSaveMapping || mappingApprovalPending || !!mappingPersistError || !reviewResult?.readyForManualDesign
                || reviewSelectionSnapshot !== JSON.stringify(mappingSelections)
                || Object.keys(mappingSelections).some(k => !mappingKinds[Number(k)])}
              onClick={() => void saveMappingEvidence()}>保存映射草稿</Button>
            <Button loading={mappingPersistBusy}
              disabled={!canSaveMapping || mappingApprovalPending || !mappingDraft || !mappingMatchesEditor
                || !reviewResult?.readyForManualDesign
                || mappingDraft.logicalSnapshotSha256 !== handoffPreview.logicalSnapshotSha256
                || mappingDraft.physicalStructureSha256 !== handoffPreview.physicalStructureSha256}
              onClick={() => void freezeMappingEvidence()}>保存映射版本</Button>
          </Space>
          {mappingDraft && <>
            <Alert className="mt-3" showIcon type="info"
              message={`映射草稿已保存 · R${mappingDraft.draftRevision}`}
              description="可继续校验并保存映射版本。" />
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
              message.info('已载入保存的映射，请重新校验');
            }}>载入已保存映射</Button>
          </>}
          {mappingFrozen && <>
            <Alert className="mt-3" showIcon type="success"
              message={`映射版本 V${mappingFrozen.revisionNo} 已保存`}
              description="映射版本不可修改。完成标准核对后，可发布物理设计。" />
            {physicalModelId != null && selected != null && detail
              && <DwdStandardReviewCard
                physicalModelId={physicalModelId}
                physicalStructureSha256={handoffPreview.physicalStructureSha256}
                processId={detail.model.processId}
                logicalModelId={selected}
                logicalVersionNo={handoffPreview.logicalVersionNo} />}

            <Button className="mt-2" loading={mappingPersistBusy}
              disabled={!canSaveMapping || !!mappingPersistError || !mappingMatchesEditor
                || !mappingDraft || mappingFrozen.mappingSetId !== mappingDraft.mappingSetId
                || mappingFrozen.draftRevision !== mappingDraft.draftRevision
                || handoffPreview.physicalStatus !== 'DRAFT' || !!mappingPublished
                || mappingApprovalPending}
              onClick={publishMappingDesign}>发布 DWD 设计版本</Button>
            {mappingApprovalPending && <Alert showIcon className="mt-2" type="info"
              message="DWD 设计发布审批进行中"
              description="可前往审批中心查看进度。" />}
          </>}
          {mappingPublished && <Alert className="mt-3" showIcon type="success"
            message={`DWD 物理设计 V${mappingPublished.versionNo} 已发布 · Version #${mappingPublished.modelVersionId}`}
            description={`已关联映射版本 #${mappingPublished.mappingRevisionId}。可打开物理模型继续后续开发。`} />}
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
                ? '字段映射校验通过'
                : '以下映射需要调整'}
              description={`已核对 ${reviewResult.reviewedMappings.length} 项字段关联。`} />
            {reviewResult.blockers.map((blocker, idx) => (
              <Typography.Paragraph key={idx} type="danger" className="!mt-2 !mb-0">
                {blocker}
              </Typography.Paragraph>
            ))}
          </div>
        )}
      </Card>}
      <LogicalSemanticReviewModal review={semanticReview} comparison={reviewComparison}
        loading={semanticReviewLoading || repairLoading} canEdit={can('modeling:update')}
        onClose={() => { repairRequest.current += 1; setRepairLoading(false); setSemanticReview(undefined); }}
        onReload={showSemanticReview} onRepair={openCurrentReviewTarget} />
      <Modal className="logical-editor-modal" width={760} title="设计版本内容" open={snapshotOpen}
        footer={<Button onClick={() => setSnapshotOpen(false)}>关闭</Button>}
        onCancel={() => setSnapshotOpen(false)}>
        <pre style={{ maxHeight: 420, overflow: 'auto', whiteSpace: 'pre-wrap' }}>{snapshotText}</pre>
      </Modal>
    </div>
  );
}
