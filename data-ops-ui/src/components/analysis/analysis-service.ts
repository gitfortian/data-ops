import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type {
  Aggregation,
  AnalysisAsset,
  AnalysisFilter,
  AnalysisSpec,
  ChartType,
  FilterOperator,
} from './model';

const ANALYSIS_API = '/api/v1/analyses';
const SHARED_ANALYSIS_CHART_TYPES = new Set<ChartType>(['metric', 'bar', 'line', 'pie', 'table']);

type AnalysisFilterWireOperator = 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'CONTAINS';

interface AnalysisFilterWire {
  fieldId: string;
  operator: AnalysisFilterWireOperator;
  value?: unknown;
}

interface AnalysisWire {
  id: string;
  name: string;
  description?: string | null;
  datasetId: string;
  chartType: 'METRIC' | 'BAR' | 'LINE' | 'PIE' | 'TABLE';
  querySpec: {
    dimensions?: string[];
    metrics?: Array<{ fieldId: string; aggregation: Aggregation }>;
    filters?: AnalysisFilterWire[];
    sorts?: Array<{
      fieldId: string;
      aggregation?: Aggregation | null;
      direction?: 'ASC' | 'DESC' | null;
    }>;
    limit?: number;
    timeoutSeconds?: number;
  };
  visualConfig: {
    showLegend: boolean;
    showDataLabels: boolean;
    smooth: boolean;
    showGrid: boolean;
  };
  createTime?: string;
  updateTime?: string;
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const toChartType = (value: AnalysisWire['chartType']): ChartType => value.toLowerCase() as ChartType;

const toFilterOperator = (value: AnalysisFilterWireOperator): FilterOperator => {
  switch (value) {
    case 'NE': return 'neq';
    case 'GT': return 'gt';
    case 'GTE': return 'gte';
    case 'LT': return 'lt';
    case 'LTE': return 'lte';
    case 'CONTAINS': return 'contains';
    default: return 'eq';
  }
};

const toAsset = (wire: AnalysisWire): AnalysisAsset => {
  const sort = wire.querySpec.sorts?.[0];
  return {
    id: String(wire.id),
    name: wire.name,
    description: wire.description || '',
    datasetId: String(wire.datasetId),
    type: toChartType(wire.chartType),
    dimensions: wire.querySpec.dimensions || [],
    metrics: (wire.querySpec.metrics || []).map((metric) => ({
      field: metric.fieldId,
      aggregation: metric.aggregation,
    })),
    filters: (wire.querySpec.filters || []).map((filter, index): AnalysisFilter => ({
      id: `analysis-filter-${index}`,
      field: filter.fieldId,
      operator: toFilterOperator(filter.operator),
      value: filter.value == null ? '' : String(filter.value),
    })),
    sort: sort ? {
      field: sort.fieldId,
      direction: sort.direction === 'DESC' ? 'desc' : 'asc',
    } : undefined,
    style: {
      showLegend: wire.visualConfig?.showLegend ?? false,
      showDataLabels: wire.visualConfig?.showDataLabels ?? false,
      smooth: wire.visualConfig?.smooth ?? false,
      showGrid: wire.visualConfig?.showGrid ?? false,
    },
    limit: wire.querySpec.limit,
    timeoutSeconds: wire.querySpec.timeoutSeconds,
    createdAt: wire.createTime,
    updatedAt: wire.updateTime,
  };
};

const filterOperatorWire = (operator: FilterOperator): AnalysisFilterWireOperator => {
  switch (operator) {
    case 'neq': return 'NE';
    case 'gt': return 'GT';
    case 'gte': return 'GTE';
    case 'lt': return 'LT';
    case 'lte': return 'LTE';
    case 'contains': return 'CONTAINS';
    default: return 'EQ';
  }
};

const payload = (name: string, description: string, spec: AnalysisSpec) => {
  if (!SHARED_ANALYSIS_CHART_TYPES.has(spec.type)) {
    throw new Error('高级图表当前仅支持 Dashboard 本地图表；共享 Analysis 暂未扩展后端 chartType 协议');
  }
  const metric = spec.sort
    ? spec.metrics.find((candidate) => candidate.field === spec.sort?.field)
    : undefined;
  return {
    name,
    description: description || undefined,
    datasetId: Number(spec.datasetId),
    chartType: spec.type.toUpperCase(),
    querySpec: {
      dimensions: spec.type === 'metric' ? [] : spec.dimensions,
      metrics: spec.metrics.map((item) => ({ fieldId: item.field, aggregation: item.aggregation })),
      filters: spec.filters
        .filter((filter) => filter.field && filter.value !== '')
        .map((filter) => ({
          fieldId: filter.field,
          operator: filterOperatorWire(filter.operator),
          value: filter.value,
        })),
      sorts: spec.sort ? [{
        fieldId: spec.sort.field,
        aggregation: metric?.aggregation,
        direction: spec.sort.direction === 'desc' ? 'DESC' : 'ASC',
      }] : [],
      limit: spec.limit ?? (spec.type === 'table' ? 200 : 500),
      timeoutSeconds: spec.timeoutSeconds ?? 30,
    },
    visualConfig: spec.style,
  };
};

export const fetchAnalyses = async (): Promise<AnalysisAsset[]> => (
  unwrap(await HttpUtils.get<AnalysisWire[]>(ANALYSIS_API), '查询 Analysis 列表失败') || []
).map(toAsset);

export const createAnalysis = async (
  name: string,
  description: string,
  spec: AnalysisSpec,
): Promise<AnalysisAsset> => toAsset(unwrap(
  await HttpUtils.post<AnalysisWire>(ANALYSIS_API, payload(name, description, spec)),
  '创建 Analysis 失败',
));

export const updateAnalysis = async (
  analysisId: string,
  name: string,
  description: string,
  spec: AnalysisSpec,
): Promise<AnalysisAsset> => toAsset(unwrap(
  await HttpUtils.put<AnalysisWire>(`${ANALYSIS_API}/${analysisId}`, payload(name, description, spec)),
  '更新 Analysis 失败',
));

export const deleteAnalysis = async (analysisId: string): Promise<void> => {
  unwrap(await HttpUtils.delete<boolean>(`${ANALYSIS_API}/${analysisId}`), '删除 Analysis 失败');
};
