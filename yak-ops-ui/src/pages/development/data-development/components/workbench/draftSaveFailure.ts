export type DraftSaveFailureKind =
  | 'conflict'
  | 'permission-denied'
  | 'not-found'
  | 'network'
  | 'unknown';

export interface DraftSaveFailure {
  kind: DraftSaveFailureKind;
  status?: number;
  detail?: string;
}

const asRecord = (value: unknown): Record<string, any> | undefined =>
  value && typeof value === 'object' ? (value as Record<string, any>) : undefined;

const messageFrom = (error: unknown) => {
  const record = asRecord(error);
  const data = asRecord(record?.data);
  const responseData = asRecord(asRecord(record?.response)?.data);
  const detail = data?.message ?? data?.msg ?? responseData?.message ?? responseData?.msg;
  if (typeof detail === 'string' && detail.trim()) return detail.trim();
  if (error instanceof Error && error.message.trim()) return error.message.trim();
  return undefined;
};

const statusFrom = (error: unknown) => {
  const record = asRecord(error);
  const response = asRecord(record?.response);
  const status = response?.status ?? record?.status;
  return typeof status === 'number' && Number.isFinite(status) ? status : undefined;
};

export const classifyDraftSaveFailure = (error: unknown): DraftSaveFailure => {
  const status = statusFrom(error);
  const detail = messageFrom(error);

  if (status === 409) return { kind: 'conflict', status, detail };
  if (status === 403) return { kind: 'permission-denied', status, detail };
  if (status === 404 || status === 410) return { kind: 'not-found', status, detail };
  if (status === undefined) return { kind: 'network', detail };
  return { kind: 'unknown', status, detail };
};
