# Modeling Requirements

本文定义建模模块的能力范围与非目标。完整需求规格与需求侧决策见 [docs/model/modeling-requirements.md](../../docs/model/modeling-requirements.md),开发排期见 [docs/model/dev-plan.md](../../docs/model/dev-plan.md)。

## Capabilities(已确认范围)

| 能力 | 需求编号 | 计划 ticket |
| --- | --- | --- |
| 模型目录与 CRUD、目录树/标签/搜索、回收站 | FR-01 | 02/03/04 |
| 物理模型表结构在线编辑(字段/主键/索引/分区/表属性) | FR-04 | 05/06 |
| 按目标方言的合法性校验 | FR-04 | 07 |
| 逆向建模:从数据源元数据导入生成模型 | FR-08 | 08/26 |
| 建库脚本生成(多方言 DDL,仅生成不执行) | FR-10 | 09/10 |
| ER 关系画布(实体呈现 + 关联关系编辑) | FR-05 | 11/12 |
| 模型版本与发布(草稿→发布→版本 diff,对齐数据开发) | FR-03 | 13/14 |
| 逻辑模型(实体/属性/关系,独立实现) | FR-06 | 15/16 |
| 逻辑→物理一键生成(单库单方言) | FR-07 | 17/18 |
| 来源映射配置(字段级 + 转换表达式)与可视化 | FR-12 | 19/20 |
| 模型加工任务(映射生成 SQL,交数据开发执行) | FR-13 | 21/22 |
| 血缘引导式登记与可视化 | FR-14 | 23/24 |
| 模型统一视图(详情页整合) | FR-17 | 25 |
| 源端结构变更感知与通知(仅引用源表,仅通知) | FR-15/16 | 27/28 |
| 项目空间全域模型视图 | FR-18 | 29 |

## Ticket 08:逆向导入(数据源存量表 → 模型)

- 用户在模型工作台点"逆向导入":选择数据源 → 浏览/搜索表 → 勾选表 → 预览列结构 → 导入为模型。
- **元数据只经 datasource 公共契约 `DataSourceCatalogReader`**(listDatabases/listTables/listColumns),不读其内部表。
- 每张表导入为一个模型:模型编码=表名(导入时可改),名称=表备注或表名;列映射 typeName→dataType、size→length、scale→scale、nullable→nullable、remarks→comment、主键列→主键。
- 导入需指定目标方言(默认按数据源 dbType 同名映射,POSTGRES→POSTGRESQL;无映射要求用户选择)与可选目录。
- 编码冲突跳过不中断:结果报告 created/skipped/failed;每张表独立事务,单表失败不影响其他表。
- 审计:导入操作落审计(SEMANTIC 同款 fail-open 门面)。

## Ticket 38:标准自动套用(ODS 逆向导入,modeling 消费 semantic)

- 导入请求增加 `layerCode`(默认 ODS):经 semantic `LayerConfigApi` 定位分层(库/数据源/命名标准/默认分区);分层不存在时降级为不定位并提示,不阻断导入。
- **标准自动套用**(经 semantic `StandardRecommendApi`,不直读 semantic 表):
  - 每列按列名/类型跑推荐:类型候选取首个 → std_type_id;安全候选取首个 → std_security_id;命名校验命中或建议 → std_naming_id。
  - **冷启动降级(决策 B)**:无候选或推荐失败时保持原值,计入 degraded 统计;导入流程永不因推荐失败中断。
  - 结果报告附 standardApply 统计(各引用套用数/降级数),为 40 沉淀与 42 上报提供输入。
- 自动补充技术字段:`etl_time`(DATETIME)、`src_system`(VARCHAR 64)、`src_table`(VARCHAR 128)——已存在则不重复添加。

## Ticket 44:按业务过程派生建模(初始化草稿,汇入现有管道)

- 派生请求:业务过程(processId)+ 目标分层(layerCode)+ 模型编码/名称/方言 + 勾选的标准字段(可改落地字段名/类型/转换表达式)。
- **建模 V11**:`yak_modeling_model` 增加 `process_id`(semantic 业务过程松散引用)与 `layer_code`(目标分层编码);`StandardQueryApi`(semantic SPI)解析标准类型字串。
- 派生动作 = 初始化草稿并汇入既有管道(决策 A):
  1. 校验业务过程/分层存在(经 ProcessApi/LayerConfigApi);编码冲突拒绝;
  2. 走 02 create + 05 save 全量替换管道生成物理模型草稿(列 = 勾选字段,落地字段名/类型来自标准字段与分层规范,std_* 列按字段引用写入);
  3. **自动写入 43 分层映射**(决策 D);
  4. `process_id`/`layer_code` 落模型行;
  5. **经 23 管道登记血缘**(字段资产 properties 携带 stdProcessFieldId,45 自动建立)。
- 校验命名/类型(07)/血缘完整性;派生失败整体回滚(单事务)。
- **role × is_required 派生默认勾选映射（35/44 联动,约束 4）**:PROCESS+必需=默认勾选为业务主键;DIMENSION+必需=默认勾选为维度;METRIC+必需=默认勾选为度量;任意非必需=不默认勾选,但仍在候选列表由用户挑选。

## Ticket 47:业务过程主线视图(modeling 消费 semantic)

