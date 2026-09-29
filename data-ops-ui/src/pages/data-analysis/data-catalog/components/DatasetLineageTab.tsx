import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  LINEAGE_ASSET_TYPES,
  assetTypeLabel,
  getLineageGraph,
  searchLineageAssets,
  type CatalogDataset,
  type LineageAsset,
  type LineageAssetType,
  type LineageDirection,
  type LineageGraph,
} from '@/services/data-analysis';
import { BRAND_COLOR } from '@/styles/brand';
import { Segmented, Select, Spin, Tag } from 'antd';
import { ArrowUpRight, GitBranch, RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MarkerType,
  type Edge,
  type Node,
} from 'reactflow';
import 'reactflow/dist/style.css';
import LineageNode, { type LineageNodeData } from '@/features/lineage/LineageNode';
import { buildLineageView, lineageLevels } from '@/features/lineage/graph-layout';

const DEFAULT_DEPTH = 3;
const nodeTypes = { lineage: LineageNode };
const visibleTypes = new Set<LineageAssetType>(LINEAGE_ASSET_TYPES);

interface DatasetLineageTabProps {
  dataset: CatalogDataset;
}

const uniqueAssets = (values: LineageAsset[]) => {
  const result = new Map<string, LineageAsset>();
  values.forEach((asset) => result.set(asset.id, asset));
  return [...result.values()];
};

const pickDatasetAsset = (dataset: CatalogDataset, candidates: LineageAsset[]) => {
  const sourceMatched = candidates.find(
    (asset) => String(asset.sourceId || '') === dataset.id,
  );
  if (sourceMatched) return sourceMatched;

  const normalizedId = dataset.id.toLowerCase();
  const keyMatched = candidates.find((asset) => {
    const key = asset.assetKey.toLowerCase();
    return key === normalizedId
      || key.endsWith(`:${normalizedId}`)
      || key.endsWith(`/${normalizedId}`);
  });
  if (keyMatched) return keyMatched;
  return candidates.find((asset) => asset.name === dataset.name);
};

const resolveDatasetAsset = async (dataset: CatalogDataset) => {
  const byName = await searchLineageAssets({
    keyword: dataset.name,
    assetType: 'DATASET',
    limit: 50,
  });
  let candidates = uniqueAssets(byName);
  let matched = pickDatasetAsset(dataset, candidates);
  if (matched) return matched;

  const byId = await searchLineageAssets({
    keyword: dataset.id,
    assetType: 'DATASET',
    limit: 50,
  });
  candidates = uniqueAssets([...candidates, ...byId]);
  matched = pickDatasetAsset(dataset, candidates);
  return matched;
};

const countAssetTypes = (graph?: LineageGraph) => {
  const counts = new Map<LineageAssetType, number>();
  graph?.nodes.forEach((asset) => {
    if (asset.id === graph.root.id) return;
    counts.set(asset.assetType, (counts.get(asset.assetType) || 0) + 1);
  });
  return counts;
};

