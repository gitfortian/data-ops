import type { ModelingVersionSummary } from '@/services/modeling/types';
import type { PhysicalDataDevHandoffReadiness } from '@/services/modeling/api';
import type { DwdPublishedMapping } from '@/services/modeling/dwdMapping';

/** A DWD read-only handoff may display exact published design evidence, never a draft. */
export function assessDwdMappingEvidence(
  modelId: number, versionNo: number | undefined,
  readiness: PhysicalDataDevHandoffReadiness | undefined,
  versions: ModelingVersionSummary[], binding: DwdPublishedMapping | undefined,
): { ready: boolean; reason: string } {
  if (!readiness || versionNo == null || !binding) {
    return { ready: false, reason: '尚未获取完整的模型版本与冻结映射证据' };
  }
  const version = versions.find(v => v.versionNo === versionNo && v.modelId === modelId);
  if (!version || readiness.modelId !== modelId || readiness.version.modelId !== modelId
      || readiness.version.versionNo !== versionNo || readiness.version.versionId !== version.id
      || binding.versionNo !== versionNo || binding.modelVersionId !== version.id) {
    return { ready: false, reason: '物理模型、发布版本或映射证据身份不一致，请重新打开版本' };
  }
  const mapping = binding.mapping;
  const positiveId = (value: number): boolean =>
    Number.isSafeInteger(value) && value > 0;
  const sha256 = (value: string): boolean => /^[a-f0-9]{64}$/.test(value);
  if (binding.state !== 'DESIGN_PUBLISHED_NOT_EXECUTED' || !mapping
      || !positiveId(mapping.mappingRevisionId) || !positiveId(mapping.mappingSetId)
      || !positiveId(mapping.revisionNo) || !positiveId(mapping.logicalModelId)
      || !positiveId(mapping.logicalVersionNo)
      || !sha256(mapping.logicalSnapshotSha256)
      || !sha256(mapping.physicalStructureSha256)
      || !sha256(mapping.contentSha256)
      || !Array.isArray(mapping.rules) || mapping.rules.length === 0) {
    return { ready: false, reason: '当前版本缺少有效的冻结逻辑身份、物理结构摘要或 MappingRevision；无法认定设计证据完整' };
  }

  // The published Modeling snapshot is immutable. Do not pass malformed/duplicated
  // column rules onward to a SQL authoring handoff as apparently complete evidence.
  // This is a read-only fail-closed check, never a DataDev write authorization.
  const normalizedTargets = new Set<string>();
  for (const rule of mapping.rules) {
    if (!rule || typeof rule.targetColumn !== 'string'
        || !rule.targetColumn.trim() || rule.targetColumn !== rule.targetColumn.trim()
        || !positiveId(rule.logicalAttributeId) || !positiveId(rule.stdFieldId)
        || !['DIRECT', 'RENAME'].includes(rule.kind)) {
      return { ready: false, reason: '冻结映射包含缺失目标列、非法逻辑属性/标准字段引用或未经支持的映射规则；拒绝交接' };
    }
    const target = rule.targetColumn.toLocaleLowerCase('en-US');
    if (normalizedTargets.has(target)) {
      return { ready: false, reason: '冻结映射包含重复的目标物理列，无法证明逐列唯一映射；拒绝交接' };
    }
    normalizedTargets.add(target);
  }
  return { ready: true, reason: '' };
}
