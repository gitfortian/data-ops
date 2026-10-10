import type { AdoptionReceipt, CandidateReview, SemanticCandidate } from './sourceSemanticCandidates';
import {
  getSemanticDomainTree, getSemanticProcess, getSemanticStandard, getSemanticField,
  listSemanticProcessFields, listSemanticProcessSources,
} from '@/services/semantic/api';
import type { SemanticDomainNode } from '@/services/semantic/types';

/**
 * F-039 #498: original Semantic pages own truth. Candidate names/ids, agent answers
 * and preflight never authorize deep links. Re-read the original current-project
 * owner before offering a handoff, and let the destination re-check access.
 */
export type VerifiedHandoff = { path: string; label: string; detail: string };
const success = new Set(['CREATED', 'REUSED', 'LINKED']);
const positive = (id: number | null): id is number =>
  id !== null && Number.isSafeInteger(id) && id > 0;
const processPath = (id: number) => `/semantic/processes?processId=${id}`;

function findDomain(nodes: SemanticDomainNode[], id: number): SemanticDomainNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node;
    const child = findDomain(node.children || [], id);
    if (child) return child;
  }
  return undefined;
}
function ancestorProcess(candidate: SemanticCandidate,
  byCandidate: Map<string, SemanticCandidate>): string | undefined {
  const visited = new Set<string>();
  const pending = [...candidate.dependencies];
  while (pending.length > 0) {
    const id = pending.pop()!;
    if (visited.has(id)) continue;
    visited.add(id);
    const found = byCandidate.get(id);
    if (!found) continue;
    if (found.kind === 'PROCESS') return found.id;
    pending.push(...found.dependencies);
  }
  return undefined;
}

export async function verifiedSourceSemanticHandoff(
  item: AdoptionReceipt, review: CandidateReview,
  authoritativeReceipts: AdoptionReceipt[],
): Promise<VerifiedHandoff | null> {
  if (!success.has(item.status) || !positive(item.semanticId)) return null;
  const exact = authoritativeReceipts.find((r) => r.candidateId === item.candidateId);
  if (!exact || !success.has(exact.status) || exact.semanticId !== item.semanticId
      || exact.kind !== item.kind || exact.status !== item.status) return null;
  const candidates = new Map(review.candidates.map((c) => [c.id, c]));
  const candidate = candidates.get(item.candidateId);
  if (!candidate || candidate.kind !== item.kind) return null;
  const id = item.semanticId;
  switch (item.kind) {
    case 'DOMAIN': {
      const domain = findDomain(await getSemanticDomainTree(), id);
      if (!domain) return null;
      return { path: `/semantic/domains?domainId=${id}`, label: '原业务域',
        detail: `${domain.name} · ${domain.code} · ID ${id}` };
    }
    case 'PROCESS': {
      const p = await getSemanticProcess(id);
      if (!p || p.id !== id) return null;
      return { path: processPath(id), label: '原业务过程',
        detail: `${p.name} · ${p.code} · ID ${id}` };
    }
    case 'FIELD': {
      const f = await getSemanticField(id);
      if (!f || f.id !== id) return null;
      return { path: `/semantic/fields?fieldId=${id}`, label: '原标准字段',
        detail: `${f.name} · ${f.code} · ID ${id}` };
    }
    case 'STANDARD_TYPE':
    case 'STANDARD_UNIT':
    case 'STANDARD_CODE': {
      const s = await getSemanticStandard(id);
      const expected = item.kind.substring('STANDARD_'.length);
      if (!s || s.id !== id || s.kind !== expected) return null;
      return { path: `/semantic/standards?standardId=${id}`, label: '原数据标准',
        detail: `${s.name} · ${s.code} · ${s.status} · ID ${id}` };
    }
    case 'PROCESS_FIELD':
    case 'SOURCE_LINK': {
      const processCandidateId = ancestorProcess(candidate, candidates);
      if (!processCandidateId) return null;
      const processReceipt = authoritativeReceipts.find((r) =>
        r.candidateId === processCandidateId && r.kind === 'PROCESS'
        && success.has(r.status) && positive(r.semanticId));
      if (!processReceipt || !positive(processReceipt.semanticId)) return null;
      const process = await getSemanticProcess(processReceipt.semanticId);
      if (!process || process.id !== processReceipt.semanticId) return null;
      if (item.kind === 'PROCESS_FIELD') {
        const fields = await listSemanticProcessFields(process.id);
        if (!fields.some((f) => f.id === id)) return null;
      } else {
        const sources = await listSemanticProcessSources(process.id);
        if (!sources.some((s) => s.id === id)) return null;
      }
      return { path: `/semantic/processes/${process.id}/edit`,
        label: item.kind === 'SOURCE_LINK' ? '原过程来源关联' : '原过程字段关联',
        detail: `${process.name} · 过程 ID ${process.id}` };
    }
    default:
      return null;
  }
}

export function modelingMetricNextSteps(verifiedProcess: VerifiedHandoff | null,
  domainId?: number, processId?: number): { path: string; label: string; note: string }[] {
  if (!verifiedProcess || !positive(processId ?? null)) return [];
  const metricFilter = new URLSearchParams({ processId: String(processId) });
  if (positive(domainId ?? null)) metricFilter.set('domainId', String(domainId));
  return [
    { path: '/modeling/mainline', label: '查看原建模主线',
      note: '仅查看已有模型主线；不表示已创建或发布模型' },
    { path: `/metric/manage?${metricFilter.toString()}`, label: '进入原指标设计',
      note: '仅携带正式业务过程筛选；不表示已有指标或已发布、被消费' },
  ];
}
