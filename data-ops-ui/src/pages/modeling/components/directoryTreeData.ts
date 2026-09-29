import type { ModelingDirectoryRecord } from '@/services/modeling/types';

/** 目录 TreeSelect/Tree 节点的显式类型（避免自引用推断）。 */
export interface DirectoryOption {
  title: string;
  value: number;
  disabled?: boolean;
  children?: DirectoryOption[];
}

/**
 * 将目录扁平列表构建为树。excludeId 及其子孙会被标记 disabled
 * （目录移动场景防环）。无父目录的节点为根。
 */
export const buildDirectoryOptions = (
  directories: ModelingDirectoryRecord[],
  excludeId?: number,
): DirectoryOption[] => {
  const nodes = new Map<number, DirectoryOption & { parentId?: number | null }>();
  directories.forEach((directory) => {
    nodes.set(directory.id!, {
      title: directory.name!,
      value: directory.id!,
      parentId: directory.parentId,
    });
  });
  const excluded = new Set<number>();
  if (excludeId) {
    const markExcluded = (id: number) => {
      excluded.add(id);
      directories.filter((item) => item.parentId === id).forEach((child) => markExcluded(child.id!));
    };
    markExcluded(excludeId);
  }
  const roots: DirectoryOption[] = [];
  directories.forEach((directory) => {
    const node = nodes.get(directory.id!)!;
    if (excluded.has(node.value)) node.disabled = true;
    const parent = node.parentId ? nodes.get(node.parentId) : undefined;
    if (parent) {
      parent.children = [...(parent.children || []), node];
    } else {
      roots.push(node);
    }
  });
  return roots;
};
