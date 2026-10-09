import {
  cancelDevelopmentTaskExecution,
  getActiveDevelopmentTaskExecution,
  getDevelopmentTaskDraft,
  getDevelopmentTaskExecution,
  retryDevelopmentTaskExecution,
  type DevelopmentId,
  type DevelopmentTaskDraft,
  type DevelopmentTaskExecutionDetail,
  type DevelopmentTaskExecutionSubmission,
} from '@/services/data-development';

/**
 * Modern data-only HTTP boundary for the Workbench.
 *
 * Save, Run, Publish and SQL Lineage preview deliberately stay in the page
 * coordinator: they include optimistic 409 recovery, user confirmation,
 * Run preflight and live SQL editor metadata, respectively.
 */
const requiredData = <T,>(data: T | null | undefined, failure: string): T => {
  if (data === null || data === undefined) throw new Error(failure);
  return data;
};

export const loadWorkbenchDraft = async (
  nodeId: DevelopmentId,
  failure: string,
): Promise<DevelopmentTaskDraft> =>
  requiredData(await getDevelopmentTaskDraft(nodeId), failure);

/** A null response means there is no active execution; an absent payload is invalid. */
export const loadWorkbenchActiveExecution = async (
  nodeId: DevelopmentId,
  failure: string,
): Promise<DevelopmentTaskExecutionDetail | null> => {
  const active = await getActiveDevelopmentTaskExecution(nodeId);
  if (active === undefined) throw new Error(failure);
  return active;
};

export const readWorkbenchExecution = async (
  executionId: DevelopmentId,
  failure: string,
): Promise<DevelopmentTaskExecutionDetail> =>
  requiredData(await getDevelopmentTaskExecution(executionId), failure);

export const cancelWorkbenchExecution = async (
  executionId: DevelopmentId,
  failure: string,
): Promise<DevelopmentTaskExecutionDetail> =>
  requiredData(await cancelDevelopmentTaskExecution(executionId), failure);

/** Do not manufacture a successful retry unless the server issued a durable ID. */
export const retryWorkbenchExecution = async (
  executionId: DevelopmentId,
  failure: string,
): Promise<DevelopmentTaskExecutionSubmission> => {
  const submission = await retryDevelopmentTaskExecution(executionId);
  if (!submission?.id) throw new Error(failure);
  return submission;
};
