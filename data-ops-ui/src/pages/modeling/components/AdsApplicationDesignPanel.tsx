import {
  Alert, Button, Card, Descriptions, Divider, Empty, Input, InputNumber,
  Modal, Select, Space, Spin, Steps, Tag, Typography, message,
} from 'antd';
import { ReloadOutlined, HistoryOutlined } from '@ant-design/icons';
import AdsHandoffEvidencePanel from './AdsHandoffEvidencePanel';
import { useEffect, useMemo, useState } from 'react';
import { listActiveMetricPublicationReferences } from '@/services/metric/api';
import type { PublishedMetricReference } from '@/services/metric/types';
import {
  getModelingModel, getModelingVersion, listModelingVersions, pageModelingModels,
} from '@/services/modeling/api';
import type {
  ModelingModelRecord, ModelingStructureRecord,
} from '@/services/modeling/types';
import {
  getAdsApplicationContract,
  getAdsApplicationContractHistory,
  saveAdsApplicationContract,
  validateAdsApplicationContract,
} from '@/services/modeling/adsApplication';
import type {
  AdsApplicationContractHistoryEntry, AdsApplicationContractRequest,
  AdsApplicationContractSaved, AdsApplicationContractValidation,
  AdsConsumerType, AdsSourceMode,
} from '@/services/modeling/adsApplication';
import {
  adsContractSignature, adsSourceDrift, changeAdsSourceMode,
  draftFromAdsSaved, newAdsApplicationDraft,
} from '../adsApplicationState';

const STEPS = [
  { title: '应用目标', description: '谁使用、做什么' },
  { title: '发布来源', description: '版本与粒度' },
  { title: '输出设计', description: '字段与业务键' },
  { title: '业务口径', description: '过滤、指标、刷新' },
  { title: '验证保存', description: '静态合同' },
  { title: '历史交接', description: '修订与消费状态' },
];

interface Props {
  modelId: number;
  structure: ModelingStructureRecord;
  structureDirty: boolean;
  modelName?: string;
  modelCode?: string;
  processId?: number;
  canEdit: boolean;
}

interface Validated {
  signature: string;
  result: AdsApplicationContractValidation;
}

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