- 按业务过程查看各层模型覆盖:过程清单经 `ProcessApi.listProcesses`(semantic),覆盖/状态聚合在 modeling 服务端完成(禁无界 list 后内存统计)。
- 每个过程输出:各层(按 44 写入的 layer_code 分组)已建模型数与模型清单(编码/名称/状态);未建层的分层编码显式列出(资产盘点:哪些过程未建 DWS/ADS)。
- 与统一视图(25)打通:模型行可跳转模型详情;semantic 业务过程详情可跳转本视图(前端路由参数)。
- 独立容错:semantic 过程清单失败时报错明确,不显示为"无过程"。

## Non-goals(本期不做,需求侧决策确认)

- 建库脚本在平台内直接执行(D3):仅生成/复制/下载。
- 变更提示后一键同步字段(D4):仅通知,由用户自行调整模型。
- 数仓分层(ODS/DWD/DIM/DWS/ADS)、主题域、命名规范(D6):下一期,首期用目录树 + 标签。
- 血缘自动登记(D7):必须引导用户选择。
- 模型与源库 diff 对比及自动同步(D9):仅导入。
- 一个逻辑模型落地多库/多方言(D10):单逻辑模型仅落地单个目标库、单个方言。
- 本体建模 / 语义层(口径字典)关联(D1):当前分支已移除该功能,建模模块独立实现。
- 独立任务执行引擎(D2):加工逻辑一律生成 SQL 交数据开发任务体系执行。

## 执行约束

所有实现遵守 [docs/model/dev-plan.md 第 0 节硬性开发约束](../../docs/model/dev-plan.md):契约先行、`data-ops-ui` 下 `.md` 契约文件只读、项目全局规范(PROJECT_SCOPE / home-overview-contract / 菜单契约等)。

## M4 增补(ticket 30 起)

- **数据标准引用预留**(modeling V8):`yak_modeling_model_column` 增加 `std_type_id`/`std_naming_id`/`std_code_id`/`std_unit_id`/`std_caliber_id`/`std_security_id` 六列——可空 BIGINT、无物理外键;字段读写 DTO 透传(值暂为 null,由 38/39/44 写入)。引用的语义由独立模块 `data-ops-business-semantic` 定义,建模仅存松散 ID 并经其 SPI 解析展示名(依赖方向见 [DEPENDENCIES.md](./DEPENDENCIES.md))。
- Non-goals 中"数仓分层/命名规范下一期"由 M4 的 semantic 模块承接(ticket 30~37/39,见 [docs/model/m4-integration.md](../../docs/model/m4-integration.md));首期模型组织仍是目录树 + 标签。

## Ticket 39:DWD 字段编辑标准套用(modeling 消费 semantic SPI)

- 表结构编辑器推荐端点:`POST /api/v1/modeling/models/{id}/standards/recommend`(字段名/类型/角色)——**经 semantic `StandardRecommendApi` SPI,不直读 semantic 表**;modelId 不存在或非本项目时拒绝。
- 推荐不阻断编辑流程:推荐结果仅提示,用户可一键采纳(写入列的 std_* 松散引用,随 02/05 保存管道持久化)或忽略。
- 前端:结构编辑器"标准助手"抽屉——命名校验(不符合提示+建议标准)、类型/码值/单位/口径/安全候选,一键套用填充当前行 std_* 字段;保存仍走既有全量替换管道。

## Ticket 19:来源映射配置(字段级映射与转换表达式)

- 用户为物理模型在线配置来源映射:选择源端数据源/库/表(复用 `DataSourceCatalogReader` 公共契约),逐字段建立"源表.字段 → 模型.字段"一对一映射;每个目标列至多一条映射。
- 每条映射可附转换表达式;表达式语法校验:标识符/字面量/算术与比较运算/函数调用形式 `fn(arg,...)`、括号配平;禁止 SQL 终结符与 DML/DDL 关键字;常用函数由前端提示列表提供。
- 映射列表增删改、批量清空、持久化;未映射字段在列表中明确标识。
- **M4 预留(V9)**:映射表含 `std_process_field_id`(可空,semantic 标准字段 ID 松散引用)——43/44 派生记录复用本表,DTO 透传(暂为 null)。

## Ticket 43:字段分层映射(定位:派生动作的自动记录)

- modeling 侧新增分层映射表(V10):记录 `process_field_id`(semantic 标准字段)+ `layer_id`(semantic 分层)+ 落地字段名/类型/来源字段/转换表达式;唯一键 (project_id, model_id, process_field_id, layer_id, layer_field_name)。
- **layer 以 37 分层配置为源**(存 layer_id 引用,非自由字符串);**process_field_id 必须存在**(经 ProcessApi 校验);转换表达式复用 19 的语法守卫。
- 展示名解析:标准字段名经 `ProcessApi`、分层名经 `LayerConfigApi` 批量解析——modeling 不 join semantic 表。
- 查询:按模型列出全部映射;按标准字段查看其各层落地(45 向下血缘的数据基础)。
- 编辑为补录;**自动写入由 44 派生时执行**(44 验收)。可视化=列表视图(按标准字段/分层过滤)。

## Ticket 23:血缘引导式登记(modeling → lineage)

- 在模型详情提供常驻"登记血缘"按钮;用户确认后才登记(决策 D7:不自动登记;发布成功后的提示入口随 13 落地补挂)。
- 登记经 lineage 公共契约 `LineageRegistrationService`(upsert by assetKey,幂等):模型 → TABLE 资产(assetKey `modeling:model:{id}`),字段 → COLUMN 资产(assetKey `modeling:model:{id}:column:{name}`,parent=表资产),表→字段 CONTAINS 关系。
- 字段资产 properties 携带 `stdProcessFieldId`(来自 43 分层映射的 std_field_id 预留)与六类 std_* 引用——45 标准字段血缘的数据基础。
- sourceType=MODELING;sourceProjectId 取可信上下文,登记幂等可重复执行。

