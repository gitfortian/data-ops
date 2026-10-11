import type { PhysicalStandardCoverageInspection } from '@/services/modeling/api';
import type { LogicalVersionSemanticReview } from '@/services/modeling/logical';

/**
 * F-040: browser-session comparison ONLY. These are not persisted approvals,
 * quality receipts, published versions or DataDev handoff authorizations.
 */
export type GovernanceRecheckResult =
  | { state: 'WRONG_OWNER'; explanation: string }
  | { state: 'UNCHANGED'; explanation: string }
  | { state: 'CHANGED'; explanation: string };

export type PhysicalGovernanceBaseline = Pick<
  PhysicalStandardCoverageInspection,
  'modelId' | 'evidenceSha256' | 'missingCount' | 'invalidCount' | 'reviewCount'
>;

export const physicalGovernanceBaseline = (
  report: PhysicalStandardCoverageInspection,
): PhysicalGovernanceBaseline => ({
  modelId: report.modelId, evidenceSha256: report.evidenceSha256,
  missingCount: report.missingCount, invalidCount: report.invalidCount,
  reviewCount: report.reviewCount,
});

export const comparePhysicalGovernance = (
  baseline: PhysicalGovernanceBaseline,
  current: PhysicalStandardCoverageInspection,
): GovernanceRecheckResult => {
  if (baseline.modelId !== current.modelId || !baseline.evidenceSha256 || !current.evidenceSha256) {
    return { state: 'WRONG_OWNER', explanation: '核验的模型身份或指纹缺失，不能与旧证据比较' };
  }
  if (baseline.evidenceSha256 === current.evidenceSha256) {
    return { state: 'UNCHANGED', explanation: '与上次定位时的物理治理证据相同；这不代表所有标准治理已完成' };
  }
  return {
    state: 'CHANGED',
    explanation: `证据已变化：缺少标准 ${baseline.missingCount}→${current.missingCount}，
失效引用 ${baseline.invalidCount}→${current.invalidCount}，
聚合待确认 ${baseline.reviewCount}→${current.reviewCount}。请以本次报告逐列结果为准，不自动判定修复成功`,
  };
};

export type LogicalGovernanceBaseline = Pick<
  LogicalVersionSemanticReview,
  'logicalModelId' | 'versionNo' | 'frozenSnapshotSha256' | 'currentDesignSha256'
  | 'reviewStatus'
> & {
  /** Snapshot of current Semantic references and review findings, not a cryptographic hash. */
  reviewProjection: string;
  pendingAttributeCount: number;
  pendingRelationCount: number;
};

export const logicalGovernanceBaseline = (
  report: LogicalVersionSemanticReview,
): LogicalGovernanceBaseline => ({
  logicalModelId: report.logicalModelId,
  versionNo: report.versionNo,
  frozenSnapshotSha256: report.frozenSnapshotSha256,
  currentDesignSha256: report.currentDesignSha256,
  reviewStatus: report.reviewStatus,
  pendingAttributeCount: report.attributes.filter(a => a.standardStatus !== 'ENABLED_NOW').length,
  pendingRelationCount: report.relations.length,
  reviewProjection: JSON.stringify({
    status: report.reviewStatus, currentDesign: report.currentDesignSha256,
    attributes: report.attributes.map(a => [
      a.entityId, a.attributeId, a.stdFieldId, a.standardStatus, a.explanation,
    ]),
    relations: report.relations.map(r => [r.relationId, r.cardinality, r.status, r.explanation]),
    blockers: report.blockers,
    changes: report.changes,
  }),
});

export const compareLogicalGovernance = (
  baseline: LogicalGovernanceBaseline,
  current: LogicalVersionSemanticReview,
): GovernanceRecheckResult => {
  if (baseline.logicalModelId !== current.logicalModelId
    || baseline.versionNo !== current.versionNo
    || !baseline.frozenSnapshotSha256
    || baseline.frozenSnapshotSha256 !== current.frozenSnapshotSha256) {
    return { state: 'WRONG_OWNER', explanation: '逻辑模型或冻结版本快照身份不一致，不能比较两次治理报告' };
  }
  const after = logicalGovernanceBaseline(current);
  if (baseline.reviewProjection === after.reviewProjection) {
    return { state: 'UNCHANGED', explanation: '当前业务设计、标准引用和治理发现与上次审阅一致；不表示已获批准' };
  }
  return {
    state: 'CHANGED',
    explanation: `证据已变化：待核对标准属性 ${baseline.pendingAttributeCount}→${after.pendingAttributeCount}，
待确认关系 ${baseline.pendingRelationCount}→${after.pendingRelationCount}。
冻结 V${baseline.versionNo} 未被本次审阅修改；请人工检查当前差异和所有阻断项`,
  };
};
