import type { TemplateFolderView } from '@/services/data-quality';

export interface TemplateFolderRow extends TemplateFolderView {
  depth: number;
}

export const flattenTemplateFolders = (folders: TemplateFolderView[]) => {
  const groups = new Map<number | undefined, TemplateFolderView[]>();
  folders.forEach((folder) => {
    const values = groups.get(folder.parentId) || [];
    values.push(folder);
    groups.set(folder.parentId, values);
  });
  groups.forEach((values) =>
    values.sort(
      (a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name),
    ),
  );

  const result: TemplateFolderRow[] = [];
  const walk = (parentId: number | undefined, depth: number) => {
    (groups.get(parentId) || []).forEach((folder) => {
      result.push({ ...folder, depth });
      walk(folder.id, depth + 1);
    });
  };
  walk(undefined, 0);
  return result;
};
