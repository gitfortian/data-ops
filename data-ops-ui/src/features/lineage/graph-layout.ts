import type {
  LineageAsset,
  LineageAssetType,
  LineageDirection,
  LineageGraph,
  LineageRelation,
} from '@/services/data-analysis';

const HORIZONTAL_GAP = 330;
const VERTICAL_GAP = 108;
/** 节点卡片高 78px;行距下限保证不重叠并保留呼吸空间。 */
const MIN_VERTICAL_GAP = 92;
/** 单层内容高度上限,超出按行距下限压缩,避免超宽扇出把整图撑出画布(M3-01/T6-02)。 */
const MAX_LEVEL_HEIGHT = 1240;

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
  DATABASE_SERVICE: 0,
  DATABASE: 0,
  DOMAIN: 0,
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

/** 单层节点过多时压缩行距(下限防重叠),控制整图高度让超宽扇出仍可整图呈现。 */
export const levelVerticalGap = (count: number) => {
  if (count <= 1) return VERTICAL_GAP;
  const naturalHeight = (count - 1) * VERTICAL_GAP;
  if (naturalHeight <= MAX_LEVEL_HEIGHT) return VERTICAL_GAP;
  return Math.max(MIN_VERTICAL_GAP, MAX_LEVEL_HEIGHT / (count - 1));
};

/**
 * 同层节点按相邻层邻居的平均行号重排(两轮扫描)。
 * 初始按类型/名称排序与连接关系无关,深层图会出现长边交叉;重心扫描把
 * 连向同一父节点的子节点聚到一起,显著减少交叉(M3-01/T6-02 "连线密集")。
 */
const reduceCrossings = (
  groups: Map<number, LineageAsset[]>,
  relations: LineageRelation[],
): Map<number, LineageAsset[]> => {
  const adjacency = new Map<string, Set<string>>();
  relations.forEach((relation) => {
    if (!adjacency.has(relation.sourceAssetId)) {
      adjacency.set(relation.sourceAssetId, new Set());
    }
    if (!adjacency.has(relation.targetAssetId)) {
      adjacency.set(relation.targetAssetId, new Set());
    }
    adjacency.get(relation.sourceAssetId)!.add(relation.targetAssetId);
    adjacency.get(relation.targetAssetId)!.add(relation.sourceAssetId);
  });

  const rows = new Map<string, number>();
  groups.forEach((assets) => assets.forEach((asset, index) => rows.set(asset.id, index)));
  const ordered = new Map(groups);

  for (let pass = 0; pass < 2; pass += 1) {
    [...ordered.keys()].sort((left, right) => left - right).forEach((level) => {
      const assets = ordered.get(level)!;
      const scored = assets.map((asset, index) => {
        const neighborRows = [...(adjacency.get(asset.id) ?? [])]
          .map((neighborId) => rows.get(neighborId))
          .filter((row): row is number => row != null);
        const score = neighborRows.length
          ? neighborRows.reduce((sum, row) => sum + row, 0) / neighborRows.length
          : index;
        return { asset, index, score };
      });
      scored.sort((left, right) => left.score - right.score || left.index - right.index);
      ordered.set(level, scored.map((item) => item.asset));
      scored.forEach((item, index) => rows.set(item.asset.id, index));
    });
  }
  return ordered;
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
  // 初始序:类型/名称;reduceCrossings 再按连接关系收敛。
  groups.forEach((assets) => {
    assets.sort((left, right) => {
      const typeCompare = left.assetType.localeCompare(right.assetType);
      return typeCompare || left.name.localeCompare(right.name, 'zh-CN');
    });
  });

  const positioned: PositionedLineageAsset[] = [];
  [...reduceCrossings(groups, graph.relations).entries()]
    .sort(([left], [right]) => left - right)
    .forEach(([level, assets]) => {
      assets
        .forEach((asset, index) => {
          positioned.push({
            asset,
            level,
            position: {
              x: level * HORIZONTAL_GAP,
              y: (index - (assets.length - 1) / 2) * levelVerticalGap(assets.length),
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
