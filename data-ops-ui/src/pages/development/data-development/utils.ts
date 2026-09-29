import { normalizeDevelopmentSqlDialect } from '@/services/data-development';
import {
  DATA_DEVELOPMENT_DEFAULT_TREE_WIDTH,
  DATA_DEVELOPMENT_MAX_TREE_WIDTH,
  DATA_DEVELOPMENT_MIN_TREE_WIDTH,
} from './constants';
import {
  getDevelopmentResourceSqlDialect,
  getDevelopmentSqlDialectLabel,
} from './sqlDatabaseProfiles';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNodeType,
  DevelopmentResourceNode,
  DevelopmentSqlDialect,
  DevelopmentTreeAction,
  DevelopmentTreeNode,
  DevelopmentTreeNodeKey,
} from './types';

export const developmentDirectoryKey = (
  directoryId: DevelopmentId,
): DevelopmentTreeNodeKey => `directory:${directoryId}`;

export const developmentNodeKey = (
  nodeId: DevelopmentId,
): DevelopmentTreeNodeKey => `node:${nodeId}`;

export const developmentIdFromTreeKey = (
  key: string | undefined,
  prefix: 'directory:' | 'node:',
): DevelopmentId | undefined => {
  if (!key?.startsWith(prefix)) return undefined;
  const value = key.substring(prefix.length).trim();
  return value || undefined;
};

export const clampDevelopmentTreeWidth = (value: number) =>
  Math.min(
    DATA_DEVELOPMENT_MAX_TREE_WIDTH,
    Math.max(DATA_DEVELOPMENT_MIN_TREE_WIDTH, value),
  );

export const parseDevelopmentTreeWidth = (storedValue: string | null) => {
  const value = Number(storedValue);
  return Number.isFinite(value) && value > 0
    ? clampDevelopmentTreeWidth(value)
    : DATA_DEVELOPMENT_DEFAULT_TREE_WIDTH;
};

export const developmentNodeTypeForAction = (
  action: DevelopmentTreeAction,
): DevelopmentNodeType | undefined => {
  if (action === 'create-sql' || action.startsWith('create-sql-dialect:')) return 'SQL';
  if (action === 'create-shell') return 'SHELL';
  if (action === 'create-python') return 'PYTHON';
  if (action === 'create-java') return 'JAVA';
  if (action === 'create-dataset') return 'DATASET';
  if (action === 'create-data-service') return 'DATA_SERVICE';
  return undefined;
};

export const developmentSqlDialectForAction = (
  action: DevelopmentTreeAction,
): DevelopmentSqlDialect | undefined => {
  const prefix = 'create-sql-dialect:';
  if (!action.startsWith(prefix)) return undefined;
  const dialect = normalizeDevelopmentSqlDialect(action.substring(prefix.length));
  return dialect === 'GENERIC' ? undefined : dialect;
};

export const buildDevelopmentTreeData = (
  directories: DevelopmentDirectory[],
  nodes: DevelopmentResourceNode[],
): DevelopmentTreeNode[] => {
  const directoryPathMap = new Map(
    directories.map((directory) => [directory.id, directory.path]),
  );

  const resourceNodes = (
    directoryId?: DevelopmentId,
  ): DevelopmentTreeNode[] =>
    nodes
      .filter((node) => (node.directoryId || undefined) === directoryId)
      .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
      .map((node) => {
        const parentPath = directoryId
          ? directoryPathMap.get(directoryId) || ''
          : '';
        const sqlDialect =
          node.type === 'SQL' ? getDevelopmentResourceSqlDialect(node) : undefined;
        const displayType = sqlDialect
          ? getDevelopmentSqlDialectLabel(sqlDialect)
          : node.type;
        return {
          key: developmentNodeKey(node.id),
          title: node.name,
          nodeType: 'node',
          resourceId: node.id,
          resourcePath: `${parentPath}/${node.name}`,
          taskType: node.type,
          sqlDialect,
          searchText: `${node.name} ${node.type} ${displayType} ${node.id}`,
          updatedBy: node.updatedBy,
          updateTime: node.updateTime,
          pendingPublish: node.pendingPublish,
          isLeaf: true,
        };
      });

  const directoryNodes = (
    parentId?: DevelopmentId,
    ancestors = new Set<DevelopmentId>(),
  ): DevelopmentTreeNode[] =>
    directories
      .filter((directory) => (directory.parentId || undefined) === parentId)
      .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
      .flatMap((directory) => {
        if (ancestors.has(directory.id)) return [];
        const nextAncestors = new Set(ancestors);
        nextAncestors.add(directory.id);
        return [
          {
            key: developmentDirectoryKey(directory.id),
            title: directory.name,
            nodeType: 'directory' as const,
            resourceId: directory.id,
            resourcePath: directory.path,
            searchText: `${directory.name} ${directory.path || ''}`,
            children: [
              ...directoryNodes(directory.id, nextAncestors),
              ...resourceNodes(directory.id),
            ],
          },
        ];
      });

  return [...directoryNodes(), ...resourceNodes()];
};

export const filterDevelopmentTreeData = (
  tree: DevelopmentTreeNode[],
  keyword: string,
): DevelopmentTreeNode[] => {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) return tree;

  const filterNodes = (
    values: DevelopmentTreeNode[],
  ): DevelopmentTreeNode[] =>
    values.flatMap((node) => {
      const children = node.children ? filterNodes(node.children) : [];
      const text = `${node.title} ${node.searchText || ''}`.toLowerCase();
      if (text.includes(normalized)) {
        return [{ ...node, children: node.children }];
      }
      return children.length ? [{ ...node, children }] : [];
    });

  return filterNodes(tree);
};

export const copyDevelopmentText = async (value: string) => {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(value);
    return;
  }

  const textarea = document.createElement('textarea');
  textarea.value = value;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  const copied = document.execCommand('copy');
  document.body.removeChild(textarea);
  if (!copied) throw new Error('复制失败');
};
