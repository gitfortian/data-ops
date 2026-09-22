/** 元数据中心(data-metadata)后端契约类型,对齐 /api/v1/metadata REST API（ticket 116/129）。 */

export type CollectProviderType = 'HARVESTED' | 'REGISTERED';
export type CollectRunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SUSPECT';
export type CollectTriggerType = 'SCHEDULE' | 'MANUAL' | 'DRY_RUN';

export interface MetadataPageResult<T> {
  records: T[];
  total: number;
  pages: number;
  pageNo: number;
  pageSize: number;
}

export interface CollectJobRecord {
  id: number;
  jobCode: string;
  jobName: string;
  providerType: CollectProviderType;
  typeName?: string | null;
  dataSourceId?: number | null;
  databaseName?: string | null;
  schemaName?: string | null;
  tablePattern?: string | null;
  collectColumns?: boolean | null;
  cronExpression?: string | null;
  enabled: boolean;
  /** 启用闸门：没有一次通过的 dry-run 预演就不允许启用（作用域改动会将其重置）。 */
  dryRunPassed: boolean;
  collapseThresholdPct?: number | null;
  missingRounds?: number | null;
  lastRunId?: number | null;
  createdBy?: string;
  updatedBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface CollectJobQueryParams {
  pageNo: number;
  pageSize: number;
  providerType?: CollectProviderType | '';
  enabled?: boolean;
  keyword?: string;
}

/** 新建/修改共用同一形状：PUT 是整行替换，表单载入后整份提交。 */
export interface CollectJobUpsertParams {
  /** 留空自动生成；它是身份不是内容,修改时留空 = 保留原值。 */
  jobCode?: string;
  jobName: string;
  /** 新建留空即 HARVESTED；修改必带（后端不认空串）。 */
  providerType?: CollectProviderType;
  typeName?: string;
  dataSourceId?: number | null;
  databaseName?: string | null;
  schemaName?: string | null;
  tablePattern?: string | null;
  collectColumns?: boolean | null;
  cronExpression?: string;
  collapseThresholdPct?: number | null;
  missingRounds?: number | null;
}

export interface CollectRunRecord {
  runId: number;
  jobId: number;
  providerType?: CollectProviderType;
  triggerType?: CollectTriggerType;
  dryRun?: boolean;
  status?: CollectRunStatus;
  cntTotal?: number | null;
  cntNew?: number | null;
  cntChanged?: number | null;
  cntUnchanged?: number | null;
  cntGone?: number | null;
  cntPartialFailed?: number | null;
  cursorWatermark?: string | null;
  scopeSnapshot?: string | null;
  errorMessage?: string | null;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number | null;
  createdBy?: string;
}

/** 元模型类型（GET /metadata/types 的子集；平台全局,不随项目变化）。 */
export interface EntityTypeView {
  id: number;
  typeName: string;
  category: string;
  displayName?: string;
  /** true = 该类型由物理采集通道维护,登记/对账通道不应对它建任务。 */
  collectible: boolean;
  status: string;
  description?: string;
  /** 渲染令牌：结果行的徽标色/图标出自类型定义,前端不写死类型清单。 */
  color?: string | null;
  iconUrl?: string | null;
  /** 检索面：权重与是否默认参与（tableColumn 默认排除即由此而来）。 */
  searchDefaultWeight?: number;
  searchIncludeByDefault?: boolean;
  /** includeFields=true（默认）时带回字段清单,是筛选控件的唯一来源。 */
  fields?: MetadataFieldView[];
}

/** 字段定义视图：`filterable` 决定要不要给筛选控件——能筛的才给,不让人点了才发现没反应。 */
export interface MetadataFieldView {
  id: number;
  fieldName: string;
  displayName?: string | null;
  baseType?: string | null;
  searchable: boolean;
  matchType?: MetadataMatchType | null;
  facetable: boolean;
  filterable: boolean;
  storageSlot?: string | null;
  showInList: boolean;
  ordinal?: number | null;
  deprecated?: boolean;
}

export type MetadataMatchType = 'text' | 'exact' | 'like' | 'range';

// ---------- 统一搜索（ticket 117 端点，页面见 ticket 123） ----------

/** 原生可筛键：目录固有列 + 三个特殊键，形状与后端 MetadataNativeFilterColumns 一致。 */
export type MetadataNativeFilterKey =
  | 'typeName'
  | 'providerType'
  | 'datasourceId'
  | 'parentAssetId'
  | 'databaseName'
  | 'layerCode'
  | 'owner'
  | 'entityStatus'
  | 'domainId'
  | 'tagged'
  | 'hasSummary';

/** 筛选条件：原生裸键或 `attr.<field_name>`（后者必须已在 field_def 提槽,否则 49024）。 */
export type MetadataFilter = Partial<Record<MetadataNativeFilterKey, string | number | boolean | Array<string | number>>> & {
  [attrKey: `attr.${string}`]: string | number | Array<string | number> | undefined;
};

export interface MetadataSearchParams {
  /** 空 = 浏览模式（目录浏览页靠这一条复用同一接口）。 */
  q?: string;
  /** 多值 = 类型切换；只换参数不换接口。 */
  index?: string[];
  queryFilter?: MetadataFilter;
  postFilter?: MetadataFilter;
  sortField?: 'relevance' | 'updateTime' | 'createTime' | 'name' | 'id';
  sortOrder?: 'asc' | 'desc';
  /** `<sortValue>|<id>`：仅单列排序提供游标,relevance 没有。 */
  searchAfter?: string;
  from?: number;
  size?: number;
  getHierarchy?: boolean;
  explain?: boolean;
}

/** 结果行是开放形状：列随元模型增长,前端按已知字段渲染,未知键不得成为编译期负担。 */
export interface MetadataSearchItem extends Record<string, unknown> {
  id: number;
  assetKey: string;
  name?: string | null;
  displayName?: string | null;
  summary?: string | null;
  typeName?: string | null;
  typeDisplayName?: string | null;
  providerType?: string | null;
  /** 源侧坐标：投影实体据此跳回源域编辑（HARVESTED 行的 source_id 是数据源 id，不可当跳转目标）。 */
  sourceType?: string | null;
  sourceId?: string | null;
  entityStatus?: string | null;
  ownerUser?: string | null;
  domainIds?: string[];
  layerCode?: string | null;
  dataSourceId?: number | null;
  databaseName?: string | null;
  schemaName?: string | null;
  tableName?: string | null;
  columnName?: string | null;
  updatedBy?: string | null;
  fullyQualifiedName?: string | null;
  parentAssetId?: number | null;
  firstSeenAt?: string | null;
  lastCollectAt?: string | null;
  updateTime?: string | null;
  createTime?: string | null;
  /** tableColumn 不在检索面时,该表被命中的列数由这一笔账还回来（plan §4.6）。 */
  matchedColumnCount?: number | null;
  parent?: { id: number; assetKey?: string; name?: string; displayName?: string; typeName?: string } | null;
  attributes?: Record<string, unknown> | string | null;
}

export interface MetadataSearchFacet {
  typeName: string;
  displayName?: string | null;
  count: number;
}

export interface MetadataSearchExplanation {
  backend?: string;
  booleanQuery?: string;
  degradedToLike?: boolean;
  totalSource?: string;
  sqlCount?: number;
  sql?: string[];
  notes?: string[];
}

export interface MetadataSearchResult {
  items: MetadataSearchItem[];
  /** 与行集同一次查询的 GROUP BY 结果：切换类型不换接口,计数也不会自相矛盾。 */
  typeFacets: MetadataSearchFacet[];
  total: number;
  from: number;
  size: number;
  nextSearchAfter?: string | null;
  explanation?: MetadataSearchExplanation | null;
}

// ---------- 实体详情聚合（ticket 118 端点，抽屉见 ticket 123） ----------

/**
 * 对齐后端 {@code EntityDTO}：facts 是共表固有列，attributes 是属性袋，slotValues 是已提槽字段的值。
 *
 * <p>{@code facts} 点名了目录读路径固定给出的那些列（键是 camelCase，与后端行映射同名）；
 * 留索引签名是因为共表还会加列（工单 133），新列不该在编译期卡住展示面。
 */
export interface MetadataEntityDto {
  id: number;
  typeName?: string | null;
  facts: MetadataEntityFacts;
  attributes: Record<string, unknown>;
  slotValues: Record<string, unknown>;
}

export interface MetadataEntityFacts extends Record<string, unknown> {
  assetKey?: string;
  name?: string | null;
  displayName?: string | null;
  summary?: string | null;
  parentAssetId?: number | null;
  providerType?: string | null;
  sourceType?: string | null;
  sourceId?: string | null;
  entityStatus?: string | null;
  ownerUser?: string | null;
  domainIds?: string[];
  layerCode?: string | null;
  dataSourceId?: number | string | null;
  databaseName?: string | null;
  schemaName?: string | null;
  tableName?: string | null;
  columnName?: string | null;
  fullyQualifiedName?: string | null;
  firstSeenAt?: string | null;
  lastCollectAt?: string | null;
  lastChangeAt?: string | null;
  updatedBy?: string | null;
  sourceUpdatedAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/**
 * 一块的三态（对齐后端 {@code SectionState}）。
 * EMPTY=确实没有，UNAVAILABLE=这块读不了：两者在面板上必须长得不一样，
 * 否则"没接上"会被读成"没有",排查方向直接指错。
 */
export type MetadataSectionStatus = 'OK' | 'EMPTY' | 'UNAVAILABLE';

export interface MetadataSection<T = unknown> {
  status: MetadataSectionStatus;
  code?: number | null;
  message?: string | null;
  data?: T | null;
}

/** 统计来自采集时落袋的那三份，行数恒为引擎估算。 */
export interface MetadataStatsSectionData {
  rowCountApprox?: number;
  partitioned?: boolean;
  lastDdlTime?: string | null;
  approximate?: boolean;
  lastCollectAt?: string | null;
}

export interface MetadataLineageEntry {
  assetKey: string;
  path: string;
}

/** 对齐后端 {@code MetadataGovernanceQueryService.ChangeView}。 */
export interface MetadataChangeRecord {
  id: number;
  changeType?: string | null;
  fieldName?: string | null;
  beforeValue?: string | null;
  afterValue?: string | null;
  detail?: string | null;
  collectRunId?: number | null;
  changedBy?: string | null;
  changedAt?: string | null;
}

/** 对齐后端 {@code LabelView}；state=SUGGESTED 是机器/继承的默认,等人工确认（写侧归工单 124）。 */
export interface MetadataLabelRecord {
  id: number;
  labelCode?: string | null;
  labelType?: string | null;
  state?: string | null;
  appliedBy?: string | null;
  appliedAt?: string | null;
  reason?: string | null;
  expiresAt?: string | null;
}

/** 源域投影的"目录里绝不存"那半袋（对齐后端 {@code EntityProjection.extra}）。 */
export interface MetadataSourceExtra {
  status?: string | null;
  dialect?: string | null;
  latestVersionNo?: number | null;
  tableName?: string | null;
  tableComment?: string | null;
  primaryKey?: string[] | null;
  partition?: Record<string, unknown> | null;
  columns?: Array<Record<string, unknown>> | null;
}

export interface MetadataSourceProjection {
  sourceId?: string | null;
  assetKey?: string | null;
  name?: string | null;
  displayName?: string | null;
  summary?: string | null;
  ownerUser?: string | null;
  domainIds?: string | null;
  layerCode?: string | null;
  sourceUpdatedAt?: string | null;
  /** 正常恒为空：源域业务内容进不了目录那一袋（§10 测试 10）。 */
  attributes?: Record<string, unknown> | null;
  extra?: MetadataSourceExtra | null;
}

/** 块名由后端按元模型决定（投影类型没有 stats，物理类型没有 source），故按键可缺。 */
export interface MetadataEntityDetail {
  entity: MetadataEntityDto;
  sections: Partial<Record<
    'stats' | 'children' | 'history' | 'labels' | 'lineage' | 'source',
    MetadataSection<unknown>
  >>;
}
