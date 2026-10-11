import type { PhysicalDataDevHandoffReadiness } from '@/services/modeling/api';
import type { ModelingModelRecord, ModelingVersionSummary } from '@/services/modeling/types';

/**
 * URL parameters are navigation hints, NEVER a persisted ModelVersionRef.
 * DataDev re-reads each owning domain before showing any version evidence.
 */
export interface PhysicalDataDevHandoffHint {
  processId: number;
  modelId: number;
  modelVersionNo: number;
}

export type ParsedPhysicalDataDevHint =
  | { kind: 'NONE' }
  | { kind: 'INVALID'; problem: string }
  | { kind: 'HINT'; value: PhysicalDataDevHandoffHint };

const positiveInt = (value: string | null): number | undefined => {
  if (!value || !/^[1-9]\d*$/.test(value)) return undefined;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
};

export const physicalDataDevHandoffUrl = (hint: PhysicalDataDevHandoffHint): string => {
  if (!Object.values(hint).every(id => Number.isSafeInteger(id) && id > 0)) {
    throw new Error('交接导航必须包含真实且合法的业务过程、模型与发布版本号');
  }
  const params = new URLSearchParams({
    source: 'modeling-handoff',
    processId: String(hint.processId),
    modelId: String(hint.modelId),
    modelVersionNo: String(hint.modelVersionNo),
  });
  return `/data-development?${params.toString()}`;
};

export const parsePhysicalDataDevHandoff = (search: string): ParsedPhysicalDataDevHint => {
  const params = new URLSearchParams(search);
  const marker = params.get('source');
  const keys = ['source', 'processId', 'modelId', 'modelVersionNo'];
  if (!keys.some(key => params.has(key))) return { kind: 'NONE' };
  if (marker !== 'modeling-handoff' || keys.some(key => params.getAll(key).length !== 1)) {
    return { kind: 'INVALID', problem: '不完整或重复的模型交接参数；请从已发布物理版本重新进入' };
  }
  const processId = positiveInt(params.get('processId'));
  const modelId = positiveInt(params.get('modelId'));
  const modelVersionNo = positiveInt(params.get('modelVersionNo'));
  if (!processId || !modelId || !modelVersionNo) {
    return { kind: 'INVALID', problem: '模型、版本或业务过程 ID 不合法，拒绝自动选用其他对象' };
  }
  return { kind: 'HINT', value: { processId, modelId, modelVersionNo } };
};

export const verifyPhysicalDataDevEvidence = (input: {
  hint: PhysicalDataDevHandoffHint;
  owner: ModelingModelRecord;
  processId: number;
  versions: ModelingVersionSummary[];
  report: PhysicalDataDevHandoffReadiness;
}): { valid: true } | { valid: false; reason: string } => {
  const { hint, owner, processId, versions, report } = input;
  const frozen = versions.filter(v => v.versionNo === hint.modelVersionNo);
  const signature = report.version.frozenStructureSha256;
  if (Number(owner.id) !== hint.modelId || owner.processId !== hint.processId
    || processId !== hint.processId
    || !['DWD', 'DIM'].includes(owner.layerCode?.toUpperCase() ?? '')
    || report.modelId !== hint.modelId
    || report.version.modelId !== hint.modelId
    || report.version.versionNo !== hint.modelVersionNo
    || frozen.length !== 1
    || !Number.isSafeInteger(report.version.versionId) || report.version.versionId <= 0
    || !/^[a-f0-9]{64}$/i.test(signature)
    || !/^[a-f0-9]{64}$/i.test(report.inspectionSha256)
    || !['DWD', 'DIM'].includes(report.layerCode)) {
    return { valid: false, reason: 'Modeling / Semantic 的实际对象、冻结版本或指纹与导航不一致；拒绝交接展示' };
  }
  return { valid: true };
};

/** A chosen DataDev SQL task is a review candidate, never a persisted association. */
export const isCurrentProjectSqlCandidate = (
  node: { id: string; type: string; projectId?: string | null },
  projectId: string | number | undefined,
): boolean => Boolean(
  node.id && node.type === 'SQL' && projectId != null
  && (node.projectId == null || String(node.projectId) === String(projectId)),
);
