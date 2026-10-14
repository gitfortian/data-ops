import {
  creationNextStep, requiresLogicalBasis, rootDomainForProcess, verifiedProcessInDomain,
} from './modelCreationScope';
import type { SemanticDomainNode, SemanticProcessRecord } from '@/services/semantic/types';
const tree: SemanticDomainNode[] = [
  { id: 1, code: 'trade', name: '交易', children: [
    { id: 2, code: 'orders', name: '订单', children: [
      { id: 3, code: 'details', name: '订单明细', children: [] },
    ] },
  ] },
  { id: 4, code: 'finance', name: '财务', children: [] },
];
const processes: SemanticProcessRecord[] = [
  { id: 71, code: 'order_create', name: '下单', domainId: 2, grain: '每笔订单', bizType: 'FACT' },
  { id: 72, code: 'item_create', name: '订单明细', domainId: 3, grain: '每笔订单明细', bizType: 'FACT' },
  { id: 80, code: 'invoice', name: '开票', domainId: 4, bizType: 'FACT' },
];

it('prefills parent business domain from nested process owner without changing the process or its grain', () => {
  expect(rootDomainForProcess(tree, 2)).toBe(1);
  expect(rootDomainForProcess(tree, 3)).toBe(1);
  expect(verifiedProcessInDomain(tree, processes, 1, 71)).toEqual(processes[0]);
  expect(verifiedProcessInDomain(tree, processes, 1, 72)?.grain).toBe('每笔订单明细');
});

it('rejects cross-domain, missing and orphaned process identities even if they share a name', () => {
  expect(verifiedProcessInDomain(tree, processes, 1, 80)).toBeUndefined();
  expect(verifiedProcessInDomain(tree, processes, 2, 71)).toBeUndefined(); // selectable roots only
  expect(verifiedProcessInDomain([], processes, 1, 71)).toBeUndefined();
  expect(verifiedProcessInDomain(tree, [], 1, 71)).toBeUndefined();
  expect(verifiedProcessInDomain(tree, processes, 1, 999)).toBeUndefined();
  expect(rootDomainForProcess(tree, 999)).toBeUndefined();
});

it('keeps the special source-to-ODS branch without pretending all other layers have an approved mapping', () => {
  expect(requiresLogicalBasis('ODS')).toBe(false);
  expect(requiresLogicalBasis('ods')).toBe(false);
  expect(requiresLogicalBasis('DWD')).toBe(true);
  expect(requiresLogicalBasis('DIM')).toBe(true);
  expect(requiresLogicalBasis('DWS')).toBe(true);
  expect(creationNextStep('ODS')).toMatch(/不会自动读取或绑定源表/);
  expect(creationNextStep('DWD')).toMatch(/不会形成正式映射/);
});
