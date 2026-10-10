import HttpUtils from '@/utils/HttpUtils';

const PREFIX = '/api/v1/agent/source-semantic/tasks';
const OPTIONS = { credentials: 'include' as const, skipErrorHandler: true };

export interface SemanticCatalogEntry {
  kind: string;
  id: number | null;
  version: number;
  code: string | null;
  name: string;
  status: string;
  role: string | null;
}
export interface CandidateEvidence {
  tableAssetKey: string;
  column: string | null;
  sourceChunkId: string;
}
export interface SemanticCandidate {
  id: string;
  kind: string;
  code: string | null;
  name: string;
  role: string | null;
  grain: string | null;
  description: string | null;
  typeId: number | null;
  unitId: number | null;
  reuseId: number | null;
  reuseVersion: number | null;
  dependencies: string[];
  evidence: CandidateEvidence[];
}
export interface CandidateReview {
  taskId: string;
  projectId: number;
  sourceFingerprint: string;
  planSha256: string;
  revision: number;
  candidates: SemanticCandidate[];
  selectedIds: string[];
  answers: Record<string, string>;
}
export interface CandidateView {
  review: CandidateReview;
  catalogEntries: SemanticCatalogEntry[];
  matches: Array<{ candidateId: string; matches: SemanticCatalogEntry[]; ambiguous: boolean }>;
}
export interface AdoptionReceipt {
  candidateId: string;
  status: 'CREATED' | 'REUSED' | 'LINKED' | 'NOT_EXECUTED' | 'NEEDS_RECONCILIATION' | 'WAITING_APPROVAL' | string;
  semanticId: number | null;
  semanticVersion: number | null;
  kind: string;
  message: string | null;
}
export interface CandidatePreflight {
  revision: number;
  payloadDigest: string;
  ticket: string | null;
  selected: string[];
  closure: string[];
  blockers: string[];
  ready: boolean;
}
const path = (taskId: string) => `${PREFIX}/${encodeURIComponent(taskId)}/candidates`;
export const sourceSemanticCandidates = {
  read: (taskId: string) => HttpUtils.getData<CandidateView>(path(taskId), OPTIONS),
  edit: (taskId: string, candidate: SemanticCandidate, expectedRevision: number) =>
    HttpUtils.postData<CandidateView>(`${path(taskId)}/edit`, {
      expectedRevision,
      candidateId: candidate.id,
      code: candidate.code,
      name: candidate.name,
      role: candidate.role,
      grain: candidate.grain,
      description: candidate.description,
      typeId: candidate.typeId,
      unitId: candidate.unitId,
      reuseId: candidate.reuseId,
      reuseVersion: candidate.reuseVersion,
    }, OPTIONS),
  select: (taskId: string, expectedRevision: number, ids: string[]) =>
    HttpUtils.postData<CandidateView>(`${path(taskId)}/select`, { expectedRevision, ids }, OPTIONS),
  merge: (taskId: string, expectedRevision: number, firstId: string, secondId: string) =>
    HttpUtils.postData<CandidateView>(`${path(taskId)}/merge`,
      { expectedRevision, firstId, secondId, confirmedSameMeaning: true }, OPTIONS),
  split: (taskId: string, expectedRevision: number, candidateId: string,
    tableAssetKey: string, column: string) =>
    HttpUtils.postData<CandidateView>(`${path(taskId)}/split`,
      { expectedRevision, candidateId, tableAssetKey, column }, OPTIONS),
  answer: (taskId: string, expectedRevision: number, questionId: string, value: string) =>
    HttpUtils.postData<CandidateView>(`${path(taskId)}/answer`,
      { expectedRevision, questionId, value }, OPTIONS),
  adopt: (taskId: string, revision: number, payloadDigest: string, preflightTicket: string) =>
    HttpUtils.postData<AdoptionReceipt[]>(`${path(taskId)}/adopt`,
      { revision, payloadDigest, preflightTicket, confirmed: true }, OPTIONS),
  receipts: (taskId: string) =>
    HttpUtils.getData<AdoptionReceipt[]>(`${path(taskId)}/adoption-receipts`, OPTIONS),
  preflight: (taskId: string, revision: number) =>
    HttpUtils.postData<CandidatePreflight>(
      `${path(taskId)}/preflight?revision=${encodeURIComponent(String(revision))}`, {}, OPTIONS),
};
