export type ModelingModelId = number | string;

export type ModelingDialect = 'MYSQL' | 'POSTGRESQL' | 'ORACLE' | 'DORIS' | 'CLICKHOUSE' | 'STARROCKS' | string;

export type ModelingModelStatus = 'DRAFT' | string;

export interface ModelingModelRecord {
  id?: ModelingModelId;
  code?: string;
  name?: string;
  dialect?: ModelingDialect;
  description?: string;
  status?: ModelingModelStatus;
  owner?: string;
  /** 最后更新人（后端 V20；历史行为空时展示「-」）。 */
  updatedBy?: string | null;
  createTime?: string;
  updateTime?: string;
  /** 目标分层(44 派生写入,2026-09-17 工作台展示)。 */
  layerCode?: string;
  /** 关联业务过程 ID。 */
  processId?: number;
  /** 业务过程名称(服务端经 ProcessApi 解析)。 */
  processName?: string;
  /** 目标库名(分层配置 database_name 解析)。 */
  databaseName?: string;
  directoryId?: ModelingModelId | null;
  /** 业务域 ID(semantic 松散引用,新建向导/编辑写入)。 */
  domainId?: number | null;
  /** 业务域名称(服务端经 ProcessApi 解析)。 */
  domainName?: string | null;
  tagIds?: number[];
  deletedBy?: string;
  deletedTime?: string;
  /** 来源数据源 ID(逆向导入写入,用于血缘)。 */
  sourceDatasourceId?: number | null;
  /** 来源库名。 */
  sourceDatabase?: string | null;
  /** 来源表名。 */
  sourceTable?: string | null;
  /** 字段导入方式(血缘追溯):MANUAL/SOURCE_TABLE/MODEL/BUSINESS_PROCESS。 */
  importMode?: string | null;
  /** 来源模型ID(import_mode=MODEL时记录,血缘追溯)。 */
  sourceModelId?: number | null;
  /** 当前发布版本ID(未发布时为 null)。 */
  publishedVersionId?: number | null;
  /** 最新版本号(0=未发布)。 */
  latestVersionNo?: number;
}

export interface ModelingDirectoryRecord {
  id?: number;
  parentId?: number | null;
  name?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ModelingTagRecord {
  id?: number;
  name?: string;
  createTime?: string;
}

export interface ModelingPageInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export interface ModelingPageResult {
  bizData: ModelingModelRecord[];
  pagination: ModelingPageInfo;
}

export interface ModelingPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  directoryId?: number;
  tagIds?: number[];
  /** 分层过滤(2026-09-17)。 */
  layerCode?: string;
  /** 业务过程过滤。 */
  processId?: number;
  /** 业务域过滤:该域下全部业务过程(目录树按业务域视图,2026-09-17)。 */
  processIds?: number[];
  /** 业务域过滤(domain_id 直存)。 */
  domainId?: number;
  /** 状态过滤。 */
  status?: string;
}

export interface ModelingCreatePayload {
  name: string;
  code: string;
  dialect: string;
  description?: string;
  /** 目标分层编码(2026-09-17 新建模型分步)。 */
  layerCode?: string;
  /** 关联业务域 ID(v2.0)。 */
  domainId?: number;
  /** 关联业务过程 ID(2026-09-17)。 */
  processId?: number;
  /** 所属目录 ID(2026-09-17)。 */
  directoryId?: number;
  /** 来源数据源 ID(逆向导入写入,用于血缘)。 */
  sourceDatasourceId?: number;
  /** 来源库名。 */
  sourceDatabase?: string;
  /** 来源表名。 */
  sourceTable?: string;
}

/** 编码不可编辑（稳定键）；除编码外均可更新。引用字段 undefined=不修改，null=清空。 */
export interface ModelingUpdatePayload {
  name: string;
  dialect: string;
  description?: string;
  layerCode?: string | null;
  domainId?: number | null;
  processId?: number | null;
  directoryId?: number | null;
  sourceDatasourceId?: number | null;
  sourceDatabase?: string | null;
  sourceTable?: string | null;
}

