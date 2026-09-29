import {
  buildDevelopmentTreeData,
  clampDevelopmentTreeWidth,
  developmentDirectoryKey,
  developmentIdFromTreeKey,
  developmentNodeKey,
  developmentNodeTypeForAction,
  filterDevelopmentTreeData,
  parseDevelopmentTreeWidth,
} from './utils';

const directories = [
  {
    id: 'dir-1',
    name: '订单域',
    path: '/订单域',
  },
  {
    id: 'dir-2',
    parentId: 'dir-1',
    name: '明细层',
    path: '/订单域/明细层',
  },
];

const nodes = [
  {
    id: 'node-root',
    name: '公共脚本',
    type: 'SHELL' as const,
    configured: true,
  },
  {
    id: 'node-orders',
    directoryId: 'dir-2',
    name: '订单明细',
    type: 'SQL' as const,
    configured: true,
    updatedBy: '锐',
    updateTime: '2026-08-26 10:00:00',
    pendingPublish: true,
  },
];

describe('data-development page utilities', () => {
  it('builds stable directory and node keys', () => {
    expect(developmentDirectoryKey('100')).toBe('directory:100');
    expect(developmentNodeKey('200')).toBe('node:200');
    expect(developmentIdFromTreeKey('node:200', 'node:')).toBe('200');
    expect(
      developmentIdFromTreeKey('directory:100', 'node:'),
    ).toBeUndefined();
  });

  it('clamps and restores the tree panel width', () => {
    expect(clampDevelopmentTreeWidth(100)).toBe(220);
    expect(clampDevelopmentTreeWidth(360)).toBe(360);
    expect(clampDevelopmentTreeWidth(900)).toBe(440);
    expect(parseDevelopmentTreeWidth('380')).toBe(380);
    expect(parseDevelopmentTreeWidth('invalid')).toBe(300);
  });

  it('builds a hierarchical tree and carries node metadata', () => {
    const tree = buildDevelopmentTreeData(directories, nodes);
    const directory = tree.find((item) => item.key === 'directory:dir-1');
    const nestedDirectory = directory?.children?.find(
      (item) => item.key === 'directory:dir-2',
    );
    const nestedNode = nestedDirectory?.children?.find(
      (item) => item.key === 'node:node-orders',
    );

    expect(tree.some((item) => item.key === 'node:node-root')).toBe(true);
    expect(nestedNode).toMatchObject({
      title: '订单明细',
      resourcePath: '/订单域/明细层/订单明细',
      taskType: 'SQL',
      updatedBy: '锐',
      pendingPublish: true,
    });
  });

  it('keeps parent directories when only a child matches', () => {
    const tree = buildDevelopmentTreeData(directories, nodes);
    const filtered = filterDevelopmentTreeData(tree, '订单明细');

    expect(filtered).toHaveLength(1);
    expect(filtered[0].key).toBe('directory:dir-1');
    expect(filtered[0].children?.[0].key).toBe('directory:dir-2');
    expect(filtered[0].children?.[0].children?.[0].key).toBe(
      'node:node-orders',
    );
  });

  it('maps create actions to node types', () => {
    expect(developmentNodeTypeForAction('create-sql')).toBe('SQL');
    expect(developmentNodeTypeForAction('create-dataset')).toBe('DATASET');
    expect(developmentNodeTypeForAction('rename')).toBeUndefined();
  });
});
