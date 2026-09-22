import type {
  LineageAsset,
  LineageAssetType,
  LineageDirection,
  LineageGraph,
  LineageRelation,
} from '@/services/data-analysis';

const HORIZONTAL_GAP = 330;
const VERTICAL_GAP = 108;

export interface PositionedLineageAsset {
  asset: LineageAsset;
  level: number;
  position: { x: number; y: number };
}

export interface LineageView {
  nodes: PositionedLineageAsset[];
  relations: LineageRelation[];
}

export interface ImpactSummary {
  total: number;
  byType: Record<LineageAssetType, number>;
  assetIds: Set<string>;
}

const emptyTypeCounts = (): Record<LineageAssetType, number> => ({
  TABLE: 0,
  COLUMN: 0,
  SQL_TASK: 0,
  DATASET: 0,
  DATASET_FIELD: 0,
  CHART: 0,
  DASHBOARD: 0,
  METRIC: 0,
});

const distances = (
  rootId: string,
  relations: LineageRelation[],
  direction: 'UPSTREAM' | 'DOWNSTREAM',
) => {
  const result = new Map<string, number>([[rootId, 0]]);
  const queue = [rootId];
  let cursor = 0;

  while (cursor < queue.length) {
    const current = queue[cursor++];
    const currentDistance = result.get(current) || 0;
    relations.forEach((relation) => {
      const matches = direction === 'UPSTREAM'
        ? relation.targetAssetId === current
        : relation.sourceAssetId === current;
      if (!matches) return;
      const next = direction === 'UPSTREAM'
        ? relation.sourceAssetId
        : relation.targetAssetId;
      if (result.has(next)) return;
      result.set(next, currentDistance + 1);
      queue.push(next);
    });
  }
  return result;
};

export const lineageLevels = (graph: LineageGraph) => {
  const upstream = distances(graph.root.id, graph.relations, 'UPSTREAM');
  const downstream = distances(graph.root.id, graph.relations, 'DOWNSTREAM');
  const levels = new Map<string, number>([[graph.root.id, 0]]);

  graph.nodes.forEach((asset) => {
    if (asset.id === graph.root.id) return;
    const upstreamDistance = upstream.get(asset.id);
    const downstreamDistance = downstream.get(asset.id);
    if (upstreamDistance == null && downstreamDistance == null) return;
    if (upstreamDistance != null && downstreamDistance != null) {
      levels.set(
        asset.id,
        upstreamDistance <= downstreamDistance ? -upstreamDistance : downstreamDistance,
      );
      return;
    }
    if (upstreamDistance != null) levels.set(asset.id, -upstreamDistance);
    else if (downstreamDistance != null) levels.set(asset.id, downstreamDistance);
  });
  return levels;
};

export const buildLineageView = (
  graph: LineageGraph,
  direction: LineageDirection,
  visibleTypes: ReadonlySet<LineageAssetType>,
): LineageView => {
  const levels = lineageLevels(graph);
  const visibleAssets = graph.nodes.filter((asset) => {
    if (asset.id === graph.root.id) return true;
    const level = levels.get(asset.id);
    if (level == null) return false;
    if (direction === 'UPSTREAM' && level >= 0) return false;
    if (direction === 'DOWNSTREAM' && level <= 0) return false;
    return visibleTypes.has(asset.assetType);
  });

  const groups = new Map<number, LineageAsset[]>();
  visibleAssets.forEach((asset) => {
    const level = levels.get(asset.id) || 0;
    const group = groups.get(level) || [];
    group.push(asset);
    groups.set(level, group);
  });

  const positioned: PositionedLineageAsset[] = [];
  [...groups.entries()]
    .sort(([left], [right]) => left - right)
    .forEach(([level, assets]) => {
      assets
        .sort((left, right) => {
          const typeCompare = left.assetType.localeCompare(right.assetType);
          return typeCompare || left.name.localeCompare(right.name, 'zh-CN');
        })
        .forEach((asset, index) => {
          positioned.push({
            asset,
            level,
            position: {
              x: level * HORIZONTAL_GAP,
              y: (index - (assets.length - 1) / 2) * VERTICAL_GAP,
            },
          });
        });
    });

  const visibleIds = new Set(positioned.map((item) => item.asset.id));
  return {
    nodes: positioned,
    relations: graph.relations.filter(
      (relation) => visibleIds.has(relation.sourceAssetId)
        && visibleIds.has(relation.targetAssetId),
    ),
  };
};

export const downstreamImpact = (graph: LineageGraph): ImpactSummary => {
  const reachable = distances(graph.root.id, graph.relations, 'DOWNSTREAM');
  reachable.delete(graph.root.id);
  const byType = emptyTypeCounts();
  const assetIds = new Set<string>();
  graph.nodes.forEach((asset) => {
    if (!reachable.has(asset.id)) return;
    assetIds.add(asset.id);
    byType[asset.assetType] += 1;
  });
  return { total: assetIds.size, byType, assetIds };
};
