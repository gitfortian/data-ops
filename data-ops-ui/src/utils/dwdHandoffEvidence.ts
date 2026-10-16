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
  if (binding.state !== 'DESIGN_PUBLISHED_NOT_EXECUTED' || !binding.mapping
      || binding.mapping.mappingRevisionId <= 0 || binding.mapping.mappingSetId <= 0
      || binding.mapping.logicalModelId <= 0 || binding.mapping.logicalVersionNo <= 0
      || !/^[a-f0-9]{64}$/.test(binding.mapping.contentSha256)
      || binding.mapping.rules.length === 0) {
    return { ready: false, reason: '当前版本没有可核验的冻结逻辑属性和映射修订；旧版不能冒充已映射设计' };
  }
  return { ready: true, reason: '' };
}
