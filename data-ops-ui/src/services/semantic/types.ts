/** 业务语义数据标准类型(ticket 30)。六类统一模型,kind 外专有字段按需为空。 */

export type SemanticStandardKind = 'NAMING' | 'TYPE' | 'CODE' | 'UNIT' | 'CALIBER' | 'SECURITY';

export type SemanticStandardStatus = 'ENABLED' | 'DISABLED';

export type SemanticStandardId = number;

export interface SemanticStandardRecord {
  id: SemanticStandardId;
  kind: SemanticStandardKind;
  code: string;
  name: string;
  status: SemanticStandardStatus;
  version: number;
  sortOrder: number;
  preset: boolean;
  description?: string;
  /** 命名标准 */
  scope?: string;
  layer?: string;
  ruleExpr?: string;
  example?: string;
  /** 类型标准 */
  typeCode?: string;
  stdType?: string;
  sourceMapping?: string;
  /** 码值标准 */
  codeSetCode?: string;
  codeValue?: string;
  codeLabel?: string;
  /** 单位标准 */
  unitCode?: string;
  unitType?: string;
  /** 口径标准 */
  caliberCode?: string;
  calRule?: string;
  businessDesc?: string;
  /** 安全标准 */
  levelCode?: string;
  maskRule?: string;
  /** 码值数;仅码集聚合组行有值(32.1,全部视图 CODE 行)。 */
  codeValueCount?: number;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SemanticStandardPageParams {
  pageNo: number;
  pageSize: number;
  kind?: SemanticStandardKind;
  keyword?: string;
  status?: SemanticStandardStatus;
}

export interface SemanticPageInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export interface SemanticPageResult<T = SemanticStandardRecord> {
  bizData: T[];
  pagination: SemanticPageInfo;
}

export interface SemanticStandardVersionRecord {
  standardId: SemanticStandardId;
  version: number;
  operatedBy?: string;
  createTime?: string;
  payload: Record<string, unknown>;
}

/** 业务域树节点(ticket 33)。 */
export interface SemanticDomainNode {
  id: number;
  code: string;
  name: string;
  owner?: string;
  description?: string;
  sortOrder: number;
  children: SemanticDomainNode[];
}

/** 业务过程(ticket 34)。 */
export interface SemanticProcessRecord {
  id: number;
  code: string;
  name: string;
  domainId: number;
  grain?: string;
  bizType: 'FACT' | 'DIMENSION';
  owner?: string;
  description?: string;
  sortOrder: number;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SemanticProcessPageParams {
  pageNo: number;
  pageSize: number;
  domainId?: number;
  keyword?: string;
  bizType?: 'FACT' | 'DIMENSION';
}

/** 过程源表关联(ticket 36)。 */
export interface SemanticProcessSourceRecord {
  id: number;
  processId: number;
  datasourceId: number;
  sourceTable: string;
  tableRole: 'MAIN' | 'DETAIL' | 'DIM';
  joinCondition?: string;
}

/** 数仓分层(ticket 37)。 */
export interface SemanticLayerRecord {
  id: number;
  code: string;
  name: string;
  databaseName?: string;
  datasourceId?: number;
  stdNamingId?: number;
  defaultPartition?: string;
  storageFormat?: string;
  lifecycleDays?: number;
  description?: string;
  sortOrder: number;
  status: 'ENABLED' | 'DISABLED';
  /** 定标闸门(M2-5):该层是否强制字段落标;缺省=强制。 */
  stdMandatory?: boolean;
  preset: boolean;
  /** 被引用模型数(modeling 侧上报,2026-09-16)。 */
  modelCount?: number;
  /** 该层模型字段总数(M2-5 定标观察期;无建模域统计时为空)。 */
  stdColumnTotal?: number;
  /** 该层已绑定标准字段数(M2-5 定标观察期)。 */
  stdBoundColumns?: number;
}

/** 标准字段(ticket 35)。 */
export type SemanticFieldRole = 'PROCESS' | 'DIMENSION' | 'METRIC';

export interface SemanticFieldRecord {
  id: number;
  code: string;
  name: string;
  role: SemanticFieldRole;
  status?: 'ENABLED' | 'DISABLED';
  dataType?: string;
  stdTypeId?: number;
  stdUnitId?: number;
  stdCaliberId?: number;
  stdCodeSetCode?: string;
  stdSecurityId?: number;
  /** 引用标准名称（名称（编码））,服务端解析(2026-09-16)。 */
  stdTypeName?: string;
  stdUnitName?: string;
  stdCaliberName?: string;
  stdSecurityName?: string;
  businessDesc?: string;
  source?: 'PRESET' | 'MANUAL' | 'CAPTURE';
  version?: number;
  /** 过程内装配标记，派生建模时用于默认勾选。 */
  required?: boolean;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SemanticFieldPageParams {
  pageNo: number;
  pageSize: number;
  role?: SemanticFieldRole;
  keyword?: string;
}

/** 标准引用/绕过统计（ticket 42）。 */
export interface SemanticStandardUsageSummary {
  standardId: number;
  applyCount: number;
  bypassCount: number;
}

/** 码集内单个码值条目。 */
export interface CodeValueItem {
  codeValue: string;
  codeLabel?: string;
  sortOrder?: number;
}

/** 码集聚合记录(列表展示用)。 */
export interface CodeSetRecord {
  codeSetCode: string;
  name: string;
  valueCount: number;
  status: 'ENABLED' | 'DISABLED';
  preset: boolean;
  /** 版本(组内 MAX)。 */
  version?: number;
  sortOrder?: number;
  description?: string;
  updateTime?: string;
}

/** 启用码集选项(标准字段码值引用下拉,value = code_set_code)。 */
export interface SemanticCodeSetOption {
  codeSetCode: string;
  name: string;
}

/** 启用标准选项(类型/单位/口径/安全引用下拉,编辑弹窗打开时按 kinds 按需加载)。 */
export interface SemanticStandardOption {
  id: number;
  kind: SemanticStandardKind;
  code: string;
  name: string;
  /** 类型标准:标准类型(生效类型提示回填用)。 */
  stdType?: string;
  /** 类型标准:类型编码(单位推荐映射键,仅 TYPE 行)。 */
  typeCode?: string;
  /** 单位标准:单位类型(金额/数量/时间…,单位推荐匹配键,仅 UNIT 行)。 */
  unitType?: string;
}

/** 码集详情记录(编辑用,含码集下全部码值行)。 */
export interface CodeSetDetailRecord extends CodeSetRecord {
  revision: string;
  values: SemanticStandardRecord[];
  /** 存量空码集行按 std_code 独立成组:码集编码可补全(采纳进新码集)。 */
  legacy?: boolean;
  /** 历史数据组内名称不一致:编辑保存后统一为码集名称。 */
  nameInconsistent?: boolean;
}