## Ticket 45:标准字段级血缘(基于 43 映射 + 23 管道)

- 登记模型血缘时自动追加:对 43 分层映射中的每条记录,若其落地字段名与模型字段匹配,则:
  - 注册语义侧标准字段资产(assetKey `semantic:field:{id}`,sourceType=SEMANTIC,名称经 ProcessApi 解析);
  - 建立 落地字段资产 DERIVES_FROM 标准字段资产 关系(向上=物理字段来自哪个标准字段;向下=标准字段各层落地,由关系方向天然支持)。
- 查询:按标准字段查看各层落地(43 的 by-field 端点)+ 血缘图(复用血缘工作台可视化,不重复建设)。
- 全部走 23 的 upsert 管道,幂等可重复执行;不重复造血缘存储。

## Ticket 46:变更影响分析(基于血缘与映射,后端归 modeling)

- 标准字段变更 → 影响范围:经 43 分层映射(by-process-field)列出受影响模型与落地字段。
- 物理字段(来源列)变更 → 影响范围:经 19 来源映射按源表/源列反查受影响模型与目标字段。
- 查询为服务端过滤(项目绑定),前端提供影响范围预览与受影响项跳转(批量打开模型);不提供自动修改(与 A5/D9 一致)。
- 影响分析为独立页面(/modeling/impact);semantic 标准详情页的入口需要"标准 → 引用它的标准字段"跨表反查,随后续增强接入(本期标准详情页不挂错误参数的入口)。

## 模型工作台交互优化(2026-09-17 界面评审)

- **创建入口合并**:单一主按钮"新建模型"(下拉):按过程派生(副标题 从业务过程派生 DWD/DWS/ADS)、逆向导入(副标题 从源表导入 ODS)、手工建模型(副标题 从零建模型)。
- **派生前置检查(不阻断)**:派生页选定非 ODS 分层时,若该业务过程无 ODS 模型(经 47 主线覆盖查询),提示"请先逆向导入 ODS 模型"并提供跳转入口(`/modeling?action=import` 自动打开逆向导入抽屉)。
- **列表加列**:分层(layer_code)、业务过程(process_name)、目标库(分层配置的 database_name);名称经 semantic SPI(ProcessApi/LayerConfigApi)解析,列表接口仅扩展 VO 与查询参数,**不动表结构**。
- **筛选扩展**:分层(layer_code)、业务过程(processId)、状态(status)、目标库(由分层派生,前端筛选)、标签(保留);查询参数后端支持 layerCode/processId/status。
- **状态中文化**:DRAFT→草稿(当前仅此态;PUBLISHED→已发布、DISABLED→停用 随 13 发布版本落地,标签映射预留);状态标签带 tooltip 说明含义。
- **视图切换**:列表 / 主线视图用视图切换(Segmented)呈现,不再是独立入口按钮。
- **按钮分组**:创建类(新建模型下拉)/ 视图类(Segmented)/ 管理类(回收站、标签管理收进"更多"菜单)。
- **目录树 vs 标签职责提示**:目录树=层级分类(交易/订单),标签=横向标记(核心/临时);界面上以 tooltip 说明。
- **列表布局收敛(2026-09-17 二轮评审)**:目标方言+目标库合并为"目标"列(`db（方言）`);操作栏固定 180px,常用"编辑"平铺、详情/移动/标签/删除收进"更多"下拉(删除红色+二次确认);更新时间列 `nowrap` 且格式去秒(`yyyy-MM-dd HH:mm`);低频列(目标/所属目录/标签)支持"列设置"显隐(默认全显,localStorage 持久化)。
- **模型详情页优化(2026-09-17 界面评审,四组)**:
  - **概览**:补 分层/目标库/数据源(经 semantic SPI 与分层配置解析);业务过程显示名称或"未关联"(不暴露 ticket 编号);状态中文化(DRAFT→草稿 等)。
  - **表结构 Tab 顶部**:去除 Tab 内重复的模型名/编码标题与"返回列表"按钮(统一视图顶部已有),去除"来源映射/登记血缘/分层映射"按钮(Tab 导航已有),仅保留 建库脚本/保存表结构;物理表名默认=模型编码;空字段时轻量空态不渲染表头;索引区默认折叠。
  - **分区配置**:主键与分区区域增加"是否分区"开关,默认关闭(隐藏分区配置);开启后显示分区类型/分区字段/分区表达式,并自动填充默认值(RANGE / event_time / dt);分区字段在未选分区类型时禁用;保存时开关关闭则不分区。
  - **字段列表**:类型改下拉(按方言候选,07);长度/精度按类型自动带出(VARCHAR 带长度、DECIMAL 带精度);"可空"默认不勾选;单行时隐藏上移/下移;"标准"列更名"数据标准",已引用时展示引用名称;新增 批量添加/批量删除。**"标准字段"列暂缓**:物理字段↔标准字段关联在 43 分层映射,结构 VO 未暴露,需后端扩展后落地。
  - **标准助手抽屉**:先推荐后沉淀(推荐区固定展示五组候选,空组显示"不适用");标题用字段名,未填字段名时提示"请先填字段名";编码自动生成;字段"标准类型"更名"数据类型",默认从字段带出。