export interface ModelingColumnRecord {
  id?: number;
  columnName?: string;
  dataType?: string;
  length?: number | null;
  scale?: number | null;
  nullable?: boolean;
  defaultValue?: string;
  comment?: string;
  businessDescription?: string;
  sortOrder?: number;
  /** 数据标准松散引用(ticket 30 预留 / 39 套用写入)。 */
  stdTypeId?: number | null;
  stdNamingId?: number | null;
  stdCodeSetCode?: string | null;
  stdUnitId?: number | null;
  stdCaliberId?: number | null;
  stdSecurityId?: number | null;
  /** 标准字段关联(38 导入匹配 / 44 派生继承写入)。 */
  stdFieldId?: number | null;
  /** 聚合层字段角色(51/52;非聚合层为空):DIMENSION 分组键 / MEASURE 度量。 */
  fieldRole?: 'DIMENSION' | 'MEASURE' | null;
  /** 聚合函数(MEASURE 必填;SUM/COUNT/COUNT_DISTINCT/MAX/MIN/AVG)。 */
  aggregateFunc?: string | null;
  /** 口径/转换表达式(DWS/ADS 度量口径)。 */
  transformExpr?: string | null;
}

/** 字段标准推荐(ticket 39,semantic StandardRecommendApi 的前端视图)。 */
export interface ModelingStructureRecommendPayload {
  columnName: string;
  dataType?: string;
  role?: 'PROCESS' | 'DIMENSION' | 'METRIC';
}

export interface ModelingStandardCandidate {
  standardId: number;
  kind: string;
  code: string;
  name: string;
  codeSetCode?: string;
  ruleExpr?: string;
  reason?: string;
}

export interface ModelingStandardRecommendation {
  naming: {
    evaluated: boolean;
    matched: boolean;
    standardId?: number;
    code?: string;
    name?: string;
    ruleExpr?: string;
  };
  typeCandidates: ModelingStandardCandidate[];
  codeCandidates: ModelingStandardCandidate[];
  unitCandidates: ModelingStandardCandidate[];
  caliberCandidates: ModelingStandardCandidate[];
  securityCandidates: ModelingStandardCandidate[];
}

/** 逆向导入(ticket 08)。 */
export interface ModelingImportTableView {
  database?: string;
  schema?: string;
  name: string;
  type?: string;
  remarks?: string;
}

export interface ModelingImportColumnView {
  name: string;
  typeName: string;
  size?: number | null;
  scale?: number | null;
  /** 小数位(部分 JDBC 驱动返回)。 */
  decimalDigits?: number | null;
  nullable?: boolean;
  primaryKey?: boolean;
  /** 默认值(部分 JDBC 驱动返回)。 */
  defaultValue?: string;
  remarks?: string;
  /** 表级备注(冗余到每列,方便前端取值)。 */
  tableRemarks?: string;
}

export interface ModelingImportPayload {
  dialect: string;
  directoryId?: number;
  /** 目标分层编码(38,缺省 ODS)。 */
  layerCode?: string;
  tables: {
    datasourceId: number;
    database: string;
    table: string;
    code?: string;
    name?: string;
    remarks?: string;
    /** event_time 的来源业务字段(可空 = 后端自动识别含 time/date 的列)。 */
    eventTimeField?: string;
  }[];
}

/** 导入动作:建模型 / 已有空模型补字段 / 已有字段跳过 / 失败(2026-09-17)。 */
export type ModelingImportAction = 'CREATED' | 'FILLED' | 'SKIPPED' | 'FAILED';

/** 逐列匹配明细(38 评审补充:命中/未命中都要可见)。 */
export interface ModelingImportColumnMatch {
  columnName: string;
  dataType?: string;
  stdFieldId?: number;
  stdFieldCode?: string;
  stdFieldName?: string;
  matchedBy?: string;
  /** 类型/命名/安全标准未命中时的降级说明。 */
  degradedReason?: string;
  /** 技术列(自动补充,不参与治理)。 */
  technical: boolean;
  /** 名称相似给出的建议(未自动关联,需用户确认)。 */
  suggestedStdFieldId?: number;
  suggestedStdFieldName?: string;
  /** 实际套用的类型标准(53;为空 = 未套用,交用户处理)。 */
  stdTypeId?: number;
  stdTypeName?: string;
}

export interface ModelingImportResult {
  created: string[];
  /** 编码已存在但模型无字段、按源表补全了结构的表(2026-09-17;兼容旧服务端可缺省)。 */
  filled?: string[];
  skipped: string[];
  failed: { table: string; reason: string }[];
  /** 单表去向,供"打开模型"直达详情;modelId 为空表示失败发生在建模型之前。 */
  models?: {
    table: string;
    modelId?: number;
    action: ModelingImportAction;
    /** 逐列治理明细(2026-09-17)。 */
    fields?: ModelingImportColumnMatch[];
  }[];
  /** 标准自动套用统计(38)。 */
  standardApply?: {
    typeApplied: number;
    securityApplied: number;
    namingApplied: number;
    degraded: number;
  };
}

