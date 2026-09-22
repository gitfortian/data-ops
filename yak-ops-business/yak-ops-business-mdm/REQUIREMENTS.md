# MDM Requirements

> 只描述模块需要什么,不描述怎么实现。按 ticket 追加;行为变更先改本文件再写代码。
> 菜单口径(menu.md):只建 5 个特有菜单(总览/建模/识别/清洗/审批),采集→数据集成、质量→数据质量、血缘→数据血缘、服务→数据服务、标准→语义中心、分析→仪表盘,全部跳转。

## Ticket 50:模块骨架 + 菜单(总览)

- 平台出现"主数据管理"一级入口(项目空间内可用):后端独立 `yak-ops-business-mdm` 模块,自持 Flyway(baseline V1,历史表 `flyway_schema_history_mdm`)与 Maven 聚合/BOM 注册;前端新增"主数据总览"路由与菜单(组 `mdm`,页 `mdm-overview`),权限根 `mdm` + `mdm:read/create/update/delete`,RBAC 生效。
- 本 ticket 不实现任何主数据业务:总览页为占位骨架(统计卡片随 51/55/58/60/62 落地填充);菜单契约测试 `navigationMenuContract.test.ts` 通过。
- 错误码段 44001+(dev-plan D-M3,43001-43018 已被 ResourceErrorCode 占用):`MdmErrorCode` 含 44001 实体不存在/44002 记录不存在/44003 属性不存在/44004 来源不存在/44005 审批失败/44006 分发失败。
- 页面在项目空间上下文内访问,遵循 PROJECT_SCOPE。

## Ticket 51:主数据实体建模

- 管理员进入"主数据管理 → 主数据建模"(menuCode `mdm-modeling`),维护主数据实体(客户/商品/供应商等):增删改查、编码唯一、状态(草稿/生效/停用)、负责人、描述。实体是属性(52)/识别(53)/采集(55)的归属根。
- 实体行为:
  - 创建:编码 `[A-Za-z0-9_]` 1~64 位,项目内唯一,创建后不可改;名称必填(≤128);负责人/描述可空;初始状态 DRAFT。
  - 编辑:名称/描述/负责人可改;编码不可改。
  - 状态流转:草稿→生效→停用→生效(DRAFT 为创建态,离场后不可回退 DRAFT);`changeStatus` 校验状态合法性与流转方向。
  - 删除:物理删除,删除前引用校验(52 属性/53 来源引用,本期校验为空实现,挂点先落服务层);删除靠审计留痕,不建回收站。
  - 查询:分页(编码/名称统一关键词/状态过滤,`POST /page`)与详情(`GET /{id}`);仅本项目空间数据可见。
- 审计:创建/编辑/启停/删除全部落审计(fail-open,不阻断业务),事件命名 `MDM_ENTITY_*`。
- 实体详情页骨架:概览(名称/状态/负责人/创建时间)+ 空态 Tab(属性/记录/采集/质量/分发/血缘/标准/变更,随后续 ticket 填充)。
- 前端:实体列表页(分页/搜索/状态筛选/新建/编辑/启停/删除)+ 详情页骨架;菜单 `mdm-modeling` 本 ticket 注册(V2025),权限 mdm:read/create/update/delete。
- 本 ticket 追加错误码:44007 实体编码已存在/44008 编码格式不合法/44009 名称不合法/44010 状态不合法/44011 删除失败/44012 实体已被引用。

> 后续 ticket 行为要求按 51~62 增量追加(实体/属性/识别/采集配置/采集执行/清洗/分发/服务/审批/治理/总览分析)。

## Ticket 52:主数据属性建模(引用数据标准)