- **派生源表匹配校验(2026-09-17 评审,44 增强)**:派生字段来源改为**业务过程关联源表字段**(36),与标准字段集(35)匹配后纳入草稿——匹配规则(优先级:字段名精确/模糊相似度/注释/类型)前端规则实现(后续可升级 41 AI);匹配到=规范字段自动纳入并标注,未匹配=warning、默认不纳入。未匹配字段三选一:沉淀为标准字段(40:创建 semantic 字段并绑定业务过程后纳入)/关联到已有标准字段(手动指定后纳入)/忽略(排除);未处理的未匹配字段生成时二次确认不纳入。生成 payload 仍为 processFieldId 驱动(44 既有管道),仅字段选择来源与匹配展示变更。
- **目录树语义视图(2026-09-17 评审)**:左侧目录树顶部加视图切换(目录/按业务域/按分层),默认"按业务域",选择持久化(localStorage)。按业务域:一级业务域、二级业务过程(数据来自 semantic BusinessDomainApi/ProcessApi),点过程 → 右侧按 processId 筛选、点域 → 按该域全部过程筛选;按分层:一级分层(ODS/DWD/DWS/ADS,来自 LayerConfigApi),点分层 → 按 layerCode 筛选。左侧粗粒度导航 + 右侧细粒度筛选可叠加;业务域/分层数据只读引用语义中心,不重复定义,本地目录视图(归类 CRUD)保留为第三档。空态提示并跳转语义中心配置。后端列表接口新增 processIds 列表过滤参数(域级,无表结构变更)。

## 逆向导入字段自动填充(2026-09-17 评审)

- **导入即产出字段**:一个表的"建模型 + 写表结构 + 落分层"在同一事务内完成(每表独立事务,单表失败不影响其他表),失败整体回滚——不再出现"模型建好了但表结构为空"的孤儿模型。
- **已存在模型自动补字段**:导入编码命中已有模型时,若该模型**尚无任何字段**,按源表列自动补全表结构并回报"已补全字段";若已有字段则跳过(不覆盖人工编辑)。重复导入因此成为可用的修复手段。
- **目标分层落库**:导入请求的 layerCode(缺省 ODS)写入模型 layer_code,仅在该模型尚无分层时写入;分层不可解析时降级不阻断。
- **导入结果报告扩展**:`created / filled / skipped / failed` 四类结果 + 每张表的 `models[]{table, modelId, action}`,供前端展示"打开模型"直达详情。表结构与 05/03/V5 一致,无表结构变更。
- 导入自动补充技术字段 `process_time`(数据处理时间)与 `event_time`(业务事件时间);标准自动套用(38 类型/命名/安全 + 降级统计)规则不变,补字段路径同样套用。

## 字段继承与治理(44 派生,2026-09-17 定稿)

- **继承起点改为 ODS 模型**:派生字段来自"该源表对应的 ODS 模型"字段,标准字段关联(`std_field_id`)随字段一起继承;不再从数据源源表列重新做模糊匹配。匹配规则在**后端单一实现**(38/44 共用),前端只做展示与治理决策。
- **选表按 36 角色**:`MAIN` 唯一且必须存在(缺失/多个阻断);`DETAIL` 按绑定顺序追加;`DIM` 默认不继承、界面可显式勾选(退化维度)。定位 ODS 模型按 `(source_datasource_id, source_table)` 精确匹配 `layer_code='ODS'`,未就绪分组提示,不静默跳过。
- **字段规则**:排除技术列 `etl_time/src_system/src_table/dt`;同名字段先到先得(MAIN 优先,再按绑定顺序)并标注合并来源;顺序 = MAIN 字段序 → DETAIL 字段序(按 ODS 模型 sort_order);类型沿用 ODS 类型,目标方言不支持时经类型标准 `source_mapping` 兼容映射,仍不支持则阻断并指明字段。
- **preview 只读接口**:按源表分组返回字段清单(角色/ODS 模型/命中标准字段与匹配方式/冲突/技术列标记/未就绪源表)、命中与未命中计数、治理率、以及"同过程同层已有模型"冲突。未命中字段三动作:沉淀(40)/关联已有/忽略;生成前对未处理未命中二次确认。
- **落库(同一事务)**:模型 + 字段(含 `std_field_id`)+ 19 来源映射(源=ODS 模型绑定的源表/列,可校验才写)+ 43 分层映射(`process_field_id = std_field_id`;为空则不写并计入未治理)+ 血缘登记;**治理回填**:本次已确定而 ODS 列尚空时回写 `std_field_id`。
- **治理率** = 已关联标准字段的落地字段数 / 总落地字段数,派生结果与模型详情展示;发布门禁默认只提示不阻断。
- **防重**:同一"业务过程 + 分层"已有模型时阻断派生并指向既有模型;增量合并留待后续。
- **表结构变更(已确认)**:`yak_modeling_model` 增 `source_datasource_id/source_database/source_table`(补齐 08 来源标记);`yak_modeling_model_column` 增 `std_field_id`。均为可空列 + 索引,无物理外键。

- **来源标记补齐(2026-09-17)**:重复导入命中已有字段的模型不改动其结构,但来源标记为空时补齐来源绑定(迁移前导入的历史模型没有该标记,44 派生定位不到对应 ODS 模型,重导又被跳过);只写空列,不覆盖人工配置。

## 派生按目标分层解析上游(49,2026-09-17)

