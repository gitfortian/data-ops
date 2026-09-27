/** Metric module API types (matches backend VO/DTO contracts). */

export type MetricType = 'ATOMIC' | 'DERIVED' | 'COMPOSITE';
export type MetricStatus = 'ENABLED' | 'DISABLED';
export type StatPeriod = 'DAY' | 'WEEK' | 'MONTH';
export type MetricDefinitionViewType = 'CURRENT_EDITABLE';
export type MetricVersionViewType = 'HISTORICAL_SNAPSHOT';
export type DependencyHealth = 'UP_TO_DATE' | 'OUTDATED' | 'REMOVED' | 'UNAVAILABLE';
export type AuthoringNextStep =
  | 'VALIDATE'
  | 'REVIEW_OUTDATED_DEPENDENCY'
  | 'RESOLVE_REMOVED_DEPENDENCY'
  | 'RETRY_DEPENDENCY_PROVIDER';
export type ImpactCoverageStatus = 'READY' | 'EMPTY' | 'UNAVAILABLE' | 'FORBIDDEN' | 'NOT_APPLICABLE';

/** 派生指标结构化限定条件(02):与后端 MetricQualifier 契约一致。 */
export interface MetricQualifier {
  field: string;
  op: string;
  value?: string;
}

export interface MetricRecord {
  id: number;
  metricCode: string;
  metricName: string;
  domainId?: number;
  domainName?: string;
  processId?: number;
  processName?: string;
  metricType: MetricType;
  caliberId?: number;
  caliberName?: string;
  calRule?: string;
  measureExpr?: string;
  filterExpr?: string;
  dimModelIds?: string;
  refMetricId?: number;
  refMetricName?: string;
  dimConstraint?: string;
  qualifiersJson?: string;
  modelId?: number;
  modelName?: string;
  statDimensions?: string;
  statPeriod: StatPeriod;
  unitId?: number;
  unitName?: string;
  businessDesc?: string;
  owner?: string;
  status: MetricStatus;
  version: number;
  createdBy?: string;
  updatedBy?: string;
  createTime?: string;
  updateTime?: string;
  compositions?: CompositionItem[];
  /** Phase 5: current mutable definition vs immutable historical snapshot. */
  definitionViewType?: MetricDefinitionViewType;
  editable?: boolean;
  /** Detail-only dependency truth resolved by the backend through owning-domain SPI. */
  dependencyChanges?: DependencyChange[];
  authoringNextStep?: AuthoringNextStep;
}

export interface CompositionItem {
  id?: number;
  subMetricId: number;
  operator: string;
  expression?: string;
  sortOrder: number;
  subMetricCode?: string;
  subMetricName?: string;
}

export interface MetricPageParams {
  pageNo: number;
  pageSize: number;
  domainId?: number;
  /** Stable Semantic Business Process id; display truth remains owned by Semantic. */
  processId?: number;
  metricType?: string;
  status?: string;
  keyword?: string;
  owner?: string;
  tagIds?: number[];
}

export interface MetricPageResult {
  records: MetricRecord[];
  total: number;
  pages: number;
  pageNo: number;
  pageSize: number;
}

export interface MetricCreatePayload {
  metricName: string;
  metricCode?: string;
  domainId?: number;
  processId?: number;
  metricType: MetricType;
  caliberId?: number;
  calRule?: string;
  measureExpr?: string;
  filterExpr?: string;
  dimModelIds?: string;
  refMetricId?: number;
  dimConstraint?: string;
  qualifiersJson?: string;
  modelId?: number;
  statDimensions?: string;
  statPeriod?: StatPeriod;
  unitId?: number;
  businessDesc?: string;
  owner?: string;
  compositions?: Omit<CompositionItem, 'id'>[];
}

export interface MetricUpdatePayload extends MetricCreatePayload {
  expectedVersion: number;
}

export interface MetricStats {
  total: number;
  atomic: number;
  derived: number;
  composite: number;
}

export interface MetricTagRecord {
  id: number;
  tagCode: string;
  tagName: string;
  sortOrder: number;
  status: string;
}

export interface MetricVersionRecord {
  id: number;
  version: number;
  snapshot: string;
  changeDesc?: string;
  changedBy: string;
  createTime: string;
  /** Phase 5 backend marks historical versions immutable and non-editable. */
  versionViewType?: MetricVersionViewType;
  editable?: boolean;
}

export interface MetricDependencyRecord {
  id: number;
  dependencyType: string;
  dependencyId: number;
  dependencyCode?: string;
  dependencyVersion?: number;
  createTime: string;
}

export interface UsageSummary {
  metricId: number;
  totalCount: number;
  reportCount: number;
  datasetCount: number;
  dashboardCount: number;
  apiCount: number;
  screenCount: number;
}

/** 指标使用明细(01 消费接线)。 */
export interface MetricUsageRecord {
  id: number;
  usageType: string;
  usageId: number;
  usageName?: string;
  createTime?: string;
}

export interface LineageGraphNode {
  id: number;
  assetKey: string;
  assetType: string;
  name: string;
  sourceType: string;
}

export interface LineageGraphEdge {
  id: number;
  sourceAssetId: number;
  targetAssetId: number;
  relationType: string;
  expression?: string;
}

export interface LineageGraphView {
  root: LineageGraphNode;
  direction: string;
  depth: number;
  nodes: LineageGraphNode[];
  relations: LineageGraphEdge[];
}

export interface ImpactReport {
  metricId: number;
  metricCode: string;
  metricName: string;
  changes: DependencyChange[];
  usageCount: number;
  authoringNextStep?: AuthoringNextStep;
}

export interface DependencyChange {
  dependencyType: string;
  dependencyId: number;
  dependencyCode?: string;
  registeredVersion?: number;
  currentVersion?: number;
  changeStatus: string;
  dependencyHealth?: DependencyHealth;
}

export interface MetricReferenceUsageEvidence {
  referenceId: number;
  usageType: string;
  usageId: number;
  usageName?: string;
  recordedAt?: string;
}

export interface MetricObservedUsageEvidence {
  evidenceId?: string;
  productKey?: string;
  consumerRef?: string;
  action?: string;
  outcome?: string;
  observedAt?: string;
}

export interface MetricObservedUsageCoverage {
  provider: string;
  status: ImpactCoverageStatus;
  evidence: MetricObservedUsageEvidence[];
  reason?: string;
}

/** Phase 5 impact context keeps declaration/reference usage separate from runtime observations. */
export interface MetricImpactContext {
  metricId: number;
  metricCode: string;
  metricName: string;
  metricVersion: number;
  dependencies: DependencyChange[];
  referenceUsage: MetricReferenceUsageEvidence[];
  observedUsage: MetricObservedUsageCoverage[];
  generatedAt: string;
}

/** 反向影响分析:引用了上游对象的指标行(dependencyTypes 为命中的登记类型)。 */
export interface AffectedMetricRecord {
  metricId: number;
  metricCode: string;
  metricName: string;
  metricType: MetricType;
  metricStatus: MetricStatus;
  owner?: string;
  registeredVersion?: number;
  dependencyTypes: string[];
}