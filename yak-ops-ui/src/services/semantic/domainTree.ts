import type { SemanticDomainNode } from './types';

/**
 * 业务域 TreeSelect 数据：只有顶层真业务域可选，子域仅作层级展示不可选。
 * 子节点承载业务过程的归属，若可选会被误填进“业务域”字段（F-2/F-3 根因）。
 */
export const toDomainTreeData = (nodes: SemanticDomainNode[], isRootLevel = true): any[] =>
  (nodes ?? []).map((node) => ({
    value: node.id,
    title: node.name,
    selectable: isRootLevel,
    children: node.children?.length ? toDomainTreeData(node.children, false) : undefined,
  }));

/**
 * 选中域 → 该域及其全部子孙节点的 id 集合，用于推导“域下全部业务过程”。
 * 树中找不到的 id（如存量数据指向已删节点）回退为仅自身，保持旧精确匹配行为。
 */
export const collectDomainSubtreeIds = (nodes: SemanticDomainNode[], domainId: number): Set<number> => {
  const subtreeOf = (node: SemanticDomainNode): Set<number> => {
    const ids = new Set<number>([node.id]);
    node.children?.forEach((child) => subtreeOf(child).forEach((id) => ids.add(id)));
    return ids;
  };
  const find = (list: SemanticDomainNode[]): SemanticDomainNode | undefined => {
    for (const node of list) {
      if (node.id === domainId) return node;
      const hit = find(node.children ?? []);
      if (hit) return hit;
    }
    return undefined;
  };
  const node = find(nodes ?? []);
  return node ? subtreeOf(node) : new Set([domainId]);
};
