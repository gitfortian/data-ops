import { available, buildProcessJourneyReview, unavailable } from './processJourneyReview';
import type { SemanticProcessRecord } from '@/services/semantic/types';

const process = {
  id: 7, code: 'place_order', name: '下单', grain: '每单一行',
} as SemanticProcessRecord;
const physicalCoverage = { processId: 7, processCode: 'place_order',
  processName: '下单', totalModels: 1, layers: [] };
const input = () => ({
  process,
  sources: available([{ id: 1, processId: 7, datasourceId: 4,
    sourceTable: 'trade_order', tableRole: 'MAIN' as const }]),
  standards: available([{ id: 10, code: 'order_id', name: '订单号',
    role: 'PROCESS' as const, status: 'ENABLED' as const }]),
  logical: available([{ id: 21, projectId: 1, processId: 7, domainId: 1,
    draftRevision: 1, code: 'order', name: '订单', status: 'DRAFT' as const },
  { id: 22, projectId: 1, processId: 8, domainId: 1,
    draftRevision: 1, code: 'payment', name: '支付', status: 'DRAFT' as const }]),
  physical: available(physicalCoverage),
});
const status = (result: ReturnType<typeof buildProcessJourneyReview>, key: string) =>
  result.steps.find(s => s.key === key);

describe('F-040 process journey read-only projection', () => {
  it('reports five recorded facts without promoting them to delivery or approval', () => {
    const result = buildProcessJourneyReview(input());
    expect(result.steps).toHaveLength(5);
    expect(result.steps.every(step => step.status === 'RECORDED')).toBe(true);
    expect(status(result, 'logical')?.summary).toContain('1 份');
    expect(status(result, 'physical')?.summary).toContain('不等于部署');
    expect(result.explanation).toContain('不创建第二套进度记录');
  });

  it('missing sources and fields are distinct from inaccessible owner facts', () => {
    const sourceMissing = buildProcessJourneyReview({
      ...input(), sources: available([]), standards: available([]),
      logical: available([]), physical: available({ ...physicalCoverage, totalModels: 0 }),
    });
    expect(status(sourceMissing, 'source')?.status).toBe('NOT_STARTED');
    expect(status(sourceMissing, 'standard')?.status).toBe('NOT_STARTED');
    expect(status(sourceMissing, 'logical')?.status).toBe('NOT_STARTED');

    const unknown = buildProcessJourneyReview({
      ...input(), sources: unavailable(), standards: unavailable(),
      logical: unavailable(), physical: unavailable(),
    });
    expect(unknown.unknownCount).toBe(4);
    expect(status(unknown, 'source')?.status).toBe('UNAVAILABLE');
    expect(status(unknown, 'physical')?.summary).toContain('不可访问');
  });

  it('grain/MAIN cardinality/disabled standard are review work, never automatic inference', () => {
    const result = buildProcessJourneyReview({
      ...input(), process: { ...process, grain: '   ' },
      sources: available([{ id: 4, processId: 7, datasourceId: 2,
        sourceTable: 'order_detail', tableRole: 'DETAIL' }]),
      standards: available([{ id: 11, code: 'amount', name: '金额',
        role: 'METRIC', status: 'DISABLED' }]),
    });
    expect(status(result, 'grain')?.status).toBe('NEEDS_ATTENTION');
    expect(status(result, 'source')?.summary).toContain('MAIN 数量为 0');
    expect(status(result, 'standard')?.status).toBe('NEEDS_ATTENTION');
  });

  it('mismatched physical scope must not appear as zero or complete', () => {
    const result = buildProcessJourneyReview({
      ...input(), physical: available({ ...physicalCoverage, processId: 88 }),
    });
    expect(status(result, 'physical')?.status).toBe('UNAVAILABLE');
    expect(result.unknownCount).toBe(1);
  });
  it('identifies exact source and standard refs without guessing ownership or approval', () => {
    const row = {
      ...input(),
      sources: available([
        { id: 41, processId: 7, datasourceId: 3, sourceTable: 'orders', tableRole: 'MAIN' as const },
        { id: 42, processId: 7, datasourceId: 3, sourceTable: 'order_detail', tableRole: 'MAIN' as const },
      ]),
      standards: available([
        { id: 23, code: 'amount', name: '金额', role: 'METRIC' as const, status: 'DISABLED' as const },
        { id: 24, code: 'order_id', name: '订单号', role: 'PROCESS' as const, status: 'ENABLED' as const },
      ]),
    };
    const result = buildProcessJourneyReview(row);
    expect(status(result, 'source')?.evidence?.map(x => x.id)).toEqual(['41', '42']);
    expect(status(result, 'source')?.evidence?.every(x => x.status === 'REVIEW')).toBe(true);
    expect(status(result, 'standard')?.evidence?.find(x => x.id === '23')?.status).toBe('REVIEW');
    expect(status(result, 'standard')?.evidence?.find(x => x.id === '24')?.status).toBe('OK');
    expect(status(result, 'logical')?.evidence?.map(x => x.id)).toEqual(['21']);
    expect(status(result, 'physical')?.evidence).toEqual([]);
  });

  it('an unavailable owner does not fabricate object-level evidence', () => {
    const result = buildProcessJourneyReview({
      ...input(), sources: unavailable(), standards: unavailable(), logical: unavailable(),
      physical: unavailable(),
    });
    expect(result.steps.every(x => !x.evidence?.length || x.key === 'grain')).toBe(true);
  });

});
