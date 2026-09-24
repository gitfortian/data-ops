export type WorkspaceLoadFailureKind = 'permission-denied' | 'unavailable';

export interface WorkspaceLoadFailure {
  kind: WorkspaceLoadFailureKind;
  status?: number;
  detail?: string;
}

const asRecord = (value: unknown): Record<string, any> | undefined =>
  value && typeof value === 'object' ? (value as Record<string, any>) : undefined;

const statusFrom = (error: unknown) => {
  const record = asRecord(error);
  const response = asRecord(record?.response);
  const status = response?.status ?? record?.status;
  if (typeof status === 'number' && Number.isFinite(status)) return status;

  const code = record?.code;
  return typeof code === 'number' && Number.isFinite(code) ? code : undefined;
};

const detailFrom = (error: unknown) => {
  const record = asRecord(error);
  const data = asRecord(record?.data);
  const responseData = asRecord(asRecord(record?.response)?.data);
  const detail =
    data?.message ??
    data?.msg ??
    responseData?.message ??
    responseData?.msg ??
    record?.message;
  if (typeof detail === 'string' && detail.trim()) return detail.trim();
  if (error instanceof Error && error.message.trim()) return error.message.trim();
  return undefined;
};

/**
 * Workspace catalogue failures are facts, not empty results.
 *
 * A successful [] response is the only path that may render the empty workspace.
 * Any rejected request is classified separately so the UI never turns permission,
 * transport or server failure into a fake EMPTY state.
 */
export const classifyWorkspaceLoadFailure = (
  error: unknown,
): WorkspaceLoadFailure => {
  const status = statusFrom(error);
  const detail = detailFrom(error);

  if (status === 403) return { kind: 'permission-denied', status, detail };
  return { kind: 'unavailable', status, detail };
};