- **上游按层解析**:派生先按目标分层确定上游,再决定字段继承来源——DWD/DIM ← 该源表对应的 ODS 模型(按 `source_*` 精确反查,沿用 44 规则);preview 显式返回 `upstreamLayer` 与支持状态,不再隐含硬编码 ODS。
- **支持矩阵**(以 37 分层配置的编码为准,未登记的自定义分层按不支持处理):`ODS` 由逆向导入产生不经派生;**DWD/DIM 支持**;**DWS 待结构化聚合(51)**;**ADS 待应用绑定与多上游 join(52)**。
- **阻断是服务端硬校验**:preview 返回 `supported=false` + `unsupportedReason`(说明上游是什么、缺什么能力、指向哪张票);derive 再次校验并抛 `LAYER_DERIVE_UNSUPPORTED`(41916),不写任何数据。前端仅提前展示与禁用按钮。
- 治理/映射/血缘管道不变:支持的分层仍走继承 → 标准字段关联 → 19/43 映射 → 回填 ODS → 血缘登记。

## 维表约定字段(50,2026-09-17)

- **DIM 目标默认补结构性约定列**:代理键 `dim_<过程编码>_sk`(始终)、SCD2 时补 `start_time`/`end_time`/`is_current`;`scdType`(`SCD1` 默认 | `SCD2`)仅对 DIM 生效,非 DIM 传入被忽略。
- **类型按目标方言解析**:代理键 BIGINT→INT64→NUMBER→INT→INTEGER;时间 DATETIME→TIMESTAMP→DATE;布尔 BOOLEAN→TINYINT→SMALLINT→CHAR(1)→INT(实测 PG 无 DATETIME、Oracle 无 BIGINT/BOOLEAN);均不可用则阻断并指明列名,不生成不可用 DDL。
- **约定列是结构字段**:preview 标 `convention=true`、界面标「维表约定」;**不计入治理率与未治理计数**、不写 43 分层映射(无标准字段)、不写 19 来源映射(无上游来源列);可改名、可取消(与后端必补的 `etl_time` 不同,约定列由 preview 提供并按请求回传)。
- **主键 = 代理键**(代理键被取消时不设主键);DIM 与 DWD 仍走同一继承/治理管道,SCD 加工(拉链)不在本期。
- preview 新增 `dialect`/`scdType` 可选参数(默认 MYSQL/SCD1),前端随方言与 SCD 类型变化重取预览,保证"看到的就是将要写下的"。

## DWS 聚合与 ADS 应用建模(51/52,2026-09-17)

- **上游按层解析**(49 的能力矩阵全层放开):DWD/DIM ← ODS 模型(按 `source_*` 反查);**DWS ← 同过程 DWD 模型**;**ADS ← 同过程 DWS 模型**;ODS 仍阻断(走逆向导入)。聚合/应用层的上游是**模型**,界面多选、默认全选;上游不存在时阻断并说明(先派生上一层)。
- **结构化聚合定义**(51):43 分层映射新增 `field_role`(`DIMENSION` 分组键 / `MEASURE` 度量)与 `aggregate_func`(`SUM/COUNT/COUNT_DISTINCT/MAX/MIN/AVG`);口径/过滤沿用 `transform_expr`;模型行新增 `stat_period`(表级周期约定 `1h/1d/1w/1m/ALL`)。默认角色按标准字段角色推断(METRIC→度量+SUM,其余→维度),未命中标准字段默认维度(不擅自聚合);度量必须给出白名单内的聚合函数,维度不得带函数。
- **主键与命名**:DWS 主键 = 全部维度字段(分组键即粒度),无维度时 warning 且不设主键;**ADS 默认不设主键**;命名建议 `dws_<过程编码>_<周期>`、`ads_<应用编码>_<过程编码>`,preview 返回 `suggestedCode/suggestedName`。
- **应用绑定**(52):模型行新增 `app_code`/`app_name`(松散引用,不新建应用实体与表);字段逐列记录来源 `<上游模型编码>.<列>`(43 的 `source_field`),多上游 join 属加工阶段(21/22)。
- **映射与回填**:聚合/应用层**不写 19 来源映射**(上游是模型,19 只能表达数据源表;跳过条数回报),来源由 43 承载;**聚合/应用层的 43 落全部业务字段**(未治理字段 `process_field_id` 为空——维度/度量与聚合函数是粒度与口径定义,不能因未治理而丢失;V14 放宽该列非空约束,INHERIT 层行为不变);**治理回填只对 INHERIT 模式(上游 ODS)生效**;血缘登记沿用 23 管道。
- **治理率**分母 = 业务字段(排除目标层技术列、维表约定列);维度与度量都算业务字段。

## 新建模型与逆向导入分步交互(2026-09-17)

### 新建模型分两步

- **第 1 步 基本信息**:模型名称(必填)/模型编码(按命名标准自动生成,可改)/分层(必填,下拉选择)/业务过程(可选)/所属目录(可选)/模型描述(可选)。
- **分层选择后自动带出**:目标库(`database_name`)、数据源(`datasource_id` 解析名称)、方言(`db_type` 映射)——只读展示,不可编辑;前端按 semantic `LayerConfigApi` 与 datasource 列表本地推导,不新增接口。
- **编码自动生成规则**:`{layerCode}_{snakeCase(name)}`,去重下划线与首尾下划线;用户手动修改编码后不再自动覆盖。
- **第 2 步 编辑字段**:创建成功后自动跳转模型详情页(字段编辑 Tab);第 2 步可跳过(保存空字段,后续补)。
- **后端变更**:创建请求 `CreateRequest` 扩展 `layerCode`/`processId`/`directoryId`/`description` 四字段;`ModelCatalogService.create` 写入模型行并自动初始化空表结构(与 02 统一);创建成功返回 `ModelVO` 供前端跳转。