- 管理员在实体详情页"属性"Tab(无独立菜单,menu.md 五)维护主数据属性(客户名称/手机号/地址/等级等):增删改查、排序、必填标记。
- 属性行为:
  - 创建:编码 `[A-Za-z0-9_]` 1~64 位,实体内唯一,创建后不可改;名称必填(≤128);角色 PK/ATTR/RELATION;同一实体 PK 唯一。
  - 类型/单位/码值/安全**必须引用 semantic 数据标准**(类型/单位/安全为标准 ID,码值为码集编码),经 `StandardQueryApi` 消费;不允许自由填写(与 semantic 决策 A9 一致:松散 ID 引用,展示名经 SPI 解析)。
  - 引用校验:类型标准必须 kind=TYPE 且 ENABLED;单位=UNIT;安全=SECURITY;码集=项目内启用码集(existsCodeSet)。
  - 编辑:名称/角色/数据类型/标准引用/必填/业务描述/排序可改;编码不可改;改 PK 时校验唯一。
  - 删除:物理删除,删除前引用校验(54 采集映射 / 56 清洗规则引用,本期校验为空实现,挂点先落服务层)。
  - 查询:按实体列出全部属性(sort_order 排序);仅本项目空间数据可见。
- 列表展示:标准引用展示名由服务端经 SPI 解析(`名称（编码）`),前端不预载标准字典(semantic 32.1)。
- 审计:创建/编辑/删除全部落审计(fail-open),事件命名 `MDM_ATTRIBUTE_*`。
- 前端:实体详情"属性"Tab 落地(列表 + 新建/编辑弹窗,弹窗打开时按需加载标准 options / 码集 options,复用 semantic 前端服务)。
- 本 ticket 追加错误码:44013 标准引用不合法/44014 属性已被引用/44015 属性编码已存在/44016 PK 属性已存在/44017 属性角色不合法。

## Ticket 53:主数据识别(数据源发现/候选/确认)

- 管理员进入"主数据管理 → 主数据识别"(menuCode `mdm-identification`),选择数据源扫描其表结构,系统按识别规则(表名/字段名与实体名称/编码语义匹配)给出候选主数据,用户确认为某实体的主数据来源或排除。
- 识别行为:
  - 扫描:选数据源 → 服务端经 datasource 公共契约读表清单(库/模式/表/备注),数据源不可用/连接失败时给出明确提示;表已绑定某实体时标记"已确认"。
  - 候选规则(服务端规则,先规则后 AI;AI 匹配范围外):表名分词(非字母数字分隔 + 驼峰)与项目实体编码/名称(拉丁)匹配;命中则标注匹配实体。
  - 确认:候选/任意表可"确认为主数据"(选实体 + 角色 MAIN/AUXILIARY),生成 `mdm_source` MAIN 绑定;确认前校验表存在(元数据读取)与唯一(`(project_id, entity_id, datasource_id, source_database, source_schema, source_table)` 唯一)。
  - 排除:当前扫描会话内不再提示(前端会话态,不落库)。
  - 解绑:删除来源绑定;删除前引用校验(54 采集配置引用,本期校验为空实现,挂点先落服务层)。
  - 查询:已确认来源列表(按实体筛选),展示实体编码/名称、数据源名、库/模式/表、角色。
- 复用:元数据读取/连通性经 datasource 公共契约(DataSourceCatalogReader/DataSourceReader),不重复造轮子;来源绑定为松散 ID(数据源 ID),展示名经 datasource 契约解析。
- 审计:确认/解绑落审计(fail-open),事件命名 `MDM_SOURCE_*`。
- 前端:识别页(数据源选择 + 扫描 + 候选标注 + 确认弹窗 + 来源列表 + 解绑);菜单 `mdm-identification` 本 ticket 注册(V2026)。
- 本 ticket 追加错误码:44018 来源重复绑定/44019 数据源扫描失败/44020 来源已被引用。

## Ticket 54(修正):主数据采集(复用 sync)

- 采集配置与执行**完全复用「数据集成」(sync)**,MDM 不建字段映射编辑器、不建执行引擎、不建调度(design.md 3.3:同步任务/字段映射/调度 = 复用 sync;menu.md:采集不建菜单、详情页"采集配置"Tab 跳数据集成)。
- MDM 采集侧只保留主数据特有的部分:
  - **来源→实体绑定**(53 已有,`mdm_source` 核心列,不存字段映射/方式/频率/配置状态——V5 已回退)。
  - **采集状态展示**:实体详情页"采集"Tab 展示来源绑定列表(角色/采集状态占位)+ "前往数据集成配置采集"跳转;总览"采集状态"卡片(menu.md 三.1)随 55 接入 sync 后回写。
- 采集状态(最近采集时间/状态)经 sync「主数据」标签任务执行状态查询回写(离线+实时,dev-plan D-M11),随 55 落地。
- 前端:详情页"采集"Tab(`CollectStatusTab`,来源列表 + 跳转),无独立菜单。

