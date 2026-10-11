import type {
  DevelopmentTaskExecutionPage, DevelopmentTaskExecutionSummary,
  DevelopmentTaskRevisionSummary,
} from '@/services/data-development';

/**
 * C5 evidence belongs to DataDev. No physical ModelVersionRef is written here;
 * an observed execution must NEVER be classified as a successful business
 * delivery or proof that its SQL processed the selected Modeling version.
 */
export type TaskRevisionInspection =
  | { state: 'INVALID_OWNER'; reason: string }
  | { state: 'NO_PUBLISHED_REVISION'; revisions: DevelopmentTaskRevisionSummary[] }
  | { state: 'PUBLISHED_REVISION_EXISTS'; revisions: DevelopmentTaskRevisionSummary[] };

export const inspectTaskRevisions = (
  nodeId: string,
  revisions: DevelopmentTaskRevisionSummary[],
): TaskRevisionInspection => {
  if (!nodeId || !Array.isArray(revisions)
    || revisions.some(v => v.nodeId !== nodeId || !v.id
      || !Number.isSafeInteger(v.revisionNo) || v.revisionNo <= 0)
    || new Set(revisions.map(v => v.revisionNo)).size !== revisions.length) {
    return { state: 'INVALID_OWNER', reason: 'DataDev 任务修订的节点、编号或列表不一致，拒绝用于交接' };
  }
  if (!revisions.length) return { state: 'NO_PUBLISHED_REVISION', revisions: [] };
  return {
    state: 'PUBLISHED_REVISION_EXISTS',
    revisions: [...revisions].sort((a, b) => b.revisionNo - a.revisionNo),
  };
};

export type TaskExecutionObservation =
  | { state: 'INVALID_PAGE'; reason: string }
  | {
      state: 'OBSERVED' | 'NOT_OBSERVED_IN_WINDOW';
      matching: DevelopmentTaskExecutionSummary[];
      scanned: number;
      total: number;
      complete: boolean;
    };

/**
 * The existing DataDev page API has NO nodeId filter.
 * We only consider exact nodeId records in the first bounded page; even a
 * SUCCESS result is not a verified Modeling→DataDev association.
 */
export const observeTaskExecutions = (
  nodeId: string,
  page: DevelopmentTaskExecutionPage,
): TaskExecutionObservation => {
  if (!nodeId || !page || !Array.isArray(page.records)
    || page.pageNo !== 1
    || !Number.isSafeInteger(page.pageSize) || page.pageSize < 1
    || !Number.isSafeInteger(page.total) || page.total < page.records.length
    || page.records.some(row => !row.id || !row.nodeId || !row.taskType || !row.status)) {
    return { state: 'INVALID_PAGE', reason: '运行历史分页或执行身份不完整，拒绝推断运行状态' };
  }
  const matching = page.records
    .filter(row => row.nodeId === nodeId && row.taskType === 'SQL')
    .sort((a, b) => String(b.startTime || '').localeCompare(String(a.startTime || '')));
  return {
    state: matching.length ? 'OBSERVED' : 'NOT_OBSERVED_IN_WINDOW',
    matching,
    scanned: page.records.length,
    total: page.total,
    complete: page.total <= page.records.length,
  };
};