### 逆向导入分三步

- **第 1 步 选源表**:选择数据源 → 浏览/搜索表 → 勾选表;下一步需至少选 1 张表。
- **第 2 步 确认配置**:目标分层(必填,默认 ODS)/目标方言(按数据源 dbType 自动识别,可改)/导入到目录(可选);展示已选表摘要清单。
- **第 3 步 确认字段**:逐表预览列结构、配置 `event_time` 来源字段(自动识别,可改);点击导入执行;结果展示与既有 08/38 一致(created/filled/skipped/failed + 字段治理明细 + 打开模型)。
- **后端不变**:导入接口与数据结构沿用 08/38 既有契约;仅前端交互分步,请求 payload 不变。

### 字段列表刷新

- 模型详情页字段列表顶部操作栏增加「刷新」按钮,与批量添加/批量删除并列。
- 点击后重新请求 `GET /api/v1/modeling/models/{id}/structure`,loading 状态,刷新展示;不弹确认框。
- 用途:字段引用的标准更新后,用户可即时同步最新值而不 F5 整页重载。

## 统一新建模型入口与通用信息抽屉(2026-09-17 评审)

### 入口统一

- 模型工作台顶部操作栏**只保留一个**「新建模型」主按钮(原「按过程派生」「逆向导入」「新建物理模型」三个入口全部移除)。
- 点击后打开右侧抽屉(`ModelCreateWizardDrawer`),不走页面跳转。
- 逆向导入的独立入口(`ReverseImportDrawer`)保留为独立组件,仅用于派生页「请先逆向导入 ODS 模型」的跳转回补(`/modeling?action=import`),不在工作台主界面展示入口。

### 抽屉结构

```
新建模型（抽屉）
├── 一、通用信息
│     ├── 模型名称 *（单行输入）
│     ├── 模型编码 *（按命名标准自动生成,可手动修改）
│     ├── 分层 *（下拉,从 semantic LayerConfigApi 取）
│     ├── 目标库（只读,从分层配置 databaseName 带出）
│     ├── 数据源（只读,从分层配置 datasourceId 解析名称）
│     ├── 方言（只读,从数据源 dbType 映射,默认 MYSQL）
│     ├── 业务过程（下拉,可选）
│     ├── 所属目录（下拉,可选）
│     └── 模型描述（多行,可选）
├── 二、建模方式 *（Radio 单选）
│     ├── ○ 手工建模型
│     ├── ○ 逆向导入
│     └── ○ 按过程派生
├── 三、方式特有信息（根据方式动态显示）
│     ├── 逆向导入:数据源 / 库(Schema) / 表名
│     └── 按过程派生:提示文案（跳转至派生页完成上游模型选择）
└── [下一步]
```

- **编码自动生成规则**:`{layerCode}_{snakeCase(name)}`,去重下划线与首尾下划线;用户手动修改编码后不再自动覆盖(`codeManuallyEdited` 标记)。
- **分层自动带出**:选择分层后,前端本地查 `layers.find(l=>l.code).datasourceId` → `datasources.find(d=>d.id).name/dbType` → `dialectOfDbType()` 推导方言;不新增接口。
- **业务域**:当前版本数据结构(`yak_modeling_model`)未扩展 `domain_id`,抽屉中暂不展示业务域字段;业务域语义由「业务过程」间接表达(每个过程已归属业务域)。后续数据结构扩展后补 UI。

### 下一步跳转逻辑

| 建模方式 | 行为 |
|----------|------|
| 手工建模型 | 调用 `POST /api/v1/modeling/models` 创建空模型 → 跳转 `/modeling/models/{id}`(字段编辑页,空字段列表) |
| 逆向导入 | 先调用 `POST /api/v1/modeling/models` 创建空模型 → 跳转 `/modeling/models/{id}?initMode=import&datasourceId={id}&database={db}&table={tbl}` → detail 页自动调用 `POST /api/v1/modeling/import/preview` 预览源表字段并填充到字段列表 |
| 按过程派生 | 先调用 `POST /api/v1/modeling/models` 创建空模型 → 跳转 `/modeling/derive?code={code}&name={name}&layerCode={layer}&processId={pid}&directoryId={did}&description={desc}` → derive 页从 query 自动预填通用信息,用户在该页完成上游模型选择与字段治理后生成 |

- **统一创建接口**:三种方式均先走 `POST /api/v1/modeling/models`(扩展字段 `layerCode`/`processId`/`directoryId`/`description`),创建成功后再按方式分流;后端无需新增接口。
- **逆向导入字段预览**:detail 页消费 URL query 中的 `initMode=import` + 源表参数,调用既有 `POST /api/v1/modeling/import/preview` 接口,将返回列映射为字段草稿并标记 `dirty=true`;消费后 `replaceState` 清理 query,避免刷新重复填充。
- **按过程派生预填**:derive 页初始化时读取 URL query 的 `code`/`name`/`layerCode`/`processId`/`directoryId`/`description`,有值则写入对应 state 并清理 query;不改动 derive 既有管道。