## Ticket 55(方案 A):主数据统一记录落地(主数据加工任务)

- 统一主数据表 `mdm_record`(master_id 跨系统唯一、attributes 属性值、source_ids 各系统原始 ID、版本,requirement.md 2.4)由 **主数据加工任务**生成:MDM 按实体/属性/来源绑定生成加工任务(含 master_id 唯一、source_ids 多源关联、增量更新 version 递增),交**数据开发**执行写入,参照 modeling 44"派生建模 → 生成加工任务 → 任务目录(task-catalog)"模式;MDM **不建执行引擎、不建采集、不建调度**。
- 采集执行与状态彻底复用 sync:sync 任务(离线+实时)打「主数据」标签(`is_master_data` + 实体维度),MDM 只查询展示标签任务执行状态(离线 `OfflineExecutionEvent`/实时执行记录),采集状态回写 `mdm_source` 与总览"采集状态"卡片(跳数据集成)。
- 跨模块契约先行:mdm ↔ sync(标签任务查询)与 mdm ↔ 数据开发/task-catalog(加工任务生成与注册)契约文件同步更新。
- `mdm_record` 表 Flyway V6;(project_id, entity_id, master_id) 唯一约束;仅 ACTIVE 记录对外可见(MERGED/DELETED 保留可追溯)。
- **55a(MDM 核心,本票)**:生成主数据加工 SQL(读来源表,列名=属性编码匹配;master_id=确定性哈希(实体编码+PK 属性值),多源 PK 值一致即统一;source_ids 记录各来源原始 ID;attributes 按属性组装;UPSERT 到 mdm_record,version 递增);记录只读分页查询(实体详情"记录"Tab);前端一键"生成加工 SQL"→ 复制或跳数据开发新建任务草稿(复用 DevelopmentTaskApi.SaveDraftRequest,22 模式)。
- **55b(sync 标签,依赖 sync 契约改动,后续增量)**:sync 离线+实时任务增加「主数据」标签(`is_master_data`+实体维度)与执行状态查询 API,MDM 查询展示采集状态(离线 OfflineExecutionEvent/实时执行记录),回写 mdm_source/总览。

## Ticket 56:主数据清洗·去重与合并

- 管理员进入"主数据管理 → 主数据清洗"(menuCode `mdm-cleansing`),按实体配置去重规则(如手机号相同 + 姓名相似)发现重复组,对重复组执行合并(选主记录:属性合并 + source_ids 合并,其余记录 status=MERGED,version 递增)或保留(忽略该组);质量检查(完整性/格式)跳数据质量,清洗只管去重、合并(标准化/补全随 57)。
- 去重规则(实体级配置,`mdm_clean_rule` V7):
  - 创建:rule_type 固定 DEDUP(本期);rule_name 必填(≤128),实体内唯一;rule_expr JSON 配置匹配字段与方式:`{"fields":[{"attrCode":"mobile","matchType":"EXACT"}],"condition":"AND"}`,matchType EXACT(精确)/FUZZY(trim+小写规范化);同一字段仅出现一次;字段必须是实体已定义属性;至少一个字段。
  - 编辑/启停/删除/排序;删除前引用校验(合并日志引用,本期校验为空实现,挂点先落服务层)。
  - 查询:按实体列出全部规则。
- 去重发现(服务端聚合,禁止无界 list 后内存统计,遵循 home-overview-contract):按规则扫描实体 ACTIVE 记录,按 matchKey(EXACT 字段原值 + FUZZY 规范化值)服务端分组,组内 ≥2 为重复组;返回重复组(组内记录、匹配依据、置信度),分页展示。
- 合并预览:展示组内各记录属性/source_ids 对比与合并结果(主记录属性优先,其余记录非空补全;source_ids 去重合并),确认后执行。
- 合并执行:选主记录 → 主记录 attributes/source_ids 合并、version+1;其余记录 status=MERGED、version+1;写 `mdm_merge_log`(V8:project_id/entity_id/rule_id/master_record_id/merged_record_ids/operator_id/result/时间)可追溯;合并不可回滚但记录保留;审计 `MDM_MERGE_*`。
- 前端:清洗页(实体选择 → 规则列表/新建/编辑/启停/删除 → 执行去重发现 → 重复组分页展示 → 合并预览弹窗 → 合并执行/保留);菜单 `mdm-cleansing` 本 ticket 注册(V2027)。
- 本 ticket 追加错误码:44022 去重规则不存在/44023 去重规则不合法/44024 合并请求不合法。

