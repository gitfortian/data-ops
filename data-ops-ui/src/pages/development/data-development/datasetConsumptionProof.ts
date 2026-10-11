import type {
  DevelopmentReleaseDetail, DevelopmentReleasePage, DevelopmentReleaseSummary,
} from '@/services/data-development';
import type { DatasetManagementItem } from '@/services/dataset/model';
import type { ConsumerImpact, ProductLookupResult } from '@/services/consumption';

/**
 * C6 is a read-only cross-domain evidence projection, NOT a G2 success flag.
 * A physical Modeling version has no approved durable link to a DataDev node.
 */
export type ReleaseLookup =
  | { state: 'INVALID'; reason: string }
  | { state: 'NOT_FOUND_IN_WINDOW'; examined: number; total: number; complete: boolean }
  | { state: 'FOUND'; release: DevelopmentReleaseSummary; examined: number; total: number; complete: boolean };

export const findExactSqlRelease = (nodeId: string, page: DevelopmentReleasePage): ReleaseLookup => {
  if (!nodeId || !page || page.pageNo !== 1 || !Array.isArray(page.records)
    || !Number.isSafeInteger(page.pageSize) || page.pageSize <= 0
    || !Number.isSafeInteger(page.total) || page.total < page.records.length
    || page.records.some(r => !r.assetId || !r.nodeId || !r.taskType || !r.currentRevisionId)) {
    return { state: 'INVALID', reason: 'DataDev 发布中心返回的任务身份或分页信息不可核查' };
  }
  const matching = page.records.filter(r => r.nodeId === nodeId && r.taskType === 'SQL');
  if (matching.length > 1) {
    return { state: 'INVALID', reason: '同一个 SQL 节点出现多个 TaskAsset，不允许自动选择' };
  }
  if (!matching.length) {
    return { state: 'NOT_FOUND_IN_WINDOW', examined: page.records.length, total: page.total,
      complete: page.total <= page.records.length };
  }
  return { state: 'FOUND', release: matching[0], examined: page.records.length,
    total: page.total, complete: page.total <= page.records.length };
};

export type DatasetSourceProof =
  | { state: 'INVALID_RELEASE'; reason: string }
  | { state: 'MATCHED'; datasets: DatasetManagementItem[]; totalDatasets: number };

/**
 * Only the current Dataset version with an exact TaskAsset + revision ID + revision number
 * is related to this DataDev release. Do not infer from task name or an arbitrary SQL string.
 * Catalog contains current versions, not historic Dataset revisions.
 */
export const linkCurrentDatasetsToRelease = (
  release: DevelopmentReleaseSummary,
  detail: DevelopmentReleaseDetail,
  catalog: DatasetManagementItem[],
): DatasetSourceProof => {
  const revisions = detail.revisions;
  if (!Array.isArray(catalog) || !Array.isArray(revisions)
    || detail.release.assetId !== release.assetId || detail.release.nodeId !== release.nodeId
    || detail.release.currentRevisionId !== release.currentRevisionId
    || detail.currentRevision.id !== release.currentRevisionId
    || detail.currentRevision.nodeId !== release.nodeId
    || detail.currentRevision.revisionNo !== release.currentRevisionNo
    || revisions.some(r => !r.id || r.nodeId !== release.nodeId
      || !Number.isSafeInteger(r.revisionNo) || r.revisionNo <= 0)
    || !revisions.some(r => r.id === release.currentRevisionId
      && r.revisionNo === release.currentRevisionNo)
    || new Set(revisions.map(r => r.id)).size !== revisions.length
    || new Set(revisions.map(r => r.revisionNo)).size !== revisions.length) {
    return { state: 'INVALID_RELEASE', reason: 'DataDev TaskAsset 或历史修订身份与发布详情不符，不生成 Dataset 来源关联' };
  }
  const refs = new Map(revisions.map(r => [r.id, r.revisionNo]));
  const matched = catalog.filter(dataset => {
    const version = dataset.currentVersion;
    if (!dataset.id || !version || version.datasetId !== dataset.id
      || (dataset.currentVersionId && dataset.currentVersionId !== version.id)
      || version.sourceType !== 'QUERY_REVISION'
      || version.sourceTaskAssetId !== release.assetId
      || !version.sourceTaskRevisionId || !version.sourceTaskRevisionNo) return false;
    return refs.get(version.sourceTaskRevisionId) === version.sourceTaskRevisionNo;
  });
  return { state: 'MATCHED', datasets: matched, totalDatasets: catalog.length };
};