/** 来源映射视图(ticket 19);mapped=false 即未映射。 */
export interface ModelingMappingView {
  targetColumn: string;
  dataType?: string;
  mapped: boolean;
  sourceDatasourceId?: number;
  sourceDatabase?: string;
  sourceTable?: string;
  sourceColumn?: string;
  transformExpr?: string;
  stdProcessFieldId?: number;
}

export interface ModelingMappingSavePayload {
  sourceDatasourceId: number;
  sourceDatabase: string;
  sourceTable: string;
  sourceColumn: string;
  transformExpr?: string;
}

export interface ModelingIndexRecord {
  id?: number;
  indexName?: string;
  uniqueIndex?: boolean;
  indexType?: string;
  columns?: string[];
}

export interface ModelingPartitionRecord {
  type?: string;
  columns?: string[];
  expression?: string;
}

export interface ModelingStructureRecord {
  modelId?: number;
  modelCode?: string;
  modelName?: string;
  dialect?: ModelingDialect;
  status?: ModelingModelStatus;
  modelDescription?: string;
  tableName?: string;
  tableComment?: string;
  columns?: ModelingColumnRecord[];
  primaryKey?: string[];
  indexes?: ModelingIndexRecord[];
  partition?: ModelingPartitionRecord;
  tableProperties?: Record<string, string>;
}

export interface ModelingColumnPayload {
  columnName: string;
  dataType: string;
  length?: number | null;
  scale?: number | null;
  nullable?: boolean;
  defaultValue?: string;
  comment?: string;
  businessDescription?: string;
  /** DWS/ADS 聚合元数据(详情页表结构编辑器直写)。 */
  fieldRole?: 'DIMENSION' | 'MEASURE' | null;
  aggregateFunc?: string | null;
  transformExpr?: string | null;
}

export interface ModelingIndexPayload {
  indexName: string;
  uniqueIndex: boolean;
  indexType?: string;
  columns: string[];
}

export interface ModelingTypeOption {
  name?: string;
  lengthRequired?: boolean;
  scaleAllowed?: boolean;
}

export interface ModelingValidationIssue {
  severity?: 'ERROR' | 'WARNING' | string;
  scope?: 'TABLE' | 'COLUMN' | 'PRIMARY_KEY' | 'INDEX' | 'PARTITION' | 'PROPERTY' | string;
  target?: string;
  message?: string;
}

export interface ModelingDdlRecord {
  dialect?: string;
  script?: string;
}

export interface ModelingSaveStructurePayload {
  tableName?: string;
  tableComment?: string;
  columns: ModelingColumnPayload[];
  primaryKey?: string[];
  indexes?: ModelingIndexPayload[];
  partition?: { type?: string; columns?: string[]; expression?: string };
  tableProperties?: Record<string, string>;
}

export interface ModelingStandardCapturePayload {
  kind: string;
  code: string;
  name: string;
  ruleExpr?: string;
  typeCode?: string;
  stdType?: string;
  columnName?: string;
  businessDesc?: string;
}

export interface ModelingStandardCaptureResult {
  standardId: number;
  code: string;
  name: string;
  created: boolean;
  message?: string;
}

/** 字段分层映射视图(ticket 43)。 */
export interface ModelingLayerFieldMappingView {
  id: number;
  modelId: number;
  processFieldId: number;
  processFieldCode?: string;
  processFieldName?: string;
  layerId: number;
  layerCode?: string;
  layerName?: string;
  layerFieldName: string;
  layerDataType?: string;
  sourceField?: string;
  transformExpr?: string;
}

export interface ModelingLayerFieldMappingPayload {
  processFieldId: number;
  layerId: number;
  layerFieldName: string;
  layerDataType?: string;
  sourceField?: string;
  transformExpr?: string;
}

/** 60/61:指标反推草稿的一条度量建议。 */
export interface ModelingMetricDraftMeasure {
  sourceColumn: string;
  aggregateFunc: string;
  landingName: string;
}