### 与现有 ticket 的关系

| Ticket | 关系 |
|--------|------|
| 02 模型 CRUD | 统一抽屉的「创建空模型」基础能力 |
| 08 逆向导入 | 建模方式之一;独立逆向导入抽屉保留为组件,但主入口不再直接打开它 |
| 38 ODS 标准套用 | 逆向导入时预览填充的字段仍走 38 标准套用(在导入事务内完成) |
| 44 按过程派生 | 建模方式之一;派生页作为第二步,通用信息由统一抽屉预填 |

### 前后端接口变更清单

**前端变更:**
- 新建 `ModelCreateWizardDrawer.tsx`(通用信息表单 + 建模方式 Radio + 方式特有信息动态区 + 自动带出 + 编码自动生成)。
- `index.tsx`:移除「按过程派生/逆向导入/新建物理模型」多入口,替换为单一「新建模型」按钮;接入 WizardDrawer;移除旧的 `ModelCreateDrawer` 引用与死代码。
- `detail.tsx`:在 `loadStructure` 成功后增加 `initMode=import` 分支,自动预览并填充源表字段。
- `derive/index.tsx`:初始化 useEffect 增加 URL query 读取,支持从统一入口预填通用信息。
- `types.ts`: `ModelingImportColumnView` 补充可选字段 `decimalDigits`/`defaultValue`(部分 JDBC 驱动返回)。

**后端变更:**
- 无新增接口;创建模型接口已在前序批次扩展 `layerCode`/`processId`/`directoryId`/`description`。
- 无表结构变更。

## 业务域贯穿 + 字段标准套用增强(2026-09-20)

### 业务域落库(修正 248 行的旧结论)

- **`yak_modeling_model.domain_id` 已落地**(V18),第 248 行「当前版本未扩展 domain_id,抽屉暂不展示业务域」的结论作废:通用信息抽屉与列表均有业务域列/筛选,`domainId` 随创建与 `PUT /models/{id}` 读写。
- **目录跟随业务域**(V19 起):保存时按域自动建/复用同名目录(域改名则目录跟随改名,父域目录进父链),清空域则模型回未分类(`directory_id=0`)。`UNCATEGORIZED=0` 而非 NULL,该列 `NOT NULL DEFAULT 0`。
- **存量归位**(V21):V19 之前建的模型即使已有 `domain_id` 仍停在未分类,迁移按「同域已绑定目录取 `MIN(id)`」(与 `findByDomainId` 口径一致)回填一次;域还没有目录的保持未分类,等下次保存补目录。命中条件带 `directory_id = 0` 故重复执行幂等。

### 更新人与回收站

- **`updated_by` 列**(V20):由应用侧写入,`update_time` 仍靠 `ON UPDATE CURRENT_TIMESTAMP`。操作人只取 `CurrentUserProvider` 的请求态,经 controller→service→repository 透传;`NULL` 表示该列上线前的历史行。创建/更新/派生/逆向导入/结构保存/发布/恢复/改目录均落更新人。
- **回收站入口**:从工作台一级按钮收进「更多」菜单(与标签管理同组),抽屉自加载目录并新增 **更新人/更新时间** 两列,加宽以容纳(横向滚动)。
- 前端孤儿组件 `ModelCreateDrawer.tsx`/`ModelDirectoryModal.tsx` 已删除(统一抽屉取代,见上一节)。

### 表结构字段编辑:标准字段检索与类型带出

- **字段名可下拉检索**(`POST /api/v1/semantic/fields/page`,keyword 命中编码或名称,260ms 防抖),也保留自由填写;下拉选中即写入该字段的标准引用(`std_field_id`/`std_type_id`/单位/口径/码集/安全等级),与「标准发现」同源同口径。
- **改名判废**:手改字段名偏离所选标准编码时,整套标准引用一并清空,避免「数据标准」列显示别人的类型。
- **类型不再留空**:按标准字段自带的 `data_type` 推导该方言可用物理类型——带精度的(`decimal(18,2)`)透传长度与小数位;逻辑名(`string`/`int` 等)在**服务端下发的方言类型目录**里取第一个支持的候选;推不出保持空白。**仅在行的类型为空时生效**,源表导入与手填的类型优先级更高。「标准发现」复用同一推导。
- 该推导为前端本地逻辑,不新增接口;`discoverStandards` 依赖数组为空,故类型目录另存 `typeCatalogRef` 供其读取,不把目录塞进依赖(否则 `loadStructure` 会重复执行)。

## Ticket 10:建库脚本多方言模板(2026-09-20)

在 09 的 MySQL 模板之上补齐 PostgreSQL / Oracle / Doris / ClickHouse / StarRocks 五个模板,`DdlService` 按模型方言选择;仍然**只生成文本、平台内不执行**(D3 不变),前端切换方言后重新生成即可复制/下载(09 已具备)。

### 共同约定