## Ticket 57:主数据清洗·标准化与补全(最小化设计,最大化复用)

- 管理员在清洗页(mdmcleansing)除去重规则外,可按实体配置标准化规则与补全规则;规则与去重共用 `mdm_clean_rule` 表(V7 已支持 rule_type 枚举 STANDARDIZE/COMPLETE),无新 Flyway 迁移。
- 标准化规则(STANDARDIZE):
  - 创建:rule_name 必填(≤128),实体内唯一;rule_expr JSON 配置属性值映射表:`{"fields":{"gender":{"M":"1","F":"2"}}}`,键为属性编码,值为源→目标映射;属性必须是实体已定义属性;映射不可为空。
  - 预览:扫描实体 ACTIVE 记录,按映射计算受影响记录数与变更明细(前 N 条)。
  - 执行:批量更新受影响记录的 attributes(version 递增,status 保持 ACTIVE);写审计 `MDM_CLEAN_APPLY_STANDARDIZE`。
- 补全规则(COMPLETE):
  - 创建:rule_expr JSON 配置默认值:`{"defaults":{"grade":"普通"}}`,键为属性编码,值为默认值;属性必须是实体已定义属性;默认值不可为空。
  - 预览:扫描实体 ACTIVE 记录,统计缺失/空值属性会被填充的记录数与变更明细。
  - 执行:批量更新受影响记录,version 递增;写审计 `MDM_CLEAN_APPLY_COMPLETE`。
- 通用规则管理(与 DEDUP 共用):CRUD、启停、排序、按实体查看;创建时按 ruleType 分派校验逻辑(DEDUP 走原匹配字段校验,STANDARDIZE/COMPLETE 走属性编码+表达式校验)。
- 复用:复用现有 `mdm_clean_rule` 表(无新迁移)、复用 audit 服务(无新执行日志表)、复用现有错误码 INVALID_CLEAN_RULE(44023)。
- 前端:清洗页规则列表支持全部类型展示;标准化/补全新建弹窗(属性选择 + 映射/默认值配置)+ 预览弹窗 + 执行确认;复用去重规则的端点结构。
- 本 ticket 不追加新错误码(复用 44023 INVALID_CLEAN_RULE)。

## Ticket 58:主数据分发(配置/执行/监控,最小化设计)

- 管理员在实体详情页“分发配置”Tab(无独立菜单,menu.md 五)维护分发配置:目标系统 + 分发方式(API/MESSAGE/FILE) + 频率(MANUAL/DAILY/HOURLY) + 范围(FULL/INCREMENTAL)。
- 分发配置(`mdm_distribution` V9):
  - 创建:target_system 必填(≤64),实体内(目标系统+方式)唯一;mode 必填;初始状态 DRAFT。
  - 编辑:target_name/mode/freq/scope 可改;target_system 不可改。
  - 状态流转:草稿→生效→停用→生效(DRAFT 为创建态,不允许直接 DRAFT→DISABLED)。
  - 删除:物理删除,审计留痕。
- 分发执行(最小化):
  - 手动触发:仅 ACTIVE 配置可执行;单目标失败不影响其他目标。
  - API 方式:查询实体 ACTIVE 记录数作为占位(实际推送待 data-service 集成,后续增量)。
  - MESSAGE/FILE 方式:为占位,后续参照 alert/storage 能力。
  - 执行结果写配置表(last_distribute_time/count/fail),不建独立日志表。
- 分发监控:配置表自带 last_distribute_time/status,按实体查看;总览“分发状态”卡片展示活跃配置数(服务端聚合)。
- 复用:复用现有 DISTRIBUTE_FAILED(44006)错误码;无独立菜单(跳数据服务)。
- 审计:创建/编辑/启停/删除/执行全部落审计(fail-open),事件命名 `MDM_DISTRIBUTION_*`。
- 本 ticket 不追加新错误码(复用 44006 DISTRIBUTE_FAILED)。