/** 60/61:指标反推草稿(选指标 → 业务过程 + 上游模型 + 周期 + 度量/维度建议)。 */
export interface ModelingMetricDraft {
  /** 所选指标的业务过程;跨过程时前端按首个过程预填。 */
  processIds: number[];
  upstreamModelIds: number[];
  /** 已按建模约定归一(1h/1d/1w/1m);指标周期缺失或无法识别时为空。 */
  statPeriod?: string | null;
  measures: ModelingMetricDraftMeasure[];
  dimensions: string[];
  warnings: string[];
}

/** 派生预览:源表就绪情况 + 字段继承清单 + 治理率(只读,不写库)。 */
export interface ModelingDerivePreview {
  processId: number;
  layerCode: string;
  /** 该目标分层的上游分层(ticket 49;不支持的分层为空)。 */
  upstreamLayer?: string;
  /** 该目标分层当前是否支持派生。 */
  supported: boolean;
  /** 派生模式(49):INHERIT / AGGREGATE(DWS) / APPLICATION(ADS)。 */
  mode?: 'INHERIT' | 'AGGREGATE' | 'APPLICATION';
  /** 聚合/应用层的上游模型候选(51/52;上游是模型而不是数据源表)。 */
  upstreamModels?: {
    modelId: number;
    code: string;
    name: string;
    layerCode: string;
    selected: boolean;
    fieldCount: number;
  }[];
  /** 命名建议(51/52 含周期或应用编码)。 */
  suggestedCode?: string;
  suggestedName?: string;
  /** 不支持时的原因(说明上游是什么、缺什么能力、指向哪张票)。 */
  unsupportedReason?: string;
  sources: {
    bindingId: number;
    datasourceId: number;
    sourceTable: string;
    tableRole: 'MAIN' | 'DETAIL' | 'DIM';
    joinCondition?: string;
    odsModelId?: number;
    odsModelCode?: string;
    odsTableName?: string;
    ready: boolean;
    fieldCount: number;
    note?: string;
  }[];
  fields: {
    sourceTable?: string;
    tableRole: string;
    odsModelId?: number;
    sourceColumn: string;
    dataType?: string;
    length?: number | null;
    scale?: number | null;
    nullable?: boolean;
    comment?: string;
    stdFieldId?: number;
    stdFieldCode?: string;
    stdFieldName?: string;
    matchedBy?: string;
    landingField: string;
    include: boolean;
    conflicting: boolean;
    conflictWith?: string;
    technical: boolean;
    /** 维表约定列(代理键/SCD,50):结构性字段,不计入治理率。 */
    convention: boolean;
    /** 聚合层字段角色(51/52):DIMENSION 分组键 / MEASURE 度量。 */
    fieldRole?: 'DIMENSION' | 'MEASURE' | null;
    /** 聚合函数(MEASURE)。 */
    aggregateFunc?: string | null;
    /** 名称相似给出的建议(未自动关联,需用户确认)。 */
    suggestedStdFieldId?: number;
    suggestedStdFieldName?: string;
  }[];
  totalFields: number;
  matchedFields: number;
  unmatchedFields: number;
  conflictFields: number;
  technicalFields: number;
  /** 维表约定列数(50)。 */
  conventionFields: number;
  governanceRate: number;
  existingModel?: { modelId: number; code: string; name: string };
  warnings: string[];
}

/** 业务过程主线视图(ticket 47)。 */
export interface ModelingMainlineModel {
  modelId: number;
  code: string;
  name: string;
  status?: string;
  layerCode?: string;
}

export interface ModelingMainlineLayer {
  layerCode: string;
  modelCount: number;
  models: ModelingMainlineModel[];
}

export interface ModelingMainlineCoverage {
  processId: number;
  processCode: string;
  processName: string;
  domainId?: number;
  totalModels: number;
  layers: ModelingMainlineLayer[];
}

/** 变更影响分析(ticket 46)。 */
export type ModelingImpactKind = 'STANDARD_FIELD' | 'SOURCE_COLUMN';

export interface ModelingImpactItem {
  modelId: number;
  layerId?: number;
  processFieldId?: number;
  targetColumn: string;
  sourceDetail?: string;
  impactKind: ModelingImpactKind;
}

// ---- 版本管理 ----

export interface ModelingVersionSummary {
  id: number;
  modelId: number;
  versionNo: number;
  columnCount: number;
  checksum: string;
  publishedBy?: string;
  publishTime: string;
}

export interface ModelingVersionDetail {
  version: ModelingVersionSummary;
  structure: ModelingStructureRecord;
}