export type DatasetConsumptionProof =
  | { state: 'INVALID_OWNER'; reason: string }
  | { state: 'NOT_OBSERVED' | 'OBSERVED' | 'UNAVAILABLE';
      successfulUsage: number; coverageNote: string; usageState: string;
      activeVersionId: string; lastObservedAt?: string };

export const inspectExactDatasetConsumption = (
  dataset: DatasetManagementItem, projectId: number,
  lookup: ProductLookupResult, impact: ConsumerImpact,
): DatasetConsumptionProof => {
  const versionId = dataset.currentVersion?.id;
  const versionNo = dataset.currentVersion?.versionNo;
  const product = lookup.product;
  if (!versionId || !dataset.id || !Number.isSafeInteger(projectId) || projectId <= 0
    || lookup.state !== 'FOUND' || !product
    || product.projectId !== projectId
    || product.productKey.productType !== 'DATASET'
    || product.productKey.sourceIdentity !== dataset.id
    || impact.productKey.productType !== 'DATASET'
    || impact.productKey.sourceIdentity !== dataset.id
    || !product.activeVersion || product.activeVersion.identity !== versionId
    || (dataset.currentVersionId && dataset.currentVersionId !== versionId)
    || typeof versionNo !== 'number' || !Number.isSafeInteger(versionNo) || versionNo <= 0) {
    return { state: 'INVALID_OWNER', reason: 'Dataset 当前版本与 Consumption 产品、项目或消费归属不一致' };
  }
  if (dataset.status !== 'ONLINE'
    || product.lifecycle !== 'PUBLISHED'
    || product.availability !== 'AVAILABLE'
    || product.access.providerState !== 'READY'
    || impact.usageState === 'FORBIDDEN' || impact.usageState === 'UNAVAILABLE'
    || impact.coverage?.sourceReadUnavailable) {
    return {
      state: 'UNAVAILABLE', successfulUsage: 0, usageState: impact.usageState,
      activeVersionId: versionId, coverageNote: impact.coverageNote || '治理或访问证据不可用',
    };
  }
  // Consumption provider state is authoritative. An EMPTY response must not be
  // upgraded to success by contradictory nested aggregates from a stale read.
  if (impact.usageState !== 'READY' && impact.usageState !== 'EMPTY') {
    return { state: 'UNAVAILABLE', successfulUsage: 0, usageState: impact.usageState,
      activeVersionId: versionId, coverageNote: impact.coverageNote || 'Consumption 当前不提供可核查使用证据' };
  }
  // Do not count unversioned consumer aggregates. Only exact immutable source version receipts.
  const receipts = (impact.usageState === 'READY' ? impact.consumers : [])
    .flatMap(c => c.observedVersions ?? [])
    .filter(v => v.sourceVersion.identity === versionId
      && Number.isSafeInteger(v.successfulUsageCount) && v.successfulUsageCount > 0);
  // A version may be present under multiple consumers. We display observations without
  // inferring a global deduplicated source-event count.
  const successfulUsage = receipts.reduce((total, v) => total + v.successfulUsageCount, 0);
  return {
    state: successfulUsage > 0 ? 'OBSERVED' : 'NOT_OBSERVED',
    successfulUsage, usageState: impact.usageState, activeVersionId: versionId,
    coverageNote: impact.coverageNote || '本次读取的 Consumption 使用证据窗口',
    lastObservedAt: receipts.map(r => r.lastObservedAt || '').sort().reverse()[0] || undefined,
  };
};
