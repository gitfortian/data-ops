import { buildLineageView, downstreamImpact, levelVerticalGap, lineageLevels } from './graph-layout';
import type { LineageAsset, LineageGraph, LineageRelation } from './types';

const asset = (id: string, assetType: LineageAsset['assetType']): LineageAsset => ({
  id,
  assetKey: `${assetType.toLowerCase()}:${id}`,
  assetType,
  name: `${assetType}-${id}`,
});

const relation = (
  id: string,
  sourceAssetId: string,
  targetAssetId: string,
): LineageRelation => ({
  id,
  sourceAssetId,
  targetAssetId,
  relationType: 'DERIVES_FROM',
});

const graph = (): LineageGraph => ({
  root: asset('3', 'DATASET'),
  direction: 'BOTH',
  depth: 3,
  nodes: [
    asset('1', 'TABLE'),
    asset('2', 'SQL_TASK'),
    asset('3', 'DATASET'),
    asset('4', 'CHART'),
    asset('5', 'DASHBOARD'),
  ],
  relations: [
    relation('r1', '1', '2'),
    relation('r2', '2', '3'),
    relation('r3', '3', '4'),
    relation('r4', '4', '5'),
  ],
});

describe('lineage graph layout', () => {
  test('places upstream left and downstream right by hop', () => {
    const levels = lineageLevels(graph());
    expect(levels.get('1')).toBe(-2);
    expect(levels.get('2')).toBe(-1);
    expect(levels.get('3')).toBe(0);
    expect(levels.get('4')).toBe(1);
    expect(levels.get('5')).toBe(2);
  });

  test('direction and type filters keep the root and matching side', () => {
    const visible = new Set<LineageAsset['assetType']>(['TABLE', 'SQL_TASK', 'DATASET']);
    const view = buildLineageView(graph(), 'UPSTREAM', visible);
    expect(view.nodes.map((node) => node.asset.id).sort()).toEqual(['1', '2', '3']);
    expect(view.nodes.find((node) => node.asset.id === '1')?.position.x).toBeLessThan(0);
    expect(view.relations).toHaveLength(2);
  });

  test('impact counts only downstream reachable assets', () => {
    const impact = downstreamImpact(graph());
    expect(impact.total).toBe(2);
    expect(impact.byType.CHART).toBe(1);
    expect(impact.byType.DASHBOARD).toBe(1);
    expect(impact.byType.TABLE).toBe(0);
  });

  test('compresses crowded levels so a wide fan-out stays inside the canvas', () => {
    expect(levelVerticalGap(1)).toBe(108);
    expect(levelVerticalGap(10)).toBe(108);
    // 超过单层高度上限后压到下限,保证卡片(78px)不重叠
    expect(levelVerticalGap(18)).toBe(92);

    const star = (): LineageGraph => {
      const nodes: LineageAsset[] = [asset('root', 'DATASET')];
      const relations: LineageRelation[] = [];
      for (let index = 1; index <= 20; index += 1) {
        nodes.push(asset(`d${index}`, 'TABLE'));
        relations.push(relation(`r${index}`, 'root', `d${index}`));
      }
      return { root: asset('root', 'DATASET'), direction: 'BOTH', depth: 3, nodes, relations };
    };

    const view = buildLineageView(star(), 'DOWNSTREAM', new Set(['TABLE']));
    // 只看 level 1 的下游节点(root 恒在 y=0,会落在排序后的中间)
    const ys = view.nodes
      .filter((node) => node.level === 1)
      .map((node) => node.position.y)
      .sort((left, right) => left - right);
    const gaps = ys.slice(1).map((y, index) => y - ys[index]);
    // 行距压缩到下限,不再使用 108 的自然行距
    expect(new Set(gaps).size).toBe(1);
    expect(gaps[0]).toBe(92);
    // 全部下游仍同层同列
    expect(
      new Set(
        view.nodes.filter((node) => node.level === 1).map((node) => node.position.x),
      ).size,
    ).toBe(1);
  });

  test('barycenter sweep groups children by parent to avoid long-edge crossings', () => {
    const nodes: LineageAsset[] = [
      asset('r', 'DATASET'),
      asset('alpha', 'TABLE'),
      asset('beta', 'TABLE'),
      asset('z1', 'TABLE'),
      asset('z2', 'TABLE'),
      asset('a1', 'TABLE'),
      asset('a2', 'TABLE'),
    ];
    const relations: LineageRelation[] = [
      relation('r1', 'r', 'alpha'),
      relation('r2', 'r', 'beta'),
      // 名称序会让 beta 的子节点(a1/a2)排在 alpha 的子节点(z1/z2)之前,与父级行序相反
      relation('r3', 'alpha', 'z1'),
      relation('r4', 'alpha', 'z2'),
      relation('r5', 'beta', 'a1'),
      relation('r6', 'beta', 'a2'),
    ];
    const view = buildLineageView(
      { root: asset('r', 'DATASET'), direction: 'BOTH', depth: 3, nodes, relations },
      'BOTH',
      new Set(['TABLE', 'DATASET']),
    );

    const byLevel = (level: number) =>
      view.nodes
        .filter((node) => node.level === level)
        .sort((left, right) => left.position.y - right.position.y)
        .map((node) => node.asset.id);

    // 父级顺序与子级顺序对齐:同父子节点连续,长边不再交叉
    expect(byLevel(1)).toEqual(['beta', 'alpha']);
    expect(byLevel(2)).toEqual(['a1', 'a2', 'z1', 'z2']);
  });
});
