import { message, Segmented, Select, Spin, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowUpRight, RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MarkerType,
  type Edge,
  type Node,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { YakButton, YakEmpty } from '@/components/ui';
import LineageNode, { type LineageNodeData } from '@/features/lineage/LineageNode';
import { buildLineageView, lineageLevels } from '@/features/lineage/graph-layout';
import {
  LINEAGE_ASSET_TYPES,
  assetTypeLabel,
  getLineageAssetByKey,
  getLineageGraph,
  type LineageAsset,
  type LineageAssetType,
  type LineageDirection,
  type LineageGraph,
} from '@/services/data-analysis';
import { getMetricLineage, pageMetrics } from '@/services/metric/api';
import type { MetricDependencyRecord, MetricRecord } from '@/services/metric/types';
import { formatMetricTime } from '../constants';

const nodeTypes = { lineage: LineageNode };
const visibleTypes = new Set<LineageAssetType>(LINEAGE_ASSET_TYPES);

const DEPENDENCY_TYPE_LABEL: Record<string, string> = {
  MODEL: '数据来源模型',
  CALIBER: '口径标准',
  UNIT: '单位标准',
  REF_METRIC: '引用原子指标',
  COMPOSITION: '复合子指标',
};

const countAssetTypes = (graph?: LineageGraph) => {
  const counts = new Map<LineageAssetType, number>();
  graph?.nodes.forEach((asset) => {
    if (asset.id === graph.root.id) return;
    counts.set(asset.assetType, (counts.get(asset.assetType) || 0) + 1);
  });
  return counts;
};

const DEPENDENCY_ASSET_KEY: Record<string, (depId: number) => string> = {
  MODEL: (id) => `modeling:model:${id}`,
  REF_METRIC: (id) => `metric:${id}`,
  COMPOSITION: (id) => `metric:${id}`,
};

/**
 * 把登记依赖中图谱未包含的上游(如未注册进全局血缘图的模型)合成虚拟上游节点，
 * 保证图谱与依赖表口径一致(M-3)。口径/单位属于属性标准而非数据流，仅在依赖表呈现。
 */
const mergeDependenciesIntoGraph = (
  graph: LineageGraph,
  dependencies: MetricDependencyRecord[],
): LineageGraph => {
  const knownKeys = new Set(graph.nodes.map((node) => node.assetKey));
  const nodes = [...graph.nodes];
  const relations = [...graph.relations];
  dependencies.forEach((dep) => {
    const keyOf = DEPENDENCY_ASSET_KEY[dep.dependencyType];
    if (!keyOf || dep.dependencyId == null) return;
    const key = keyOf(dep.dependencyId);
    if (knownKeys.has(key)) return;
    knownKeys.add(key);
    const syntheticId = `dep:${key}`;
    nodes.push({
      id: syntheticId,
      assetKey: key,
      assetType: dep.dependencyType === 'MODEL' ? 'TABLE' : 'METRIC',
      name: dep.dependencyCode || key,
      sourceType: 'DEPENDENCY_SNAPSHOT',
    });
    relations.push({
      id: `dep-rel:${dep.id}`,
      sourceAssetId: syntheticId,
      targetAssetId: graph.root.id,
      relationType: dep.dependencyType === 'MODEL' ? 'CONSUMES' : 'DERIVES_FROM',
    });
  });
  return { ...graph, nodes, relations };
};

const MetricLineagePage = () => {
  const [metricId, setMetricId] = useState<number | null>(null);
  const [metricOptions, setMetricOptions] = useState<{ label: string; value: number }[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [dependencies, setDependencies] = useState<MetricDependencyRecord[]>([]);
  const [direction, setDirection] = useState<LineageDirection>('BOTH');
  const [depth, setDepth] = useState(3);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [rootAsset, setRootAsset] = useState<LineageAsset>();
  const [graph, setGraph] = useState<LineageGraph>();
  const [isAssetLoading, setIsAssetLoading] = useState(false);
  const [isGraphLoading, setIsGraphLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setSearchLoading(true);
    pageMetrics({ pageNo: 1, pageSize: 200 })
      .then((result) => {
        setMetricOptions(
          (result.records ?? []).map((record: MetricRecord) => ({
            label: `${record.metricName}（${record.metricCode}）`,
            value: record.id,
          })),
        );
      })
      .catch(() => {
        message.error('加载指标列表失败');
      })
      .finally(() => setSearchLoading(false));
  }, []);

  // 选择指标后先定位血缘资产(assetKey = metric:{id})
  useEffect(() => {
    if (!metricId) {
      setRootAsset(undefined);
      return;
    }
    let cancelled = false;
    setIsAssetLoading(true);
    setError('');
    setRootAsset(undefined);
    setGraph(undefined);

    void getLineageAssetByKey(`metric:${metricId}`)
      .then((asset) => {
        if (!cancelled) setRootAsset(asset);
      })
      .catch((requestError) => {
        if (!cancelled) {
          setError(requestError instanceof Error ? requestError.message : '查询指标血缘资产失败');
        }
      })
      .finally(() => {
        if (!cancelled) setIsAssetLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [metricId]);

  useEffect(() => {
    if (!rootAsset) return;
    let cancelled = false;
    setIsGraphLoading(true);
    setError('');

    void Promise.all([
      getLineageGraph(rootAsset.id, depth),
      getMetricLineage(metricId!).catch(() => [] as MetricDependencyRecord[]),
    ])
      .then(([value, deps]) => {
        if (cancelled) return;
        setGraph(value);
        setDependencies(deps);
      })
      .catch((requestError) => {
        if (cancelled) return;
        setGraph(undefined);
        setError(requestError instanceof Error ? requestError.message : '加载指标血缘图失败');
      })
      .finally(() => {
        if (!cancelled) setIsGraphLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [rootAsset, depth, refreshVersion, metricId]);

  const mergedGraph = useMemo(
    () => (graph ? mergeDependenciesIntoGraph(graph, dependencies) : undefined),
    [graph, dependencies],
  );

  const view = useMemo(
    () => (mergedGraph ? buildLineageView(mergedGraph, direction, visibleTypes) : undefined),
    [mergedGraph, direction],
  );

  const flowNodes = useMemo<Array<Node<LineageNodeData>>>(() => (
    view?.nodes.map(({ asset, position }) => ({
      id: asset.id,
      type: 'lineage',
      position,
      draggable: false,
      selectable: false,
      data: { asset, root: asset.id === mergedGraph?.root.id },
    })) || []
  ), [mergedGraph?.root.id, view?.nodes]);

  const flowEdges = useMemo<Edge[]>(() => (
    view?.relations.map((relation) => ({
      id: relation.id,
      source: relation.sourceAssetId,
      target: relation.targetAssetId,
      type: 'smoothstep',
      markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14, color: '#b9bec6' },
      style: { stroke: '#c9cdd3', strokeWidth: 1.2 },
    })) || []
  ), [view?.relations]);

  const summary = useMemo(() => {
    if (!mergedGraph) return { upstream: 0, downstream: 0 };
    const levels = lineageLevels(mergedGraph);
    let upstream = 0;
    let downstream = 0;
    levels.forEach((level, assetId) => {
      if (assetId === mergedGraph.root.id) return;
      if (level < 0) upstream += 1;
      if (level > 0) downstream += 1;
    });
    return { upstream, downstream };
  }, [mergedGraph]);

  const counts = useMemo(() => countAssetTypes(mergedGraph), [mergedGraph]);

  const dependencyColumns: ColumnsType<MetricDependencyRecord> = [
    {
      title: '依赖类型',
      dataIndex: 'dependencyType',
      width: 140,
      render: (value: string) => <Tag>{DEPENDENCY_TYPE_LABEL[value] ?? value}</Tag>,
    },
    { title: '依赖编码', dataIndex: 'dependencyCode', width: 180, render: (v?: string) => v || '-' },
    {
      title: '登记版本',
      dataIndex: 'dependencyVersion',
      width: 100,
      render: (v?: number) => (v != null ? `v${v}` : '-'),
    },
    {
      title: '绑定时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => formatMetricTime(v),
    },
  ];

  const isLoading = isAssetLoading || isGraphLoading;
  const graphKey = `${rootAsset?.id || 'empty'}:${depth}:${direction}:${refreshVersion}`;

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="text-[20px] font-semibold leading-7">指标血缘</div>
      <div className="mt-1 text-[13px] text-[#667085]">
        查看指标的上下游血缘图谱：模型 → 原子指标 → 派生/复合指标 → 消费方
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Select
          showSearch
          loading={searchLoading}
          placeholder="选择指标查看血缘"
          className="!w-[320px]"
          value={metricId ?? undefined}
          onChange={(value) => setMetricId(value)}
          options={metricOptions}
          filterOption={(input, option) =>
            (option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
          }
        />
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
        <span className="text-[12px] text-[#8a8f99]">深度</span>
        <Select
          size="small"
          value={depth}
          className="!w-[84px]"
          onChange={(value) => setDepth(value)}
          options={[1, 2, 3, 4, 5].map((v) => ({ label: `${v} 层`, value: v }))}
        />
        {metricId ? (
          <YakButton
            size="small"
            icon={<RefreshCw size={13} />}
            loading={isLoading}
            onClick={() => setRefreshVersion((v) => v + 1)}
          >
            刷新
          </YakButton>
        ) : null}
        <div className="ml-auto flex items-center gap-3 text-[12px] text-[#667085]">
          {mergedGraph ? (
            <>
              <span>上游 {summary.upstream}</span>
              <span className="h-3 w-px bg-[#dfe3e8]" />
              <span>下游 {summary.downstream}</span>
            </>
          ) : null}
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

      {/* 图谱 */}
      <div className="relative mt-4 flex h-[480px] flex-col overflow-hidden border border-[#e4e7ec] bg-[#fcfcfd]">
        {/* Legend */}
        {metricId ? <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-[#f0f2f5] bg-white/90 px-3 py-2 text-[12px] text-[#667085]">
          <span>上游：来源模型 / 上游指标</span>
          <span className="text-[#c1c5cc]">→</span>
          <span className="font-medium text-[#344054]">当前指标</span>
          <span className="text-[#c1c5cc]">→</span>
          <span>下游：派生/复合指标 / 数据集 / 报表</span>
          {LINEAGE_ASSET_TYPES.map((type) => {
            const count = counts.get(type) || 0;
            return count > 0 ? (
              <span key={type} className="ml-1 text-[#8a8f99]">
                {assetTypeLabel[type]} {count}
              </span>
            ) : null;
          })}
        </div> : null}

        {isLoading && !mergedGraph ? (
          <div className="absolute inset-0 z-10 flex items-center justify-center bg-white/70">
            <Spin tip="正在加载血缘..." />
          </div>
        ) : error && !mergedGraph ? (
          <div className="flex h-full items-center justify-center">
            <YakEmpty title="血缘暂不可用" description={error} />
          </div>
        ) : !metricId ? (
          <div className="flex h-full items-center justify-center">
            <YakEmpty title="请选择指标" description="从上方下拉选择一个指标查看其血缘图谱" />
          </div>
        ) : mergedGraph && flowNodes.length ? (
          <div className="relative min-h-0 flex-1">
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
            {mergedGraph.relations.length === 0 ? (
              <div className="pointer-events-none absolute bottom-4 left-1/2 -translate-x-1/2 rounded-[6px] border border-[#e4e7ec] bg-white px-3 py-1.5 text-[12px] text-[#667085] shadow-sm">
                已定位当前指标，但暂未采集到上下游关系
              </div>
            ) : null}
          </div>
        ) : (
          <div className="flex h-full items-center justify-center">
            <YakEmpty title="暂无血缘数据" description="指标保存后会自动登记血缘资产，请稍后点击刷新。" />
          </div>
        )}

        {isGraphLoading && graph ? (
          <div className="pointer-events-none absolute right-3 top-12 flex items-center gap-2 rounded-[6px] border border-[#e4e7ec] bg-white px-2.5 py-1.5 text-[12px] text-[#667085] shadow-sm">
            <Spin size="small" /> 更新中
          </div>
        ) : null}
      </div>

      {/* 扁平依赖列表 */}
      <div className="mt-6">
        <div className="mb-2 text-[15px] font-medium">登记依赖（含引用时刻编码/版本快照）</div>
        {dependencies.length > 0 ? (
          <Table<MetricDependencyRecord>
            rowKey="id"
            columns={dependencyColumns}
            dataSource={dependencies}
            pagination={false}
            size="small"
          />
        ) : (
          <YakEmpty compact title="暂无依赖" description="编辑指标保存后会自动登记模型、口径、单位等依赖快照" />
        )}
      </div>
    </div>
  );
};

export default MetricLineagePage;