export default function DatasetLineageTab({ dataset }: DatasetLineageTabProps) {
  const [rootAsset, setRootAsset] = useState<LineageAsset>();
  const [graph, setGraph] = useState<LineageGraph>();
  const [isAssetLoading, setIsAssetLoading] = useState(false);
  const [isGraphLoading, setIsGraphLoading] = useState(false);
  const [error, setError] = useState('');
  const [depth, setDepth] = useState(DEFAULT_DEPTH);
  const [direction, setDirection] = useState<LineageDirection>('BOTH');
  const [refreshVersion, setRefreshVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setIsAssetLoading(true);
    setError('');
    setRootAsset(undefined);
    setGraph(undefined);

    void resolveDatasetAsset(dataset)
      .then((asset) => {
        if (cancelled) return;
        if (!asset) {
          setError('当前 Dataset 尚未生成血缘资产，请先确认血缘采集任务已完成。');
          return;
        }
        setRootAsset(asset);
      })
      .catch((requestError) => {
        if (!cancelled) {
          setError(requestError instanceof Error ? requestError.message : '查询 Dataset 血缘资产失败');
        }
      })
      .finally(() => {
        if (!cancelled) setIsAssetLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [dataset.id, dataset.name]);

  useEffect(() => {
    if (!rootAsset) return;
    let cancelled = false;
    setIsGraphLoading(true);
    setError('');

    void getLineageGraph(rootAsset.id, depth)
      .then((value) => {
        if (!cancelled) setGraph(value);
      })
      .catch((requestError) => {
        if (cancelled) return;
        setGraph(undefined);
        setError(requestError instanceof Error ? requestError.message : '加载 Dataset 血缘图失败');
      })
      .finally(() => {
        if (!cancelled) setIsGraphLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [depth, refreshVersion, rootAsset]);

  const view = useMemo(
    () => (graph ? buildLineageView(graph, direction, visibleTypes) : undefined),
    [direction, graph],
  );

  const flowNodes = useMemo<Array<Node<LineageNodeData>>>(() => (
    view?.nodes.map(({ asset, position }) => ({
      id: asset.id,
      type: 'lineage',
      position,
      draggable: false,
      selectable: false,
      data: { asset, root: asset.id === graph?.root.id },
    })) || []
  ), [graph?.root.id, view?.nodes]);

  const flowEdges = useMemo<Edge[]>(() => (
    view?.relations.map((relation) => ({
      id: relation.id,
      source: relation.sourceAssetId,
      target: relation.targetAssetId,
      type: 'smoothstep',
      markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 14,
        height: 14,
        color: '#b9bec6',
      },
      style: { stroke: '#c9cdd3', strokeWidth: 1.2 },
    })) || []
  ), [view?.relations]);

  const summary = useMemo(() => {
    if (!graph) return { upstream: 0, downstream: 0 };
    const levels = lineageLevels(graph);
    let upstream = 0;
    let downstream = 0;
    levels.forEach((level, assetId) => {
      if (assetId === graph.root.id) return;
      if (level < 0) upstream += 1;
      if (level > 0) downstream += 1;
    });
    return { upstream, downstream };
  }, [graph]);

  const counts = useMemo(() => countAssetTypes(graph), [graph]);
  const isLoading = isAssetLoading || isGraphLoading;
  const graphKey = `${rootAsset?.id || 'empty'}:${depth}:${direction}:${refreshVersion}`;

  return (
    <div className="flex h-full min-h-[480px] flex-col overflow-hidden border border-[#e4e7ec] bg-white">
      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-[#e4e7ec] bg-[#fafbfc] px-3 py-2">
        <div className="mr-2 flex items-center gap-2 text-[13px] font-medium text-[#344054]">
          <GitBranch size={14} />
          <span>Dataset 血缘</span>
        </div>
        <Segmented
          size="small"
          value={direction}
          options={[
            { label: '全部', value: 'BOTH' },
            { label: '上游', value: 'UPSTREAM' },
            { label: '下游', value: 'DOWNSTREAM' },
          ]}
          onChange={(value) => setDirection(value as LineageDirection)}
        />
        <span className="ml-1 text-[12px] text-[#8a8f99]">深度</span>
        <Select
          size="small"
          value={depth}
          className="w-[76px]"
          onChange={setDepth}
          options={[1, 2, 3, 4, 5].map((value) => ({ label: `${value} 层`, value }))}
        />
        <YakButton
          size="small"
          icon={<RefreshCw size={13} />}
          loading={isLoading}
          disabled={!rootAsset}
          onClick={() => setRefreshVersion((value) => value + 1)}
        >
          刷新
        </YakButton>
        <div className="ml-auto flex items-center gap-2 text-[12px] text-[#667085]">
          <span>上游 {summary.upstream}</span>
          <span className="h-3 w-px bg-[#dfe3e8]" />
          <span>下游 {summary.downstream}</span>
          {rootAsset ? (
            <YakButton
              type="link"
              size="small"
              className="px-1"
              href={`/data-analysis/lineage?assetKey=${encodeURIComponent(rootAsset.assetKey)}`}
              icon={<ArrowUpRight size={12} />}
            >
              完整血缘
            </YakButton>
          ) : null}
        </div>
      </div>

      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-[#f0f2f5] px-3 py-2 text-[12px] text-[#667085]">
        <span>上游：数据同步 / 来源表 / 数据开发</span>
        <span className="text-[#c1c5cc]">→</span>
        <Tag
          bordered={false}
          className="m-0 bg-[rgba(254,44,85,.06)] text-[11px]"
          style={{ color: BRAND_COLOR }}
        >
          当前 Dataset
        </Tag>
        <span className="text-[#c1c5cc]">→</span>
        <span>下游：图表 / 仪表盘</span>
        {LINEAGE_ASSET_TYPES.map((type) => {
          const count = counts.get(type) || 0;
          return count > 0 ? (
            <span key={type} className="ml-1 text-[#8a8f99]">
              {assetTypeLabel[type]} {count}
            </span>
          ) : null;
        })}
      </div>

      <div className="relative min-h-0 flex-1 bg-[#fcfcfd]">
        {isLoading && !graph ? (
          <div className="absolute inset-0 z-10 flex items-center justify-center bg-white/70">
            <Spin tip="正在加载血缘..." />
          </div>
        ) : error && !graph ? (
          <YakEmpty title="血缘暂不可用" description={error} className="h-full min-h-[420px]" />
        ) : graph && flowNodes.length ? (
          <>
            {/* absolute 包裹层:父级高度来自 min-height+flex-grow,ReactFlow 的 height:100% 需要确定高度才能生效 */}
            <div className="absolute inset-0">
              <ReactFlow
                key={graphKey}
                nodes={flowNodes}
                edges={flowEdges}
                nodeTypes={nodeTypes}
                fitView
                fitViewOptions={{ padding: 0.24, maxZoom: 1 }}
                minZoom={0.25}
                maxZoom={1.5}
                nodesConnectable={false}
                nodesDraggable={false}
                elementsSelectable={false}
                proOptions={{ hideAttribution: true }}
              >
                <Background color="#e8eaed" gap={18} size={1} />
                <Controls showInteractive={false} />
              </ReactFlow>
            </div>
            {graph.relations.length === 0 ? (
              <div className="pointer-events-none absolute bottom-4 left-1/2 -translate-x-1/2 rounded-[6px] border border-[#e4e7ec] bg-white px-3 py-1.5 text-[12px] text-[#667085] shadow-sm">
                已定位当前 Dataset，但暂未采集到上下游关系
              </div>
            ) : null}
          </>
        ) : (
          <div className="flex h-full min-h-[420px] items-center justify-center">
            <YakOpsEmpty
              width={180}
              height={120}
              title="暂无可展示的血缘关系"
              description="采集到上下游关系后会在这里展示。"
              showCaption
            />
          </div>
        )}

        {isGraphLoading && graph ? (
          <div className="pointer-events-none absolute right-3 top-3 flex items-center gap-2 rounded-[6px] border border-[#e4e7ec] bg-white px-2.5 py-1.5 text-[12px] text-[#667085] shadow-sm">
            <Spin size="small" /> 更新中
          </div>
        ) : null}
      </div>
    </div>
  );
}
