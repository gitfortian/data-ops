import HttpUtils from '@/utils/HttpUtils';

/** F-039 source-schema tasks. No row-level queries, writes or arbitrary SQL are exposed. */
const PREFIX = '/api/v1/agent/source-semantic';
const OPTIONS = { credentials: 'include' as const, skipErrorHandler: true };

export interface SourceColumn {
  name: string;
  contentHash: string;
  dataType: string;
  comment: string;
  primaryKey: boolean;
}
export interface SourceTable {
  assetKey: string;
  name: string;
  contentHash: string;
  declaredColumnCount: number;
  columns: SourceColumn[];
}
export interface SourceSelection {
  assetKey: string;
  columnNames: string[];
}
export interface SourceTask {
  taskId: string;
  sessionId: string;
  status: string;
  originalStatus: string | null;
  outcome: string;
  planSha256: string;
  planMarkdown: string;
  scopeFingerprint: string;
  completedChunks: number;
  totalChunks: number;
  usedTurns: number;
  maxTurns: number;
  reservedToolCalls: number;
  maxToolCalls: number;
  activeTurnId: string | null;
  nextChunkId: string | null;
  completedTurnIds: Record<string, string>;
  resultDigests: Record<string, string>;
}
export interface SourcePreview {
  scope: { projectId: number; dataSourceId: string; fingerprint?: string };
  chunkCount: number;
  evidence: {
    lastCollectAt: string;
    collectJobId: string;
    fingerprint: string;
    tables: SourceTable[];
  };
}
export interface SourceArtifact {
  taskId: string;
  chunkId: string;
  turnId: string;
  markdown: string;
  sha256: string;
}

export const sourceSemanticApi = {
  tables: (dataSourceId: string, page = 1) =>
    HttpUtils.getData<Array<{ assetKey: string; tableName: string; database: string; schema: string }>>(
      `${PREFIX}/tables?dataSourceId=${encodeURIComponent(dataSourceId)}&page=${page}`, OPTIONS),
  columns: (dataSourceId: string, assetKey: string) =>
    HttpUtils.getData<SourceTable>(
      `${PREFIX}/columns?dataSourceId=${encodeURIComponent(dataSourceId)}&assetKey=${encodeURIComponent(assetKey)}`,
      OPTIONS),
  preview: (dataSourceId: string, selection: SourceSelection[]) =>
    HttpUtils.postData<SourcePreview>(`${PREFIX}/preview`, { dataSourceId, selection }, OPTIONS),
  create: (payload: { sessionId: string; dataSourceId: string;
    selection: SourceSelection[]; businessContext: string }) =>
    HttpUtils.postData<SourceTask>(`${PREFIX}/tasks`, payload, OPTIONS),
  read: (taskId: string) =>
    HttpUtils.getData<SourceTask>(`${PREFIX}/tasks/${encodeURIComponent(taskId)}`, OPTIONS),
  approve: (taskId: string, reviewedPlanSha256: string) =>
    HttpUtils.postData<SourceTask>(`${PREFIX}/tasks/${encodeURIComponent(taskId)}/approve`,
      { reviewedPlanSha256 }, OPTIONS),
  action: (taskId: string, action: 'next' | 'pause' | 'resume' | 'cancel') =>
    HttpUtils.postData<SourceTask>(
      `${PREFIX}/tasks/${encodeURIComponent(taskId)}/${action}`, {}, OPTIONS),
  artifact: (taskId: string, chunkId: string) =>
    HttpUtils.getData<SourceArtifact>(
      `${PREFIX}/tasks/${encodeURIComponent(taskId)}/artifacts/${encodeURIComponent(chunkId)}`, OPTIONS),
};

/** Source and task selectors are not executable parameters and must remain URL-encoded. */
export function sourceTaskPath(dataSourceId: string, taskId?: string): string {
  const query = new URLSearchParams({ dataSourceId });
  if (taskId) query.set('sourceTaskId', taskId);
  return `/ai-agent?${query.toString()}`;
}