- 输入是模型**已保存**的结构(列/主键/索引/分区/表属性),表名留空按 `model_code` 兜底。
- 类型以列保存的 `dataType` 为准;若不在目标方言的类型目录内,按下面的偏好链解析为该方言可用类型;链上也无可用时**原样输出并在脚本里加一行注释提示**,不抛错——DDL 是给人复核的文本,报错中断不如把疑点写在旁边。
- 默认值语义沿用 DOMAIN.md 既有规则:纯数字与 `CURRENT_TIMESTAMP*` 裸写,其余按字符串加引号转义。
- 模板不替用户决定任何「模型里没有的物理设计信息」:该方言必需但模型推不出的子句(Doris/StarRocks 的分桶数、ClickHouse 的引擎与分区表达式落地形式)一律输出 `-- 请补充 ...` 注释提示,不编默认值。
- **列顺序不因方言而变**:Doris/StarRocks 要求 KEY 列排在列定义最前,模型里主键列可能在任意位置——模板只在列尾注释提示"请确认主键列前置",绝不擅自重排列顺序(改序会同时影响生成的加工 SQL 与血缘)。
- **ClickHouse 可空性**:主键/排序键列不允许 `Nullable`,其余 `nullable=false` 之外的可空列类型包成 `Nullable(T)`;`NOT NULL` 约束在 CK 中无对应表达,不输出。

### 跨方言类型偏好链(沿用并扩展 177 行已登记口径)

| 保存的类型 | 解析顺序(取该方言目录里第一个支持的) |
|---|---|
| `BIGINT` | BIGINT → INT64 → NUMBER → INT → INTEGER |
| `INT`/`INTEGER` | INT → INTEGER → INT32 → NUMBER |
| `DECIMAL`/`NUMERIC` | DECIMAL → NUMERIC → NUMBER(精度与小数位随类型带过去) |
| `DOUBLE`/`FLOAT` | DOUBLE → DOUBLE PRECISION → FLOAT64 → BINARY_DOUBLE / FLOAT → REAL → FLOAT32 → BINARY_FLOAT |
| `VARCHAR`/`CHAR` | 同名 → VARCHAR2(Oracle) → STRING(ClickHouse/Doris 无长度时) |
| `DATETIME`/`TIMESTAMP` | DATETIME → TIMESTAMP → DATE |
| `BOOLEAN` | BOOLEAN → TINYINT → INT → NUMBER(非原生布尔时加注释提示) |
| `JSON` | JSON → JSONB |

### 各方言语法矩阵

| 方言 | 标识符 | 列注释 | 表注释 | 二级索引 | 分区 | 表属性 |
|---|---|---|---|---|---|---|
| MySQL | 反引号 | 内联 `COMMENT 'x'` | 表尾 `COMMENT='x'` | 表内 `INDEX`(可 `USING`) | 表尾 `PARTITION BY`(HASH/KEY 完整;RANGE/LIST 提示补分区定义) | `ENGINE=`/`DEFAULT CHARSET=` 等键值直拼 |
| PostgreSQL | 双引号 | 表后 `COMMENT ON COLUMN` | 表后 `COMMENT ON TABLE` | 表后独立 `CREATE [UNIQUE] INDEX ... ON t (cols)` | 表内 `PARTITION BY RANGE/LIST (col)` + 提示补 `CREATE TABLE ... PARTITION OF`；有主键且主键未覆盖分区列时点名提示 | 目录外属性注释提示 |
| Oracle | 双引号 | 内联 `COMMENT 'x'` | 表后 `COMMENT ON TABLE` | 表后独立 `CREATE [UNIQUE] INDEX` | 表内 `PARTITION BY RANGE/LIST (col)` + 提示补 `PARTITION ... VALUES` | 同上注释提示 |
| Doris | 反引号 | 内联 `COMMENT 'x'` | 表尾 `COMMENT 'x'` | 表内不支持独立二级索引 → 注释提示(可另建 inverted index) | 表内 `PARTITION BY RANGE(...)` + 提示补分区定义;HASH 走 `DISTRIBUTED BY` 语义 | 键模型:有主键出 `UNIQUE KEY(...)`;`DISTRIBUTED BY HASH(首列)` 不带 BUCKETS(交自动分桶)并在注释里提示副本/分桶需按集群确认 |
| StarRocks | 反引号 | 同 Doris | 同 Doris | 同 Doris | 同 Doris | 同 Doris，但主键模型写 `PRIMARY KEY(...)`（Doris 写 `UNIQUE KEY(...)`） |
| ClickHouse | 反引号 | 内联 `COMMENT 'x'`;非主键/排序键且可空时类型包 `Nullable(T)` | 引擎子句之后表尾 `COMMENT 'x'`(CK 支持) | 跳过并注释提示(data-skipping index 需表达式) | 引擎子句内 `PARTITION BY 表达式`(有分区表达式时用;只有分区列时直出并建议改写表达式，分区列可空时点名提示) | `ENGINE = MergeTree ORDER BY (主键或首列)`;表属性注释提示 |

### 验收口径

- `DdlService` 对六种方言均能选到模板,不再抛 `INVALID_DIALECT`。
- 每个模板有独立单测;不可映射类型/不支持能力必须表现为脚本内注释,而不是异常或静默丢弃。
- 本地无任一目标库实例,生成文本只按上表口径与各方言官方语法人工核对,**不得声称已在真机执行验证**。


## F-023 场景 Skill 标准匹配

F-023：原标准助手消费 AI 类型候选，仅带入当前未保存字段。模型定义仍由 Modeling 拥有，ModelSuggestionQueryApi 以授权事务锁读取定义指纹；编辑上下文同时给出结构与指纹，保存可携带 If-Match，在原结构事务/审计之前拒绝过期定义。原发布快照与审批指纹序列化保持兼容。

依赖：Agent runtime → toolset → gateway → semantic.api / modeling.api；源域不依赖 Agent，不新增状态机或第二业务真相。合同见 [F-023](../../docs/product/features/F-023-skill-standard-match.md)。