export default function AdsApplicationDesignPanel({
  modelId, structure, structureDirty, modelName, modelCode, processId, canEdit,
}: Props) {
  const [draft, setDraft] = useState<AdsApplicationContractRequest>(
    () => newAdsApplicationDraft(modelCode, modelName),
  );
  const [baseline, setBaseline] = useState('');
  const [saved, setSaved] = useState<AdsApplicationContractSaved | null>(null);
  const [history, setHistory] = useState<AdsApplicationContractHistoryEntry[]>([]);
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingError, setLoadingError] = useState('');
  const [refreshIndex, setRefreshIndex] = useState(0);
  const [candidates, setCandidates] = useState<ModelingModelRecord[]>([]);
  const [candidateError, setCandidateError] = useState('');
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [metricReferences, setMetricReferences] = useState<PublishedMetricReference[]>([]);
  const [metricReferencesStatus, setMetricReferencesStatus] =
    useState<'NOT_LOADED' | 'LOADING' | 'READY' | 'UNAVAILABLE'>('NOT_LOADED');
  const [source, setSource] = useState<ModelingModelRecord | null>(null);
  const [sourceLoaded, setSourceLoaded] = useState(false);
  const [sourceError, setSourceError] = useState('');
  const [sourceStructure, setSourceStructure] = useState<ModelingStructureRecord | null>(null);
  const [choosingSource, setChoosingSource] = useState(false);
  const [validating, setValidating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [validated, setValidated] = useState<Validated | null>(null);
  const [targetStructureStatus, setTargetStructureStatus] =
    useState<'UNKNOWN' | 'CURRENT' | 'STALE' | 'NOT_PINNED'>('UNKNOWN');

  const signature = useMemo(() => JSON.stringify({
    draft, structureDefinition: structure.definition, structureDirty,
  }), [draft, structure.definition, structureDirty]);
  const changed = baseline !== adsContractSignature(draft);
  const sourceStatus = adsSourceDrift(
    draft, sourceLoaded ? source?.publishedVersionId ?? null : undefined,
  );
  useEffect(() => {
    if (!changed || loading) return;
    const guard = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', guard);
    return () => window.removeEventListener('beforeunload', guard);
  }, [changed, loading]);
  const validationState = validated
    ? validated.signature === signature
      ? (validated.result.valid ? 'STATIC_PASS' : 'STATIC_BLOCKED')
      : 'STALE'
    : 'NOT_RUN';
  const targetFields = (structure.columns ?? []).filter((field) => Boolean(field.columnName));
  const targetOptions = targetFields.map((field) => ({
    value: String(field.columnName),
    label: String(field.columnName) + ' · ' + String(field.dataType ?? '字段'),
  }));
  const metricReferenceOptions = useMemo(() => {
    const options: { value: number | string; label: string; disabled?: boolean }[] =
      metricReferences.map((reference) => ({
        value: reference.publicationEventId,
        label: `${reference.metricCode} · ${reference.metricName} · V${reference.metricVersion} · ${reference.snapshotDigest.slice(0, 8)}`,
      }));
    const pin = draft.metricPublicationPin;
    if (pin && !metricReferences.some(
      (reference) => reference.publicationEventId === pin.publicationEventId,
    )) {
      const stateLabel = metricReferencesStatus === 'READY'
        ? '非当前有效发布'
        : '状态待核验';
      options.unshift({
        value: pin.publicationEventId,
        label: `已保存引用 · Metric #${pin.metricId} · V${pin.metricVersion} · ${pin.snapshotDigest.slice(0, 8)}（${stateLabel}）`,
        disabled: true,
      });
    } else if (!pin && draft.metricVersionId != null) {
      options.unshift({
        value: `legacy:${draft.metricVersionId}`,
        label: `旧合同引用 · Metric 版本 ID ${draft.metricVersionId}（身份不足，需重选或清除）`,
        disabled: true,
      });
    }
    return options;
  }, [draft.metricPublicationPin, draft.metricVersionId, metricReferences, metricReferencesStatus]);

  const sourceOptions = (sourceStructure?.columns ?? [])
    .filter((field) => Boolean(field.columnName))
    .map((field) => ({
      value: String(field.columnName),
      label: String(field.columnName) + ' · ' + String(field.dataType ?? '字段'),
    }));
  const keyOptions = draft.outputFields
    .filter((field) => Boolean(field.outputColumn))
    .map((field) => ({ value: field.outputColumn, label: field.outputColumn }));
  const standardMissing = draft.outputFields.filter((field) => {
    const column = targetFields.find((item) => item.columnName === field.outputColumn);
    return Boolean(field.outputColumn) && !column?.stdFieldId;
  });

  const apply = (patch: Partial<AdsApplicationContractRequest>) => {
    setDraft((previous) => ({ ...previous, ...patch }));
  };

  // A project/model change remounts this component via its keyed parent.
  // Failed API reads never become a fake empty, authoritative contract.
  useEffect(() => {
    let active = true;
    setLoading(true);
    setLoadingError('');
    Promise.all([
      getAdsApplicationContract(modelId),
      getAdsApplicationContractHistory(modelId),
    ]).then(([current, revisions]) => {
      if (!active) return;
      const next = current
        ? draftFromAdsSaved(current)
        : newAdsApplicationDraft(modelCode, modelName);
      setSaved(current);
      setHistory(revisions ?? []);
      setDraft(next);
      setBaseline(adsContractSignature(next));
      setValidated(null);
      setStep(current ? 4 : 0);
    }).catch((error) => {
      if (active) setLoadingError(errorText(
        error, '无法读取应用输出合同，请核对项目权限或后端服务状态',
      ));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [modelId, modelCode, modelName, refreshIndex]);

  // Load only minimal, current-project publication identities when the user opens the
  // Metric reference step. A failed read is not represented as an empty authoritative list.
  useEffect(() => {
    if (step !== 3) return undefined;
    let active = true;
    setMetricReferencesStatus('LOADING');
    listActiveMetricPublicationReferences().then((references) => {
      if (!active) return;
      setMetricReferences(references);
      setMetricReferencesStatus('READY');
    }).catch(() => {
      if (!active) return;
      setMetricReferences([]);
      setMetricReferencesStatus('UNAVAILABLE');
    });
    return () => { active = false; };
  }, [step]);

  // Compare the saved contract's immutable target-structure pin with the current
  // server structure; the browser's editable structure snapshot is not authoritative.
  useEffect(() => {
    if (loading) return;
    if (!saved) {
      setTargetStructureStatus('NOT_PINNED');
      return;
    }
    let active = true;
    setTargetStructureStatus('UNKNOWN');
    validateAdsApplicationContract(modelId, draftFromAdsSaved(saved))
      .then((result) => {
        if (!active) return;
        const pinned = saved.contract.targetStructureFingerprint;
        setTargetStructureStatus(
          pinned && pinned === result.targetStructureFingerprint ? 'CURRENT' : 'STALE',
        );
      })
      .catch(() => {
        if (active) setTargetStructureStatus('UNKNOWN');
      });
    return () => { active = false; };
  }, [modelId, saved, structure.definition, structureDirty, loading, refreshIndex]);

  // Only current-project, same-process, already-published sources are offered.
  useEffect(() => {
    let active = true;
    if (!processId) {
      setCandidates([]);
      setCandidateError('当前 ADS 未绑定业务过程');
      return () => { active = false; };
    }
    setCandidateLoading(true);
    setCandidateError('');
    const run = async () => {
      const results: ModelingModelRecord[] = [];
      // Page through the model directory instead of silently truncating to page one.
      for (let pageNo = 1; pageNo <= 20; pageNo += 1) {
        const result = await pageModelingModels({
          pageNo, pageSize: 50, processId, layerCode: draft.sourceMode,
        });
        const batch = result?.bizData ?? [];
        results.push(...batch);
        if (batch.length < 50) break;
        if (pageNo === 20) throw new Error('来源候选过多，请收窄业务过程后重试');
      }
      if (!active) return;
      setCandidates(results.filter((item) =>
        item.id !== modelId
          && item.processId === processId
          && item.layerCode?.toUpperCase() === draft.sourceMode
          && Boolean(item.publishedVersionId),
      ));
    };
    void run().catch((error) => {
      if (active) {
        setCandidates([]);
        setCandidateError(errorText(error, '无法读取正式发布的上游模型'));
      }
    }).finally(() => {
      if (active) setCandidateLoading(false);
    });
    return () => { active = false; };
  }, [modelId, processId, draft.sourceMode]);

  // Read both the live publish pointer and the *pinned* snapshot.
  // Never substitute a live draft for the selected immutable model version.
  useEffect(() => {
    let active = true;
    setSource(null);
    setSourceLoaded(false);
    setSourceError('');
    setSourceStructure(null);
    if (!draft.upstreamModelId || !draft.upstreamVersionNo) {
      return () => { active = false; };
    }
    Promise.all([
      getModelingModel(draft.upstreamModelId),
      getModelingVersion(draft.upstreamModelId, draft.upstreamVersionNo),
    ]).then(([live, immutable]) => {
      if (!active) return;
      setSource(live);
      setSourceLoaded(true);
      setSourceStructure(immutable.structure);
      if (live.processId !== processId || live.layerCode !== draft.sourceMode) {
        setSourceError('来源与当前业务过程或分层不一致，不能保存');
      }
      if (immutable.version.id !== draft.upstreamVersionId
        || immutable.version.checksum !== draft.upstreamChecksum) {
        setSourceError('上游冻结版本 ID / 校验和不一致，请重新确认');
      }
    }).catch((error) => {
      if (active) setSourceError(errorText(error, '无法核验上游发布版本'));
    });
    return () => { active = false; };
  }, [
    modelId, processId, draft.upstreamModelId, draft.upstreamVersionId,
    draft.upstreamVersionNo, draft.upstreamChecksum, draft.sourceMode,
    refreshIndex,
  ]);

  const chooseSource = async (upstreamId: number) => {
    setChoosingSource(true);
    const mode = draft.sourceMode;
    try {
      const live = await getModelingModel(upstreamId);
      if (!live.publishedVersionId || live.processId !== processId
        || live.layerCode !== mode) {
        throw new Error('来源未正式发布，或不属于当前业务过程/分层');
      }
      const revisions = await listModelingVersions(upstreamId);
      const published = revisions.find((item) => item.id === live.publishedVersionId);
      if (!published) throw new Error('无法核实上游当前发布版本及校验和');
      setDraft((previous) => previous.sourceMode !== mode ? previous : ({
        ...previous,
        upstreamModelId: upstreamId,
        upstreamVersionId: published.id,
        upstreamVersionNo: published.versionNo,
        upstreamChecksum: published.checksum,
        outputFields: [],
        businessKeys: [],
        filterExpression: '',
        sortExpression: '',
        metricVersionId: null,
      }));
      setValidated(null);
      message.info('来源版本已明确冻结，请重新设计输出字段和业务键');
    } catch (error) {
      message.error(errorText(error, '选取来源失败'));
    } finally {
      setChoosingSource(false);
    }
  };

  const switchMode = (mode: AdsSourceMode) => {
    if (mode === draft.sourceMode) return;
    Modal.confirm({
      title: '确认切换 ADS 来源层级？',
      content: '切换会清空本次编辑中的来源版本、字段映射、业务键、固定过滤及指标引用；已保存的修订历史不受影响。DWD 明细不能沿用 DWS 汇总粒度。',
      okText: '确认切换并重做映射',
      cancelText: '取消',
      onOk: () => {
        setDraft((previous) => changeAdsSourceMode(previous, mode));
        setValidated(null);
      },
    });
  };

  const verify = async (): Promise<boolean> => {
    if (standardMissing.length) {
      message.warning('ADS 物理字段未绑定标准字段，请先在表结构中完成标准治理');
      return false;
    }
    if (!canEdit || structureDirty || sourceStatus !== 'CURRENT' || sourceError) {
      message.warning('请先保存物理结构，并核验当前正式发布来源与冻结版本');
      return false;
    }
    setValidating(true);
    try {
      const result = await validateAdsApplicationContract(modelId, draft);
      setValidated({ signature, result });
      const targetPinChanged = Boolean(saved
        && saved.contract.targetStructureFingerprint !== result.targetStructureFingerprint);
      if (saved) {
        setTargetStructureStatus(targetPinChanged ? 'STALE' : 'CURRENT');
      }
      if (result.valid && targetPinChanged) {
        message.warning('静态校验通过，但 ADS 物理结构指纹已变化；请核对输出字段与业务键后再保存新修订');
      } else if (result.valid) {
        message.success('服务端静态合同校验通过；尚未执行真实查询或消费授权');
      } else {
        message.warning('静态合同未通过，请检查具体阻断项');
      }
      return result.valid;
    } catch (error) {
      setValidated(null);
      message.error(errorText(error, '服务端静态校验失败'));
      return false;
    } finally {
      setValidating(false);
    }
  };

  const persist = async () => {
    if (saving || validating || !canEdit) return;
    const previouslyReviewed = Boolean(validated?.signature === signature && validated.result.valid);
    const reviewedFingerprint = previouslyReviewed ? validated?.result.targetStructureFingerprint : null;
    setSaving(true);
    try {
      if (saved && targetStructureStatus === 'UNKNOWN') {
        throw new Error('无法确认当前 ADS 物理结构指纹，请重新读取或运行静态校验后再保存');
      }
      if (saved && targetStructureStatus === 'STALE' && validationState !== 'STATIC_PASS') {
        throw new Error('ADS 物理结构指纹已变化；请先核对输出字段与业务键，再运行静态校验');
      }
      if (standardMissing.length) {
        throw new Error('ADS 输出列缺少正式标准字段绑定，请先完善模型物理结构');
      }
      if (structureDirty || sourceStatus !== 'CURRENT' || sourceError) {
        throw new Error('物理结构有未保存更改，或来源冻结版本不再是当前发布版本');
      }
      // C1 locks the ADS model and atomically verifies this revision on save.
      // Re-read here as an early user-friendly check, not as the only guard.
      const current = await getAdsApplicationContract(modelId);
      if (current?.revision !== saved?.revision) {
        throw new Error('另一操作已更新 ADS 合同，请核对远端新版本后再编辑，禁止覆盖');
      }
      const check = await validateAdsApplicationContract(modelId, draft);
      setValidated({ signature, result: check });
      const targetPinChanged = Boolean(saved
        && saved.contract.targetStructureFingerprint !== check.targetStructureFingerprint);
      if (targetPinChanged) setTargetStructureStatus('STALE');
      if (!check.valid) {
        message.warning('存在服务端阻断项，不能保存应用合同');
        setStep(4);
        return;
      }
      const expectedTargetFingerprint = saved
        ? (reviewedFingerprint ?? saved.contract.targetStructureFingerprint)
        : (reviewedFingerprint ?? check.targetStructureFingerprint);
      if (expectedTargetFingerprint !== check.targetStructureFingerprint) {
        setTargetStructureStatus('STALE');
        setValidated(null);
        setStep(2);
        message.warning('服务端检测到 ADS 物理结构指纹变化；已停止保存，请核对输出字段/标准/业务键并重新静态校验');
        return;
      }
      const result = await saveAdsApplicationContract(modelId, {
        ...draft,
        expectedRevision: saved?.revision ?? 0,
        expectedTargetStructureFingerprint: expectedTargetFingerprint,
      });
      let readBack: AdsApplicationContractSaved | null;
      try {
        readBack = await getAdsApplicationContract(modelId);
      } catch {
        throw new Error('保存接口已返回，但合同回读失败；请重新读取确认状态，不要重复盲写');
      }
      if (!readBack || readBack.revision !== result.revision
        || readBack.contractSha256 !== result.contractSha256) {
        throw new Error('保存接口已返回，但合同回读证据不一致；请重新读取确认状态，不要重复盲写');
      }
      // The current-contract readback is the save receipt. History is secondary:
      // if its query fails, keep the confirmed revision visible and allow retry.
      let revisions: AdsApplicationContractHistoryEntry[] | null = null;
      try {
        revisions = await getAdsApplicationContractHistory(modelId);
      } catch {
        revisions = null;
      }
      setSaved(readBack);
      setHistory(revisions ?? [
        readBack,
        ...history.filter((entry) => entry.revision !== readBack.revision),
      ].slice(0, 50));
      const next = draftFromAdsSaved(readBack);
      setDraft(next);
      setBaseline(adsContractSignature(next));
      setValidated(null);
      setStep(5);
      if (revisions) {
        message.success('合同已保存并回读，修订版本 ' + readBack.revision + '；数据消费仍未配置');
      } else {
        message.warning('合同已保存并回读，修订版本 ' + readBack.revision
          + '；但历史列表刷新失败，请稍后重新读取历史');
      }
    } catch (error) {
      message.error(errorText(error, '保存失败；请检查后端回执，勿假定保存成功'));
    } finally {
      setSaving(false);
    }
  };

  const reload = () => {
    const perform = () => setRefreshIndex((previous) => previous + 1);
    if (changed) {
      Modal.confirm({
        title: '放弃当前未保存的 ADS 合同编辑？',
        content: '重新读取会用服务端合同替换当前字段与来源选择。',
        okText: '放弃并重新读取',
        cancelText: '保留编辑',
        onOk: perform,
      });
    } else {
      perform();
    }
  };

  const reuseRevision = (entry: AdsApplicationContractHistoryEntry) => {
    Modal.confirm({
      title: '以历史合同为基础创建新草稿？',
      content: '该操作不会更改历史修订，也不会直接恢复旧版或覆盖当前合同。请核对过期来源后再通过服务端校验并保存新修订。',
      okText: '载入到当前草稿',
      cancelText: '取消',
      onOk: () => {
        setDraft(draftFromAdsSaved(entry));
        setValidated(null);
        setStep(1);
      },
    });
  };

  if (loading) return <Card bordered={false}><Spin tip="读取 ADS 应用合同与修订历史"><div className="h-24" /></Spin></Card>;
  if (loadingError) return (
    <Card bordered={false} title="ADS 应用输出工作台">
      <Alert type="error" showIcon message="应用合同读取失败" description={loadingError}
        action={<Button onClick={reload}>重试</Button>} />
    </Card>
  );

  return (
    <Card
      bordered={false}
      title="ADS 应用输出工作台"
      extra={<Space wrap>
        {saved ? <Tag>合同修订 R{saved.revision}</Tag> : <Tag>尚无合同</Tag>}
        {changed ? <Tag color="warning">未保存编辑</Tag> : null}
        <Button icon={<ReloadOutlined />} onClick={reload}>重新读取</Button>
      </Space>}
    >
      <Alert className="mb-4" type="info" showIcon
        message="ADS 是面向消费方的输出设计合同"
        description="保存合同不生成数据、执行任务、指标发布或 Dataset/API 授权；所有真实运行和消费证据必须来自各自原域。" />
      <Steps current={step} onChange={setStep} size="small" responsive
        items={STEPS} className="mb-6" />
      {structureDirty && <Alert className="mb-4" showIcon type="warning"
        message="当前物理结构有未保存更改"
        description="ADS 输出校验依赖已保存的物理字段，请先保存表结构，再验证或保存合同。" />}
      {saved && targetStructureStatus === 'STALE' && <Alert className="mb-4" showIcon type="warning"
        message="ADS 物理结构指纹已变化"
        description="合同冻结时的目标结构与当前服务端结构不同。请到“输出设计”核对字段、标准和业务键，再运行服务端静态校验；未重新校验前不能保存。" />}
      {saved && targetStructureStatus === 'UNKNOWN' && <Alert className="mb-4" showIcon type="warning"
        message="无法确认当前 ADS 物理结构指纹"
        description="保存已暂停，等待服务端校验返回。请重试读取或运行静态校验，不会把未知状态视为结构一致。" />}
      {sourceStatus === 'STALE' && <Alert className="mb-4" showIcon type="error"
        message="上游发布版本发生漂移"
        description="合同仍绑定原精确版本。不会自动升级到 latest；必须重新选择当前正式发布版本并审阅字段/业务键。" />}
      {sourceStatus === 'UNKNOWN' && draft.upstreamModelId > 0 && <Alert className="mb-4"
        showIcon type="warning" message="正在核验来源版本或无法读取当前发布指针"
        description={sourceError || '来源不明时禁止保存合同。'} />}
      {sourceError && sourceStatus !== 'UNKNOWN' && <Alert className="mb-4"
        showIcon type="error" message={sourceError} />}
      {step === 0 && <div className="space-y-4">
        <Typography.Title level={5}>01 · 明确应用目标与消费方</Typography.Title>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <label>应用编码
            <Input className="mt-1" value={draft.appCode} disabled={!canEdit || saving}
              maxLength={64} onChange={(event) => apply({ appCode: event.target.value })}
              placeholder="如 order_overview" />
          </label>
          <label>应用名称
            <Input className="mt-1" value={draft.appName} disabled={!canEdit || saving}
              onChange={(event) => apply({ appName: event.target.value })}
              placeholder="订单经营日报" />
          </label>
          <label>业务负责人
            <Input className="mt-1" value={draft.owner} disabled={!canEdit || saving}
              onChange={(event) => apply({ owner: event.target.value })}
              placeholder="明确实际业务负责人" />
          </label>
          <label>消费类型
            <Select className="mt-1 w-full" disabled={!canEdit || saving}
              value={draft.consumerType}
              onChange={(value: AdsConsumerType) => apply({ consumerType: value })}
              options={[
                { label: '报表 / BI', value: 'REPORT' },
                { label: 'Dataset', value: 'DATASET' },
                { label: 'API', value: 'API' },
                { label: '分析用途', value: 'ANALYSIS' },
              ]} />
          </label>
        </div>
        <label className="block">业务目的
          <Input.TextArea className="mt-1" rows={3} disabled={!canEdit || saving}
            value={draft.purpose} onChange={(event) => apply({ purpose: event.target.value })}
            placeholder="消费者为什么需要该应用数据？" />
        </label>
      </div>}
      {step === 1 && <div className="space-y-4">
        <Typography.Title level={5}>02 · 选择可信、已发布的精确来源</Typography.Title>
        <label className="block">来源粒度（DWD 仅用于明确的明细需求）
          <Select className="mt-1 w-full" value={draft.sourceMode} disabled={!canEdit || saving}
            onChange={switchMode}
            options={[
              { label: 'DWS · 已汇总（默认）', value: 'DWS' },
              { label: 'DWD · 明细（显式例外）', value: 'DWD' },
            ]} />
        </label>
        {draft.sourceMode === 'DWD' && <Alert showIcon type="warning"
          message="当前为 DWD 明细来源"
          description="必须重新确认每行实际粒度、明细业务键与输出字段，不能沿用 DWS 的汇总指标口径。" />}
        <label className="block">已发布的同业务过程上游模型
          <Select className="mt-1 w-full" showSearch optionFilterProp="label"
            disabled={!canEdit || saving || candidateLoading || choosingSource}
            loading={candidateLoading || choosingSource}
            value={draft.upstreamModelId || undefined}
            placeholder="请选择上游正式发布模型"
            options={candidates.map((item) => ({
              label: (item.name ?? item.code ?? String(item.id)) + ' · ' + (item.code ?? '') + ' #' + item.id,
              value: Number(item.id),
            })).concat(
              draft.upstreamModelId > 0
                && !candidates.some((item) => item.id === draft.upstreamModelId)
                ? [{ label: '原冻结来源 #' + draft.upstreamModelId + '（已不可选，需核验）',
                  value: draft.upstreamModelId }]
                : [],
            )}
            onChange={(value: number) => { void chooseSource(value); }} />
        </label>
        {candidateError && <Alert type="error" showIcon message={candidateError} />}
        {draft.upstreamModelId > 0 && <Descriptions size="small" bordered column={1}>
          <Descriptions.Item label="来源模型 ID">{draft.upstreamModelId}</Descriptions.Item>
          <Descriptions.Item label="精确发布版本">V{draft.upstreamVersionNo} / ID {draft.upstreamVersionId}</Descriptions.Item>
          <Descriptions.Item label="冻结校验和">
            <Typography.Text copyable style={{ wordBreak: 'break-all' }}>
              {draft.upstreamChecksum || '未绑定'}
            </Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label="当前指针核验">
            <Tag color={sourceStatus === 'CURRENT' && !sourceError ? 'success' : 'error'}>
              {sourceStatus === 'CURRENT' && !sourceError ? '一致' : sourceStatus}
            </Tag>
            {sourceStatus === 'STALE' && canEdit && <Button className="ml-2"
              size="small" disabled={saving} loading={choosingSource}
              onClick={() => void chooseSource(draft.upstreamModelId)}>
              人工确认并改用最新发布版本
            </Button>}
          </Descriptions.Item>
        </Descriptions>}
        <Typography.Text type="secondary">
          切换来源或发布版本后，原有输出映射、业务键、过滤与指标引用会清空，必须重新核对。
        </Typography.Text>
      </div>}
      {step === 2 && <div className="space-y-4">
        <Typography.Title level={5}>03 · 定义输出字段和业务键</Typography.Title>
        {!sourceStructure && <Alert showIcon type="warning"
          message="请先选择可核验的已发布来源，才能设计字段映射" />}
        {draft.outputFields.map((field, index) => <div
          key={index} className="rounded-lg border border-[#eaecf0] p-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_1fr_1fr_auto]">
            <label>上游发布字段
              <Select className="mt-1 w-full" showSearch optionFilterProp="label"
                value={field.sourceColumn || undefined} disabled={!canEdit || saving || !sourceStructure}
                options={sourceOptions}
                onChange={(value: string) => setDraft((previous) => ({
                  ...previous, outputFields: previous.outputFields.map((item, row) =>
                    row === index ? { ...item, sourceColumn: value } : item),
                }))} />
            </label>
            <label>ADS 目标物理字段
              <Select className="mt-1 w-full" showSearch optionFilterProp="label"
                value={field.outputColumn || undefined} disabled={!canEdit || saving}
                options={targetOptions}
                onChange={(value: string) => setDraft((previous) => ({
                  ...previous, outputFields: previous.outputFields.map((item, row) =>
                    row === index ? {
                      ...item, outputColumn: value,
                      standardFieldId: targetFields.find((column) => column.columnName === value)?.stdFieldId ?? null,
                    } : item),
                  businessKeys: previous.businessKeys.filter((key) => key !== field.outputColumn),
                }))} />
            </label>
            <label>敏感等级（标识非授权）
              <Select className="mt-1 w-full" value={field.sensitivity || undefined}
                disabled={!canEdit || saving} allowClear
                options={['LOW', 'MEDIUM', 'HIGH', 'RESTRICTED'].map((value) => ({ value }))}
                onChange={(value?: string) => setDraft((previous) => ({
                  ...previous, outputFields: previous.outputFields.map((item, row) =>
                    row === index ? { ...item, sensitivity: value ?? null } : item),
                }))} />
            </label>
            <Button className="self-end" danger disabled={!canEdit || saving}
              onClick={() => setDraft((previous) => ({
                ...previous,
                outputFields: previous.outputFields.filter((_, row) => row !== index),
                businessKeys: previous.businessKeys.filter((key) => key !== field.outputColumn),
              }))}>移除</Button>
          </div>
          <Typography.Text type="secondary" className="!text-[12px]">
            标准字段必须由现有标准库和物理结构依法绑定；本表不创建新标准，也不生成消费授权。
          </Typography.Text>
        </div>)}
        <Button disabled={!canEdit || saving || !sourceStructure || targetOptions.length === 0}
          onClick={() => apply({ outputFields: [
            ...draft.outputFields, { sourceColumn: '', outputColumn: '', standardFieldId: null, sensitivity: null },
          ] })}>添加输出字段</Button>
        <label className="block">输出业务键（至少一列；应反映交付数据的唯一粒度）
          <Select className="mt-1 w-full" mode="multiple" disabled={!canEdit || saving}
            options={keyOptions} value={draft.businessKeys}
            onChange={(values: string[]) => apply({ businessKeys: values })}
            placeholder="选择一个或多个输出列" />
        </label>
        {standardMissing.length > 0 && <Alert type="error" showIcon
          message="ADS 输出列缺少标准字段绑定"
          description={'请先在物理结构中为以下非 ODS 列绑定标准字段，再保存合同：'
            + standardMissing.map((field) => field.outputColumn).join('、')} />}
        <Typography.Text type="secondary">当前 ADS 物理表定义 {targetOptions.length} 列；输出仅允许映射到已存在的物理列。</Typography.Text>
      </div>}
      {step === 3 && <div className="space-y-4">
        <Typography.Title level={5}>04 · 消费口径、固定过滤和刷新预期</Typography.Title>
        <label className="block">固定过滤条件（仅支持已映射输出列的简单比较及 AND）
          <Input.TextArea className="mt-1" rows={2} value={draft.filterExpression}
            disabled={!canEdit || saving} maxLength={512}
            onChange={(event) => apply({ filterExpression: event.target.value })}
            placeholder="如 city_id = '310000'" />
        </label>
        <label className="block">排序规则（仅输出列 ASC / DESC）
          <Input className="mt-1" disabled={!canEdit || saving} maxLength={512}
            value={draft.sortExpression}
            onChange={(event) => apply({ sortExpression: event.target.value })}
            placeholder="如 order_day DESC, city_id ASC" />
        </label>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <label className="block">Metric 发布引用（固定不可变版本）
            <Select
              className="mt-1 w-full"
              disabled={!canEdit || saving || metricReferencesStatus === 'LOADING'}
              loading={metricReferencesStatus === 'LOADING'}
              allowClear
              showSearch
              optionFilterProp="label"
              value={draft.metricPublicationPin?.publicationEventId
                ?? (draft.metricVersionId != null ? `legacy:${draft.metricVersionId}` : undefined)}
              options={metricReferenceOptions}
              placeholder="不引用 Metric"
              notFoundContent={metricReferencesStatus === 'LOADING'
                ? '正在读取已发布指标…'
                : metricReferencesStatus === 'UNAVAILABLE'
                  ? '读取失败；不能把失败当作空列表'
                  : '当前项目暂无可供新合同引用的已发布指标'}
              onChange={(value: number | string | undefined) => {
                if (typeof value === 'string' && value.startsWith('legacy:')) return;
                const selected = typeof value === 'number'
                  ? metricReferences.find((reference) => reference.publicationEventId === value)
                  : undefined;
                apply({
                  metricVersionId: selected?.metricVersionId ?? null,
                  metricPublicationPin: selected ? {
                    metricId: selected.metricId,
                    metricVersionId: selected.metricVersionId,
                    metricVersion: selected.metricVersion,
                    snapshotDigest: selected.snapshotDigest,
                    publicationEventId: selected.publicationEventId,
                  } : null,
                });
              }}
            />
          </label>
          <label>期望刷新目标（仅业务意图，非运行 SLA）
            <Input className="mt-1" disabled={!canEdit || saving}
              value={draft.refreshTarget}
              onChange={(event) => apply({ refreshTarget: event.target.value })}
              placeholder="如 DAILY / 每日 08:00 前" />
          </label>
        </div>
        {draft.metricPublicationPin && <Typography.Text type="secondary">
          固定引用：Metric #{draft.metricPublicationPin.metricId} · V{draft.metricPublicationPin.metricVersion}
          · 发布事件 #{draft.metricPublicationPin.publicationEventId}
          · 摘要 {draft.metricPublicationPin.snapshotDigest.slice(0, 12)}
        </Typography.Text>}
        {!draft.metricPublicationPin && draft.metricVersionId != null && <Alert showIcon type="warning"
          message="旧合同仅有 Metric 版本 ID，身份不完整"
          description="不会根据版本 ID 猜测指标或发布事件。请显式选择当前已发布指标，或清除旧引用后再保存新修订。" />}
        {metricReferencesStatus === 'UNAVAILABLE' && <Alert showIcon type="warning"
          message="无法读取当前项目的已发布指标"
          description="当前用户可能缺少 Metric READ 权限，或 Metric 原域暂不可用。引用选择不可用；不能据此断言指标不存在。" />}
        {metricReferencesStatus === 'READY' && metricReferences.length === 0 && <Alert showIcon type="info"
          message="当前项目暂无可供新合同引用的已发布指标"
          description="可继续设计不引用 Metric 的 ADS 输出合同；不能手工输入 ID 绕过发布身份核验。" />}
        <Alert showIcon type="info"
          message="Metric 发布引用按原域事实核验；运行与消费授权仍未接入"
          description="选择已发布引用后，服务端会核验当前项目权限、发布事件、版本 ID 和 SHA-256 快照摘要。引用本身不证明存在真实运行任务、Dataset 或消费授权。" />
      </div>}
      {step === 4 && <div className="space-y-4">
        <Typography.Title level={5}>05 · 服务端静态验证与合同保存</Typography.Title>
        <Descriptions size="small" column={1} bordered>
          <Descriptions.Item label="应用">{draft.appName || '未填写'} · {draft.appCode || '未填写'}</Descriptions.Item>
          <Descriptions.Item label="消费方式">{draft.consumerType}</Descriptions.Item>
          <Descriptions.Item label="来源">{draft.sourceMode} · V{draft.upstreamVersionNo || '—'}</Descriptions.Item>
          <Descriptions.Item label="输出">{draft.outputFields.length} 列 · 业务键 {draft.businessKeys.join(', ') || '未填写'}</Descriptions.Item>
          <Descriptions.Item label="静态校验">
            <Tag color={validationState === 'STATIC_PASS' ? 'success' : 'warning'}>{validationState}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="真实数据预演"><Tag>NOT_CONNECTED</Tag></Descriptions.Item>
          <Descriptions.Item label="运行后对账"><Tag>NOT_CONNECTED</Tag></Descriptions.Item>
          <Descriptions.Item label="消费授权"><Tag>UNKNOWN · 未接入消费原域</Tag></Descriptions.Item>
        </Descriptions>
        {validated?.signature === signature && (validated.result.blockers.length > 0
          || validated.result.warnings.length > 0) && <div className="space-y-2">
          {validated.result.blockers.map((item, index) =>
            <Alert key={'block-' + index} type="error" showIcon message={item} />)}
          {validated.result.warnings.map((item, index) =>
            <Alert key={'warn-' + index} type="warning" showIcon message={item} />)}
        </div>}
        <Space wrap>
          <Button onClick={() => void verify()} loading={validating}
            disabled={!canEdit || saving || structureDirty || standardMissing.length > 0
              || sourceStatus !== 'CURRENT' || Boolean(sourceError)}>
            服务端静态校验
          </Button>
          <Button type="primary" loading={saving} disabled={!canEdit || saving
            || validating || structureDirty || standardMissing.length > 0
            || sourceStatus !== 'CURRENT' || Boolean(sourceError)
            || (saved !== null && targetStructureStatus === 'UNKNOWN')
            || (saved !== null && targetStructureStatus === 'STALE' && validationState !== 'STATIC_PASS')}
            onClick={() => void persist()}>
            保存应用设计合同
          </Button>
        </Space>
        <Typography.Paragraph type="secondary">
          静态校验 PASS 不代表真实数据、指标口径或安全授权合格。保存携带当前修订号，由服务端在模型行锁内原子校验；并发变更需要重新读取，不会静默覆盖。
        </Typography.Paragraph>
      </div>}
      {step === 5 && <div className="space-y-4">
        <Typography.Title level={5}><HistoryOutlined /> 06 · 历史修订与交接状态</Typography.Title>
        {saved ? <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="当前修订">R{saved.revision}</Descriptions.Item>
          <Descriptions.Item label="合同哈希"><Typography.Text copyable>{saved.contractSha256}</Typography.Text></Descriptions.Item>
          <Descriptions.Item label="最后保存">{saved.updatedBy} · {saved.updatedAt}</Descriptions.Item>
        </Descriptions> : <Empty description="尚无已保存的应用设计合同" />}
        <AdsHandoffEvidencePanel modelId={modelId} refreshKey={refreshIndex} />
        <Divider orientation="left">不可变修订历史（最多回读最近 50 条）</Divider>
        {history.length === 0 && <Empty description="暂无历史修订" />}
        {history.map((entry) => <div key={entry.revision}
          className="mb-2 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-[#eaecf0] p-3">
          <div>
            <Typography.Text strong>R{entry.revision} · {entry.contract.appName}</Typography.Text>
            <div className="text-[12px] text-[#667085]">{entry.updatedBy} · {entry.updatedAt}</div>
            <Typography.Text type="secondary" className="!text-[12px]">
              {entry.contract.sourceMode} V{entry.contract.upstream.versionNo}
              {' · '}{entry.contract.outputFields.length} 输出列
            </Typography.Text>
          </div>
          <Button disabled={!canEdit || saving} onClick={() => reuseRevision(entry)}>作为新草稿编辑</Button>
        </div>)}
      </div>}
      <Divider />
      <div className="flex flex-wrap justify-between gap-2">
        <Button disabled={step === 0} onClick={() => setStep((old) => old - 1)}>上一步</Button>
        <Space>
          {changed && <Typography.Text type="warning">当前编辑尚未保存到服务端</Typography.Text>}
          <Button type="primary" disabled={step === STEPS.length - 1}
            onClick={() => setStep((old) => old + 1)}>下一步</Button>
        </Space>
      </div>
    </Card>
  );
}
