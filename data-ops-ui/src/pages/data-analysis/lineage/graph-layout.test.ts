import { buildLineageView, downstreamImpact, lineageLevels } from './graph-layout';
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
});