## Ticket 59:主数据服务(查询 API/订阅/缓存,最小化设计)

- 查询 API(无独立菜单,跳数据服务):
  - 按 master_id 查单条 ACTIVE 记录:`GET /api/v1/mdm/service/entity/{entityId}/record/{masterId}`,仅返回 ACTIVE 状态。
  - 条件搜索分页:`POST /api/v1/mdm/service/entity/{entityId}/search`,复用 record 分页(仅 ACTIVE)。
  - 缓存/API 管理跳 data-service,不实现。
- 订阅管理(实体详情页分发配置/订阅区):
  - `mdm_subscription`(V10):subscriber_code 必填(≤64),实体内(订阅方)唯一;notify_mode EVENT/WEBHOOK。
  - 创建:初始状态 ACTIVE;编辑:名称/通知方式;状态切换:ACTIVE↔DISABLED;删除:物理删除。
  - 订阅数服务端聚合(总览卡片)。
- 变更通知:占位(审计记录),实际推送待后续增量。
- 复用:复用 RECORD_NOT_FOUND(44002)错误码;无独立菜单(跳数据服务)。
- 审计:创建/编辑/启停/删除全部落审计(fail-open),事件命名 `MDM_SUBSCRIPTION_*`。
- 本 ticket 不追加新错误码。

## Ticket 60:主数据审批(申请/审批流/版本,最小化设计)

- 变更申请:选实体/记录 → 变更类型(CREATE/UPDATE/MERGE/DELETE) → 变更内容(JSON,属性值对比) → 提交审批。
- `mdm_change`(V11):project_id/entity_id/master_id/change_type/change_content/approval_level(1/2)/approval_status/applicant/approver/approval_comment/approval_time。
- 审批流:一级(数据管理员)→ 二级(数据治理负责人),可配置(1 或 2 级,暂用固定 1 级)。
- 审批处理:
  - 通过:UPDATE 类型应用变更到 mdm_record(JSON patch 合并 attributes,version 递增);DELETE 类型标记 DELETED;CREATE/MERGE 为占位(不直写 record)。
  - 拒绝:记录审批意见,变更状态 REJECTED。
  - 撤回:仅申请人可撤回 PENDING 申请。
- 审批列表:分页查询,按状态/申请人筛选;待我审批/我发起的/全部。
- 版本管理:按 master_id 列出变更历史(只读展示)。
- 菜单:注册 `mdm-approval`(V2028),路由 `/mdm/approval`。
- 复用:复用 RECORD_NOT_FOUND(44002)错误码。
- 审计:提交/通过/拒绝/撤回全部落审计(fail-open),事件命名 `MDM_CHANGE_*`。
- 本 ticket 不追加新错误码。

## Ticket 61:主数据治理(质量/血缘/权限,最小化设计)

- 无独立菜单:入口在实体详情页 Tab,质量/血缘跳平台模块。
- 治理概览(服务端聚合):上游来源数(MdmSource)、ACTIVE 记录数、下游分发/订阅目标数、待审批数;每张卡片独立容错(-1=查不到)。
- 权限:平台 RBAC(md m:read/create/update/delete)+ 实体 owner 轻量控制(owner 匹配或无 owner 时放行)。
- 复用:质量复用 quality 模块,血缘复用 lineage,权限复用 security RBAC;不追加新表/错误码。
- 端点:`GET /api/v1/mdm/governance/entity/{entityId}` + `GET /api/v1/mdm/governance/entity/{entityId}/owner-check`。

## Ticket 62:主数据总览与分析(分布/变更/使用,最小化设计)

- 无独立菜单:总览即分析入口(`mdm-overview`)。
- 总览卡片(服务端聚合):实体记录数(ACTIVE)、分发目标数、订阅方数、待审批数;每张卡片独立容错(-1=查不到)。
- 全量总览:遍历全部实体,逐实体聚合卡片;汇总总实体数/总记录数/总待审批。
- 单实体概览:指定实体的聚合卡片。
- 复用:统计复用现有 Repository 聚合方法;不追加新表/错误码。
- 端点:`GET /api/v1/mdm/overview` + `GET /api/v1/mdm/overview/entity/{entityId}`。
