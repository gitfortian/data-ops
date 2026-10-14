import type { SemanticDomainNode, SemanticProcessRecord } from '@/services/semantic/types';
import { collectDomainSubtreeIds } from '@/services/semantic/domainTree';

/** Semantic Owner tree roots are the only selectable business domains. */
export function rootDomainForProcess(tree: SemanticDomainNode[], processDomainId: number): number | undefined {
  const contains = (node: SemanticDomainNode): boolean =>
    node.id === processDomainId || (node.children ?? []).some(contains);
  return tree.find(contains)?.id;
}

/** Fail closed: a process in a stale/partial option list isn't a valid new model scope. */
export function verifiedProcessInDomain(
  tree: SemanticDomainNode[],
  processes: SemanticProcessRecord[],
  domainId: number | undefined,
  processId: number | undefined,
): SemanticProcessRecord | undefined {
  if (domainId == null || processId == null || !tree.some(root => root.id === domainId))
    return undefined;
  const found = processes.find(p => p.id === processId);
  return found && collectDomainSubtreeIds(tree, domainId).has(found.domainId) ? found : undefined;
}

export const requiresLogicalBasis = (layer?: string): boolean =>
  Boolean(layer && layer.toUpperCase() !== 'ODS');

/** A type choice may not have a native source-to-ODS edge yet; don't call it a mapping. */
export const creationNextStep = (layer?: string) => layer?.toUpperCase() === 'ODS'
  ? '创建后进入物理表结构编辑；此手工入口不会自动读取或绑定源表。需要源表精确映射时，请从数据源工作台进入。'
  : '创建后进入该物理模型的表结构工作区；逻辑冻结版本审阅、来源映射和标准覆盖均须单独核对，创建本身不会形成正式映射或执行任务。';
