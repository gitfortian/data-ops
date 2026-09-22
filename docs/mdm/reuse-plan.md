# 主数据管理（MDM）基于平台能力复用的实施方案

> 日期：2026-09-19　前置：[review.md](./review.md)（深度评审）、[dev-plan.md](./dev-plan.md)（D-M1 复用优先、D-M11 方案 A）
> 依据：2026-09-19 对 sync / data-service / quality / job / workflow / task-catalog / lineage / notification 的接口级调研（证据均给到类/文件）
> 总原则：**MDM 零执行引擎**。凡"搬运、调度、发布 API、鉴权、缓存、血缘、任务目录"一律复用；凡"主数据语义"（实体/属性/识别/去重合并/审批/版本）留在 MDM 自建。被复用模块的能力缺口**优先用架构结构绕开，而不是要求引擎扩能**。

---

## 一、总体架构（复用视角的一条主链路）

```
业务库源表 (CRM.customer / 交易.cust …)
   │ ①落地：sync 离线任务（GUIDE_SINGLE，MDM 生成并注册，复用其字段映射/cron/UPSERT/执行状态）
   ▼
平台库落地区 yak_mdm_landing_{entity}_{source}   ←— 采集状态回写：MDM 本地存 jobDefinitionId，反查 batch-instance
   │ ②加工：数据开发 SQL 任务（DevelopmentTaskService 草稿），平台库内同库 SQL：
   │    N 落地表 → master_id=MD5(实体编码+PK值) + attributes JSON + source_ids 合并 + version+1
   ▼
yak_mdm_record（统一主数据，MDM 读写）
   │ ③清洗：MDM 自建（去重/合并/标准化/补全，JSON 语义，quality 接不了）
   │ ④质量：复用 quality —— CUSTOM_SQL 规则跑 record 表 + 列级规则绑落地表
   │ ⑤审批：MDM 自建二级流（不接 workflow）＋ 快照式版本（照抄 modeling 范式）
   │ ⑥分发：API 方式＝实现 DataServiceSourceProvider → DataServicePublisher 发布（复用 Key/限流/缓存）
   │ ⑦定时：复用框架 schedule（YakScheduleNamespaces + MDM bridge/handler，模板=lifecycle）
   ▼
⑧服务：data-service 运行时对外 API；订阅通知一期＝站内信（NotificationRouter），外部 webhook 待 alert 插件
⑨治理：血缘复用 LineageRegistrationService（源表→落地→record→消费方）；总览接已有 /overview API
```

关键判断：**跨源合并、表达式列（MD5/JSON）、JSON 冲突合并**这三件事 sync 引擎做不到（整列覆盖语义），硬做要改 sync+LinkUp 双端引擎（周级特性）。改成"落地先原样搬进平台库、加工在平台库内一段同库 SQL 完成"后，sync 只做它已经支持的事（JDBC 读 + 列改名 + 写 + cron），**引擎零改动**，且加工 SQL 从"跨库不可执行"（review P0-1.2）变成同库可执行。这是对 D-M11"MDM 只查询展示、不建执行引擎"的最忠实实现。

---

## 二、逐环节复用决策表

| # | 环节 | 结论 | 复用对象与证据 | MDM 侧要做的 | 不动对方的理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | 采集落地 | **复用 sync** | `OfflineJobDefinitionService.saveGuide(dto)`（public @Service，可注入直接建任务）；`OfflineJobDefinitionDTO` Source 支持 `table/sql/where`，Sink `mysql-jdbc`，写模式含 `UPSERT+primary_keys`；映射 `mapping.columns[{source,target}]`（GUIDE_SINGLE 可用） | 按"实体×来源"生成落地任务定义；落地表 DDL 预建；本地记录 `jobDefinitionId` | sync 无 N:1 合并、无表达式列（fanOut 是 1:1；映射仅改名）——不逼引擎扩能，合并放② |
| 2 | 采集状态 | **复用 sync 查询** | `POST /api/v1/job/batch-instance/page{jobDefinitionId,status}` 取 pageSize=1 即"最近成功"；定义行自带 `lastJobStatus/lastEndTime` | `CollectStatusTab` 与总览"采集状态"卡接反查结果 | 给 sync 加 `is_master_data` 标签要动双端表+查询；MDM 存 taskId 反查等效且零侵入（review P0 里的 55b 可降级为 MDM 本地关联表） |
| 3 | 加工统一记录 | **复用数据开发执行** | `DevelopmentTaskService.saveDraft/publish`（DEV 模块 public 服务，跨模块直连有先例：home 直引 `OfflineExecutionOverviewReader`）；SQL 节点 `supportsTaskLifecycle()=true` | 修 `MdmMasterSqlGenerator`：表名 `yak_mdm_record`、含 schema、读落地表、master_id/attributes/source_ids JSON_MERGE/version 逻辑、按来源 field_mapping；前端按钮改为"注册任务草稿→跳编辑页" | MDM 不建执行器（D-M11）；`DevelopmentTaskApi.SaveDraftRequest` 只是 HTTP DTO，注入 service 即可，无需对方改 |
| 4 | 清洗（去重/合并/标准化/补全） | **MDM 自建（维持现状）** | 已实现：`MdmCleanService` 服务端 GROUP BY 去重、合并预览/执行、typed 规则 | 补前端 57 入口 + 忽略组 + crosswalk（见 review P1） | 变换语义作用于 attributes JSON 列，quality 规则模型（`RuleType` 六类，绑物理表四元组）不承担"写回修正" |
| 5 | 质量检查 | **复用 quality** | `QualityMonitorCommand.Save` + `RuleType.CUSTOM_SQL`；编程式 `QualityMonitorManager.create/QualityExecutionManager.run`；结果 `QualityExecutionReader` | 落地表注册为表资产（`QualityTableAssetManager.register` 链路）→ 列级规则可用；record 表用 CUSTOM_SQL + `JSON_EXTRACT` 做"手机号格式"类检查并带 entityId 过滤 | 注意：MDM 属性是 JSON 列，**列级规则只能绑落地表**；这是"MDM 数据形态"决定的分工，不是 quality 缺口 |
| 6 | 审批流 | **MDM 自建补齐（不接 workflow）** | 调研定论：workflow 是 DAG 编排（表/节点/回调均无人工审批概念，回调只有进程内事件、无跨模块 SPI），接它=在 4 个模块造人工节点能力 | `yak_mdm_change` 已有 `approval_level/approver/...`：补"当前级+推进"即二级流；角色→人用 `UserService.getUserBriefListByRoleId`/`UserRoleService`；新增 `mdm:approve` 权限码；withdraw 改用 `CurrentUserProvider`（review P0-1.5） | "数据管理员/治理负责人"角色平台未预置——方案：boot migration 种两个角色或在 security 管理页由客户自建，MDM 只按 roleCode 配置解析 |
| 7 | 版本管理 | **照抄 modeling 快照范式** | `ModelVersionService.publish`：全量 JSON 快照 + SHA-256 幂等 + 线性追加 | `applyChange` 通过后落 attributes 快照（新表 `yak_mdm_record_version` 或 change 行加 before/after 双份） | modeling 自己也没抽成组件（各模块各一套版本）；只抄不 dependencies |
| 8 | 分发（API 方式） | **复用 data-service** | SPI `DataServiceSourceProvider.resolve→ResolvedSource(sql,contract)` + `DataServicePublisher.publish`；鉴权 `AuthMode.API_KEY`（X-API-Key，SHA-256 摘要/rotate/IP 白名单/`DataServiceRateLimiter`）；缓存 Caffeine | 实现 `MdmDataServiceSourceProvider`（sourceType=`MDM_RECORD`，按实体生成 select-only SQL 视图）；分发配置 ACTIVE 时触发发布；失败才计 fail | MDM 自建对外凭证体系=重造 data-service 已有的整套；唯一先例 `DevelopmentDataServiceNodeSourceProvider` 证明路子通 |
| 9 | 分发（定时触发） | **复用框架 schedule** | `YakScheduleGateway(namespace)` + `@Component(HANDLER) ScheduleHandler`；模板 `LifecycleScheduleEngineBridge`；上下文还原 `QualityScheduleHandler`（`ProjectContextScope`） | `YakScheduleNamespaces` 加 `MDM_DISTRIBUTION` 常量（common，一行）；MDM 建 bridge+handler，DAILY/HOURLY 频率落 cron；**execute 占位语义先修**（review P0-1.8：未接入不得写成功时间戳） | 不依赖 job 模块（它明确"不拥有 Cron 生命周期"） |
| 10 | 分发（MESSAGE/FILE） | **暂缓占位** | alert 渠道现状仅 DINGTALK（`AlertPluginRegistry`），storage 无消费先例 | UI 明示"暂不支持"；后端保持不写执行结果 | 为 MDM 单点开邮件/通用 webhook 插件不成比例 |
| 11 | 服务（订阅通知） | **一期站内信，外部 webhook 后置** | `NotificationRouter.publish(NotificationIntent)`（afterCommit 派发，范例 `QualityAlertRecorder`）；需 MDM 自写 `NotificationPolicyResolver`（现仅 QUALITY/OFFLINE_SYNC 有） | `notifyMode=WEBHOOK` 在接通 webhook sink 前不出现在可选项；订阅方=平台用户/角色，subscriberCode 语义收敛 | 外部系统"订阅"无凭证模型可用，硬做=私造；外部消费的正确形态是 data-service API + Key（#8） |
| 12 | 血缘 | **复用 lineage 登记** | `LineageRegistrationService.registerAsset/registerRelation`（幂等 upsert）；metric 先例 assetKey=`"metric:"+id` | 来源确认时登记源表资产；加工任务生成时登记 落地表→`mdm_record`；API 发布时登记 record→服务；MDM 实体用 `TABLE`+sourceType=`MDM` 即可，`LineageAssetType` 加 `MDM_ENTITY` 是可选项（一行枚举） | 无需大改 |
| 13 | 任务目录 | **二期再接，照抄 quality 四件套** | `TaskCatalogService.publish` + `TaskAssetRevisionProvider` + `TaskAssetReconciler` + `TaskExecutor` SPI（现实现者仅 DEV/quality） | 若加工任务需进目录/被工作流编排：仿 `QualityTaskPublisher/QualityTaskRevisionProvider/QualityTaskExecutor`；`TaskAssetSource` 需加 `MDM` 枚举（common 一行） | 一期用"数据开发 SQL 任务"已够；目录化是增值非通路 |

### 需要对方模块动的最小清单（全部是"加常量/加枚举"级）

1. `YakScheduleNamespaces` +`MDM_DISTRIBUTION`（common）。
2. （可选）`LineageAssetType` +`MDM_ENTITY`。
3. （可选，二期）`TaskAssetSource` +`MDM`。
4. sync / quality / data-service / workflow / job **引擎与契约零改动**。

**唯一需要产品/管理侧确认的落地事项**：sync/data-service 都要求"已注册数据源"。落地方案是把**平台业务库自身注册为一个数据源**（管理动作，非代码）；若不接受明文凭证回连，替代是给 datasource 增加"内置平台库"概念（小契约改动，单独立项，不阻塞主链路——加工 SQL 在数据开发执行本来就走平台连接，不经 datasource）。

---

## 三、三个被否的备选（留档防摇摆）

| 决策点 | 备选 | 否因 |
| --- | --- | --- |
| 加工落地 | 让 sync 直写 `yak_mdm_record`（补 N:1+表达式+JSON 合并） | 要改 sync+LinkUp 引擎写语义（冲突更新表达式、整列覆盖→列合并），周级双端特性开发，风险外溢到所有同步任务 |
| 加工落地 | MDM 自建轻量执行器（定时读源库 UPSERT） | 直接违反 D-M11"MDM 不建执行引擎"，且重复造 sync 已有的连接器/重试/监控 |
| 审批 | 接入 workflow 做审批流 | workflow 无人工节点、无审批人解析、无跨模块回调（调研 §1），接入成本≫自建二级流（自建只差"当前级推进"，字段已在） |
| 对外服务 | MDM 自建 API + key 体系 | data-service 的鉴权/限流/缓存/发布全套现成，自建即违反 D-M1 |

---

## 四、实施切片（垂直可验收，编号即顺序；R0~R2 = review 的 P0）

### R0 加工正确性修复（纯 MDM，先行垫底）　✅ 已完成 2026-09-19
- 修 `MdmMasterSqlGenerator`：表名 `yak_mdm_record`、补 schema、SELECT 列按 field_mapping；`mdm_source` 追加 `field_mapping` JSON 列（新 V12，来源确认弹窗按源表列结构自动预填，能默认就默认）。
- 验收：任一实体配置后"生成加工 SQL"产出的每条语句可在其引用的平台库单库内直接执行。
- 实施记录：V12 `field_mapping`（属性编码→源列名，NULL=同名回退）；来源确认 API 增 `fieldMapping` 入参（列名安全标识符校验 44025）；生成器按存在部分全限定 database/schema/table、映射列入 JSON_OBJECT/MD5、字面量转义；前端 `identification/FieldMappingEditor`（实体属性×源表列同名忽略大小写自动预填，PK 未映射阻断确认）。待重启后端跑 V12 后页面实测。
- 页面实测（2026-09-19，customer 实体 + crm_db.crm_customer）中另抓到并修复两个隐藏 P0：① MDM 全部 5 个分页端点返回裸 `Result<PageData<…>>`，命中框架 PageData 无 Jackson 序列化器缺陷 → 一律 500（实体列表一直"暂无"的根因），统一改 `PagingData.from`；② `MdmAttributeController.create` 对不引用类型标准的属性 `Set.of(null)` NPE → 建属性必 500（顺带修 `MdmEntityService` 状态审计 `Map.of("from", null)` 同款隐患）。V12 撞车说明：开发库存有已回退工单 54 旧 V5 的列，V12 已改 information_schema 幂等守卫。实测产出 SQL 已确认：`INSERT INTO yak_mdm_record … FROM crm_db.crm_customer`，phone→cust_mobile 自定义映射生效。

### R1 采集落地复用（MDM↔sync 打通，sync 零改动）　✅ 已完成（2026-09-19 实测）
- 新表 `yak_mdm_collect_link`（entity/source→jobDefinitionId）；来源确认后一键"生成落地任务"：建落地表（DDL 预建）+ 注入 `OfflineJobDefinitionService.saveGuide`；采集状态 Tab/总览卡按 jobDefinitionId 反查最近 SUCCEEDED。
- 验收：CRM.customer 经落地任务全量进平台库落地表，UI 显示最近采集时间与状态。
- 实施记录：V13 `yak_mdm_collect_link`（project+source 唯一，只存反查锚点，执行态不落库）；V14 清理工单 54 遗留列（collect_mode/collect_freq/config_status，field_mapping 留用）。`MdmCollectService`：读源表列结构→平台库 `SELECT DATABASE()` + `CREATE TABLE IF NOT EXISTS mdm_landing_{code}_{sourceId}` 预建（类型按 JDBC 标准映射，未知/超长兜底 TEXT，标识符白名单校验）→ `LandingJobFactory` 构造 GUIDE_SINGLE（source `db[.schema].table`、sink `库.落地表` + `autoCreateTable:false` + `writeMode:overwrite` 全量刷新、mapping 全列同名）→ `saveGuide`；重名自愈（link 未落库崩溃后按任务名复用既有定义）。执行=`online`（如 OFFLINE）+`execute`（异步）；状态反查=`OfflineJobExecutionService.page(status=SUCCEEDED,pageSize=1)` + 定义 `lastJobStatus`，逐项容错。review P0-1.9 回填：来源有链路即阻断解绑（SOURCE_REFERENCED）。pom 可选依赖 sync-offline（ObjectProvider 守卫，同 home 直连先例）。前端共享 `pages/mdm/components/CollectLanding`（状态 hook/状态格/生成弹窗/执行链接），识别页来源表与实体详情采集 Tab 复用；落地目标数据源在弹窗内选择（平台库无内置数据源，凭证治理见五-5，实测可暂选同实例数据源）。模块测试 123 全绿、tsc 回 199 基线（run() 引擎异常透传补丁后 124）。
- 实测记录（2026-09-19）：Link-Up 引擎就绪后全流程走通——识别页「生成落地任务」（选客户库为落地目标，link 行/落地表 `yak_security.mdm_landing_customer_1`/GUIDE_SINGLE 作业三处落库一致，重复生成幂等复用）；「执行落地」online+execute 异步跑成功行：执行实例 SUCCEEDED、`sink_committed_record_count=3060`，落地表实查 3060 行且 cust_id 无重复；UI 两处（识别页落地采集列、实体详情采集 Tab）均显示「已采集 · 2026-09-19 20:20:09 · 3060 行」，引擎未起时正确显示「运行失败 · 可查看任务日志」（状态反查路径同为实测）。附带佐证：R0 的 PagingData 修复生效，实体列表首次真实渲染。

### R2 加工任务化 + 记录可读（打通主链路终点）　✅ 已完成（2026-09-20 实测）
- "生成主数据加工任务"：多落地表→`yak_mdm_record` 的同库 UPSERT/合并 SQL，经 `DevelopmentTaskService.saveDraft` 注册草稿并跳转（替代裸复制 SQL，review P2-6）；记录搜索升级属性值检索 + 动态列展示（review P0-1.4）。
- 验收：**演示"一个客户从 CRM 源表 → 落地 → 加工 → 记录页按姓名可搜可见"**——这是整个 MDM 的成立判据。
- 实施记录：加工 SQL 口径切换——生成器入参 `SourceSpec`→`LandingSpec(平台库,落地表,datasourceId)`，读写两侧均按 `SELECT DATABASE()` 平台库限定（执行数据源默认库可不同），来源未生成落地链路时 44029 `SOURCE_NOT_LANDED` 阻断并点名缺表；新端点 `POST /entities/{id}/records/processing-task`（`MdmProcessingTaskService`）：先跑全部校验再生成，find-or-create 根目录 SQL 节点（确定性名 `MDM主数据加工-{code}`，同名非 SQL 节点拒绝），configJson 预填 `dataSourceId`=链路 job 的 `getSinkDatasourceId()` 反查（免 V15，容错缺省），`saveDraft` 按最新 draftRevision 乐观锁、冲突重读重试一次；检索升级——`page` 关键词经属性编码白名单（`DedupSql.isValidAttrCode`）下推 `JSON_UNQUOTE(JSON_EXTRACT) LIKE %kw%` OR 谓词（外部服务 API 保持 master_id 精确不变）；前端 RecordTab：「生成主数据加工任务」注册后跳转数据开发、SQL 弹窗降级为「查看加工 SQL」、按实体属性动态出列（PK 标记）、source_ids 解析为「数据源名: 原ID」、搜索 placeholder「搜索属性值（如名称）/ master_id」。模块测试 136 全绿、tsc 199 基线。
- 实测记录（2026-09-20）：记录页点「生成主数据加工任务」→ toast「已创建加工任务 MDM主数据加工-customer」并落数据开发工作台，草稿打开即见正确 SQL（读 `yak_security.mdm_landing_customer_1`、phone→`cust_mobile` 映射、source_ids 键=客户库 ID、落地目标数据源已预选），「运行当前 SQL」→ Execution #1 完成 · 1342ms · 影响行数 6120（含此前一次插入后重复执行，UPSERT 幂等：实查 `yak_mdm_record` 恰 3060 行、version 齐平 3，无重复行）；回记录页共 3060 条、动态列（客户ID/客户姓名/手机号/城市/会员等级+来源系统「客户库: C003030…」）全部真数；按姓名搜「客户2」命中 1122 条（`%客户2%` 模糊含 客户20~29/200~299/2000~2999，符合 LIKE 口径）——**客户从 CRM 源表→落地→加工→记录页按姓名可搜可见全链路走通，MDM 成立判据达成**。

### R3 质量与治理复用　✅ 已完成（2026-09-20 实测）
- 实体创建落地表后自动注册 quality 表资产；质量 Tab 提供"在数据质量建规则"预填跳转（带四元组）；实体生效/记录健康用 CUSTOM_SQL 模板（重复检查/必填检查）一键创建；血缘登记三段（源→落地→record）。
- 验收：客户实体在数据质量模块可见检查结果，详情页质量卡有真数。
- 实施记录：`MdmQualityService`（quality/lineage/sync/datasource 全 ObjectProvider 可选依赖，quality 零改动）——`status` 按四元组实时反查资产/监控/规则数/最近结果（D-M11 不落库）；`check` 一键体检 = 注册落地表资产 + 按内置模板建监控（**偏差修正：不用 CUSTOM_SQL，直接用 quality 原生 COLUMN_UNIQUE/COLUMN_NOT_NULL 模板按 code 查 templateId**，主键重复+主键必填+必填属性非空，阈值走模板默认 GTE100）+ `QualityExecutionManager.run` 异步触发，监控按四元组幂等复用（关键约束：quality 监控四元组唯一 → 共享的 `yak_mdm_record` 挂不了监控，体检对象只能是各实体落地表）。`MdmLineageService.sync`：三段 `源表→落地表→yak_mdm_record` 资产+DERIVES_FROM 关系批量登记，资产键共用 `PhysicalTableAssetKey` 归一口径（trim+小写）防与元数据/开发血缘裂节点，sourceType=MDM、证据 `mdm-collect-{linkId}`/`mdm-entity-{id}`，可反复同步 upsert 幂等；`landingContexts` 在 quality 服务算一次、血缘复用（sink 仍运行时反查 job 定义，无 V15）。新端点 `GET /entities/{id}/quality/status`、`POST …/quality/check`、`POST …/lineage/sync`（`MdmQualityController`）；错误码 44031-44033。前端 `QualityTab`（状态表/一键体检/无监控时带四元组预填跳 `monitor/create`/规则数跳监控详情/结果跳执行页，复用 quality `CheckResultTag`）+ `LineageTab`（同步回执+跳血缘图谱），detail.tsx 质量/血缘 Tab 挂载。模块测试 148 全绿（+质量 7、血缘 5）、tsc 199 基线。
- 实测记录（2026-09-20）：客户实体详情「质量」Tab 状态实时反查出落地表（客户库 · `yak_security.mdm_landing_customer_1` · 未注册/未检查）→ 点「一键质量体检」toast「已触发 1 张落地表」，资产列即「已注册」、规则列「2 条」（实体无必填属性故 PK 重复+PK 必填两条，列名按 field_mapping 解析为落地表真实列 `cust_id`）；刷新后最近检查「**通过 · 2026-09-20 12:14:02**」+详情链接。数据质量侧交叉验证：表配置页客户库出现资产「监控数 1 / 规则数 2 / 最近状态 通过」、监控 `/data-quality/monitor/1` 详情真数（手动触发、启用 2 规则、最近运行同时间戳）。「血缘」Tab 两次点「同步三段血缘」回执恒为 3 资产/2 关系；实查 `yak_security.yak_metadata_asset` source_type=MDM 恰 3 行（`table:3:crm_db..crm_customer`→`table:3:yak_security..mdm_landing_customer_1`→`table:3:yak_security..yak_mdm_record`），`yak_metadata_relation` 2 条 DERIVES_FROM 方向正确且重复同步不增行（幂等）——**验收判据「客户实体在数据质量模块可见检查结果、质量卡有真数」达成**。

### R4 审批二级流 + 审批前端（消灭菜单 404）　✅ 已完成（2026-09-20 实测）
- 当前级+推进逻辑、`mdm:approve` 权限、角色配置化解析审批人、withdraw 服务端取用户、CREATE/MERGE 提交拦截或实现、PK 修改拒绝；前端 `/mdm/approval` 页 + 契约测试补 V2028 + 详情"变更记录"Tab + 快照版本。
- 验收：改一条记录等级 → 一级→二级→生效→版本历史可见 v(n-1)/v(n) diff。
- 实施记录：选「接审批中心」架构——MDM 不自建级次推进：`MdmApprovalService.submit` 只落 PENDING 变更单并经 `ApprovalApi.submit`（flowCode=MDM_CHANGE，bizType=MDM_CHANGE/bizId=changeId）把单据关联回 `yak_mdm_change.instance_id`（V15 迁移：`instance_id` 列 + `yak_mdm_record_version` 快照表，uk 幂等）；终态回调 `MdmChangeApprovalHandler` → **`MdmChangeEffectService`**（关键解环：审批中心 `ApprovalFlowRegistry` 构造期急实例化全部 handler，故回调侧只依赖 repo+audit 的 effect 服务、不注入 ApprovalApi，与 modeling 的 ModelPublishApprovalHandler 同范式）同事务 applyChange：UPDATE/DELETE 先写 v(n-1) 基线快照（changeId=null）再 `appliedChange` 升版写 v(n) 快照（changeId+审批人）；提交侧守卫 44034 CREATE/MERGE 拦截、44035 PK 修改拒绝、44036 在途单去重；撤回走 `approvalApi.cancel`（operator 服务端取），approve/reject 端点删除。前端：`/mdm/approval` 变更单列表（状态筛选+审批单跳转+PENDING 撤回）消灭菜单 404（V2028 SQL + navigation 契约测试）；RecordTab「变更申请」弹非 PK 属性表单→diff 成 patch→submit；详情「变更记录」Tab（`ChangeHistoryTab`）按记录展示版本快照序列（基线/变更单来源标签）+ v(n-1)/v(n) 属性级 diff（新增/修改/删除）+ 变更流水。模块测试 157 全绿、tsc 199 基线。
- 实测记录（2026-09-20）：`/approval/flows` UI 建 MDM_CHANGE 两级流（两级均 root）→ 记录页对 `b4ccf6fa…`(v3) 提「会员等级 1→gold」变更 → toast「变更申请已提交」→ `/mdm/approval` 见 #1 审批中 + 审批单链接 → 待办中心实例 #7 一级通过（意见「一级同意：等级调整为 gold」）→ 当前级次即推进「第 2 级」→ 二级通过 → 单据「已通过」·结束时间 14:50:31；记录页该条 **member_level=gold · 生效 · 版本 4**、更新时间=二级通过时刻；「变更记录」Tab 选「重复客户59 · b4ccf6fa…（v4）」→ 快照两行 **v4 来源=变更单 #1 / v3 来源=基线快照**，点「对比 v3」弹「版本对比：v3 → v4 · member_level 1→gold · 修改」；审批页 #1 转「已生效」并回显审批人+意见。撤回链路：对 `f918b8f3…` 再提一单（city 改动）→ 审批页点「撤回」→ 行转「已撤回」、回记录页该条仍 上海市 · v3 未被误改——**验收判据「改一条记录等级→一级→二级→生效→版本历史可见 v(n-1)/v(n) diff」达成**。

### R5 分发复用 data-service + 定时　✅ 已完成（2026-09-20 实测）
- `MdmDataServiceSourceProvider` + 发布链路 + 分发配置 Tab 真接（执行=推送至 API 发布态/重试）；`MDM_DISTRIBUTION` schedule handler 让 DAILY/HOURLY 真生效；execute 占位语义修正（review P0-1.8）。
- 验收：外部调用方持 API Key 拉到 ACTIVE 客户记录；定时分发产生真实执行记录。
- 实施记录（2026-09-20）：
  - **发布来源**：`MdmDataServiceSourceProvider`（`SOURCE_TYPE=MDM_DISTRIBUTION`、`managesServiceDefinition=true`、**`sourceRef=distributionId`——一条分发配置一个 API**，因 data-service 侧 `UNIQUE(source_type,source_ref)` 否则同实体多目标互相覆盖，且 API Key/访问控制天然按目标隔离）。查询 SQL 由 `MdmDistributionQuerySql` 生成：读 `平台库.yak_mdm_record` 的 ACTIVE 行、`project_id/entity_id` 内联字面量（runtime 调用无项目头，租户边界=内联字面量 + API Key）、**零命名参数**、属性列 `JSON_UNQUOTE(JSON_EXTRACT)` 展平并经 `[A-Za-z0-9_]{1,64}` 白名单；`revision=SQL 模板 hash` ⇒ 只有属性集/口径变了才翻 `updateAvailable`，纯执行结果回写不触发重发布（回答本方案风险 3「发布粒度=实体查询定义、数据实时查表」）；`path=/mdm/{实体编码}/{配置id}`（目标系统可改名而路径不漂移，且避开全局 `UNIQUE(path)` 撞名）；`dataSourceId` 由采集链路落地任务的 sink 数据源运行时反查（`MdmProcessingTaskService.platformSinkDatasourceId`，零配置、与 R1 同源），反查不到即拒绝发布并提示「先执行采集」。
  - **发布编排**：`MdmDistributionPublishService`（`DataServicePublisher`/`DataServicePublicationReader` 均 ObjectProvider）三态收敛——未发布→`publish`、`updateAvailable`→`republish`、已发布但停用→`republish(enabled=true)`、否则原样返回；模块缺席抛新错误码 44037 `DATA_SERVICE_DISABLED`。
  - **execute 语义修正（P0-1.8）**：API 模式=幂等推送发布态 + 回写真实可供数条数（`MdmRecordRepository.countByEntity(ACTIVE)`）+ 回执带 `apiId/apiPath`；MESSAGE/FILE **明确抛「通道未接入」且绝不写 `last_distribute_time`**（假成功比失败危险）；`DistributionResult` 扩 apiId/apiPath；新端点 `GET /distribution/{id}/publication` 实时反查发布态（含 `available`，MDM 不落库）。
  - **定时**：`MdmDistributionScheduleEngineBridge`（namespace `yak-ops-mdm`，静态 cron DAILY=`0 0 2 * * ?` / HOURLY=`0 0 * * * ?`，payload 只带 `projectId+distributionId`、事实源仍是配置行；`ApplicationReadyEvent` 补登记——内存 Quartz 重启即失，单条失败只 warn 不连坐）+ `MdmDistributionScheduleHandler`（bean 名 `mdmDistributionScheduleHandler`＝dispatcher 按 bean 名解析；`ProjectContextScope.call` 恢复项目上下文后**复用手动 `execute` 同一条链路**，行已删则自清闹钟、不再可调度则 sync+忽略、执行异常返回 `accepted=false`）；`create/update/setStatus/delete` 挂 `sync`/`deleteIfPresent` 钩子（改频率即时生效），频率入口白名单校验 MANUAL/DAILY/HOURLY。
  - **依赖治理**：MDM pom 加 `yak-ops-business-data-service`(optional) + `yak-schedule-api`；`DataServiceSourceRegistry` 构造期急注入 `List<Provider>` ⇒ provider 只依赖 MDM 读侧、绝引 publisher/registry（同 R4 解环教训）；`data-service/DEPENDENCIES.md` 七章登记允许边 `MDM -> DataServiceSourceProvider`；**无新增 Flyway**（分发列 V9 已齐备，API Key 管理留在数据服务「API 调用」页，MDM 不建凭证 UI）。
  - **前端**：`DistributionTab`（配置表 + 新建/编辑弹窗默认「API 供数/手动/全量」、执行分发/重新发布、生效/停用/删除、发布态列实时反查「未发布/已发布/已停用/数据服务未启用」、path 跳 `/data-service/api/{id}`、「API 密钥」跳 `/data-service/access`），detail.tsx 分发 Tab 由空态改真接；未接入通道显示「通道未接入」且不给按钮。
  - 模块测试 184 全绿（新增：发布编排 7、provider 解析 5、闹钟登记 7、定时 handler 4，分发服务重写 16）；tsc 所改文件 0 报错（工作树另有并行 data-asset 未跟踪文件带来的基线漂移）。
- 实测记录（2026-09-20）：实体详情「分发配置」新建 CRM/销售中台（默认 API 供数 + 手动 + 全量）→「生效」→「执行分发」toast「**已发布至数据服务：3060 条生效记录可对外供数（/mdm/customer/1）**」，行内发布态「已发布」+ 最近分发 16:17:32·3060 条可服务；数据服务集市/API 详情（`/data-service/api/1`）均见该 API（运行中、`GET /api/v1/data-service/runtime/mdm/customer/1`、数据源=采集落地同款「客户库」、描述回显 MDM 自动发布口径）。**判据 1**：「API 调用」页建消费方「CRM 分发联调」并按指定 API 授权（1 个 API）发 Key → 浏览器内持 Key 调 runtime **200**，列 `[master_id, version, cust_id, cust_name, phone, city, member_level, update_time]`、首行「客户1 / C000001 / 福建市 / PLATINUM / v3」、`rowCount=1000 truncated=true`（默认 maxRows，未开分页）；去掉 Key 头复调 **401 Unauthorized**；API 详情概览「调用次数 2 · 成功率 50%」正是这一成一败（Key 明文只在页面内 fetch 使用，未落任何文件/仓库）。**判据 2（定时真执行）**：编辑该配置分发频率→「每小时」，toast「分发配置已更新，定时频率即时生效」（=`scheduleBridge.sync` 重登闹钟），DB `distribute_freq=HOURLY`；此后**无任何点击**，17:00:00.206→17:00:00.346 Quartz `0 0 * * * ?` 触发 handler：新增审计行 #440 `MDM_DISTRIBUTION_EXECUTE` · SUCCEEDED · `actor_id/actor_name=NULL`（后台线程无 HTTP 用户，恰证是定时器而非人点的），`yak_mdm_distribution.last_distribute_time=17:00:00`·count 3060·fail 0，页面「最近分发」同步显示 17:00:00；且 `yak_ops_data_service_api.update_time` 仍 16:19:28、`runtime_generation` 仍 2 ⇒ 定时执行命中「已发布且 `revision`（SQL 模板 hash）未变」分支、**不重发布**，正是实施记录所设的幂等口径。**附带修正**：数据服务 API 详情页此前把非「服务节点/遗留发布」的来源一律显示「Legacy」，MDM 发布的 API 被误标遗留 → `services/data-service/constants.ts` 加 `DATA_SERVICE_PROVIDER_SOURCE_LABELS`（`MDM_DISTRIBUTION=主数据分发`）并让详情页未知来源仍回落 Legacy，实测标签转「主数据分发 · - · 客户库」；该两文件 biome 报错数与 HEAD 持平（存量导入排序 + CRLF），tsc 0 新增。**遗留**：定时执行的审计 actor 为空（`execute(id,"system")` 的 operator 未映射到 audit 的 actor 字段），属展示口径，留待 R6/R7 一并看是否要给后台执行补 actor。

### R6 订阅与通知（一期站内信）　✅ 已完成（2026-09-21 实测）
- `MdmNotificationPolicyResolver`；审批通过/合并完成/发布完成按 `NotificationIntent` 派发；订阅管理 UI 落地；WEBHOOK 选项隐藏并注明依赖。
- 实施记录（2026-09-20）：
  - **派发点（三处，全部走 `MdmNotifier` 薄封装）**：①变更审批生效 `MdmChangeEffectService.applyApproved`—— UPDATE/DELETE 落库成功后 `changeApplied(entityId, appliedRecord, changeId, approver)`，重复回调在 `update` 行数为 0 时抛错、不发信；②合并完成 `MdmCleanService` 合并成功 → `mergeCompleted(entityId, masterId, mergedCount)`；③分发发布 `MdmDistributionService.execute`——只在 `PublishOutcome.changed()==true`（首发布/重发布/重新启用）时 `distributionPublished(entityId, targetSystem, count, apiPath)`，**HOURLY 定时命中「已发布且 revision 未变」分支不发信**（否则每天 24 封骚扰信）。`MdmNotifier` 依赖仅 `ObjectProvider<NotificationRouter>`/`ObjectProvider<CurrentProject>`/读侧 `MdmEntityService`，全程 try/catch 只 log——通知是副效应，绝不让发信失败弄挂主流程；`sourceId=entityId`、`actionPath=/mdm/modeling/{entityId}`、`sourceType` 三常量收敛在 `MdmNotifier.SOURCE_TYPES`。
  - **策略解析**：`MdmNotificationPolicyResolver`（order=100，`supports`=sourceType ∈ SOURCE_TYPES）——`listActiveByEntity(entityId)` 取生效订阅、只留 `notifyMode=EVENT`、经 `MdmUserDirectory.userIdOf` 解析平台用户，收件人为空即 `NotificationPolicy.disabled()`。**刻意不回落项目所有者**：订阅表就是收件人真相，没人订阅就不发，否则总览/审批期会给不相干的人刷信。`MdmUserDirectory` 包 framework `UserService.getUserBriefByUsername`，安全服务缺席/用户不存在/异常一律返回空。
  - **可达性诚实化**：`yak_mdm_subscription.subscriber_code` 解析不到平台用户 = 站内信永远收不到 → 订阅列表 VO 加 `reachable` 布尔（`MdmServiceController.toVO` 逐行实时判定），前端显示红标「账号不可达」，把静默丢信变成显式可见事实。
  - **notify_mode 白名单 + operator 服务端取**：`MdmServiceService` 先 `normalizeNotifyMode`（trim+upper，空默认 EVENT）再 `validateNotifyMode`——非 EVENT（含 WEBHOOK）抛新错误码 44038 `NOTIFY_MODE_UNSUPPORTED`（一个「订阅成功但永远不推」的假通路比报错更危险）；`createSubscription` 的 operator 由 Controller 经 `CurrentUserProvider.getCurrentUser(httpRequest)` 服务端取，不再信前端传参。
  - **前端**：新增 `SubscriptionTab`（实体详情「订阅」Tab，detail.tsx 分发配置之后）——列表（订阅方/通知方式 Tag/状态/订阅人/订阅时间/操作），新建·编辑弹窗订阅方=平台用户远程搜索单选（复用 `pageUsers`，选中自动带出 realName 作订阅方名称，编辑态锁编码）、通知方式仅「站内信」，页顶 Alert 注明「WEBHOOK 回调依赖外部 HTTP 出口，本期未接入」；`!reachable` 行内红标 + Tooltip 解释。services/mdm 补 `MdmSubscriptionRecord` 等类型与 5 个 API 函数。
  - **无新增 Flyway**（V10 订阅表列已齐备）、无新增依赖（`yak-ops-core` notification 抽象已在 MDM 依赖链上）。
  - 模块测试 210 全绿（新增：策略解析 9、Notifier 8、UserDirectory 4、审批/合并/分发派发断言 4、notify_mode 白名单 2；`PublishOutcome`/订阅 VO 相应改造）；全仓离线 `compile` rc=0；UI tsc 199 = 基线持平、mdm 文件 0 报错。**踩坑备忘**：主代码构造器加参后 maven 增量 test-compile 不会重编未改动的测试类 → 运行期 `NoSuchMethodError`，需同步改测试或 `rm -rf target/test-classes`。
  - **实测中挖出并修复的存量缺陷（2026-09-21，均非 R6 引入、但挡 R6 判据）**：
    ① **MDM 模块从建模块起就没有异常 advice**——兄弟模块（approval/alert/asset/…）各有 `*ExceptionHandler`，唯 MDM 缺席：所有 `MdmException`（44031–44038 全套业务码）到前端都裸奔成 Spring 默认 500「服务暂时不可用」，此前各票实测只打了成功路径没暴露。补 `MdmExceptionHandler`（`@RestControllerAdvice(basePackages="…mdm.controller")`，MdmException→`Result.fail(code,userMessage)`，刻意**不加** `Exception.class` 兜底以免吞认证/权限异常），+2 单测（模块 212 全绿）。WEBHOOK 44038 判据须随该修复重启后复验。
    ② **去重发现接口自 ticket 57 起从未真跑通**——`MdmRecordMapper.countDedupKeys` 用 `@Results(property=…)` 映射 record（`MdmDedupKeyRow` 无 setter），MyBatis 反射注入即抛 `There is no setter for property named 'matchKey'` → 页面点「执行去重发现」恒 500。单测全 mock repository 测不到，用临时探针测试直连真 MySQL 锤实根因后改 `@ConstructorArgs/@Arg`（用完即删探针）。**连带口径 bug**：`JSON_UNQUOTE(JSON_EXTRACT())` 把 JSON null 抽成字符串 `'null'`，逃过 `IS NOT NULL AND <> ''` → 462 条无手机号记录聚成假重复组排第一；`DedupSql.valueExpr` 加 `NULLIF(…,'null')` 归一，与 Java 侧 `javaKey`（raw==null 即不参与）对齐。修复后探针实测 60 个真重复组、假组消失。
    ③ **存量 `rule_expr` 带早期手写多余键，严格 Jackson 解析把清洗页整体判死**——该行 JSON 是 `{"and": true, "fields": [...], "condition": "AND"}`，比 record 形状多一个 `"and"`，而 `CleanJson` 用裸 `new ObjectMapper()`（默认 `FAIL_ON_UNKNOWN_PROPERTIES=true`）→ `parseExpr` 抛「规则表达式 JSON 不合法」→ 去重发现 44023，且 `MdmCleanRuleVO.parseSafely` 让规则列表也一起显示「暂无去重规则」（有数却看不见，比报错更误导）。写侧（`cleansing/index.tsx` 只发 `{fields, condition}`）本来是对的，**正确修法是读侧宽容**：`CleanJson` 的 mapper 关掉 `FAIL_ON_UNKNOWN_PROPERTIES`（多余键按无意义忽略），新增 `CleanJsonTest` 两条钉住「存量串可解析」+「当前写形状可 round-trip」，模块测试 214 全绿。顺带修 `validateNotifyMode` 把 code message 与 detail 各拼一遍导致 44038 文案重复（改为只传 `notifyMode` 做 detail，由 `MdmException.buildMessage` 统一以「：」拼接）。
- 实测记录（2026-09-21，全程页面真实操作）：
  - **订阅创建**：实体详情「订阅」Tab 新建 → 订阅方远程搜索选真实用户 `root`（自动带出「系统管理员」）、通知方式仅「站内信」→ 列表一行 `EVENT · 生效 · reachable=true`；DB `created_by=root` 由服务端取（不信前端传参）。
  - **审批通过发信 + 回跳**：`/approval/todo` 两级通过（第 1 级 → 当前级次推进第 2 级 → 确认通过）→ 收件人 `/system/messages` 顶部出现「**主数据变更已生效**」（`sourceType=MDM_CHANGE_APPLIED · sourceId=1 · actionPath=/mdm/modeling/1`），详情抽屉显示「项目 · 默认空间」+ 正文「变更单 #3 已由 root 通过，记录升至 v5」→「前往处理」实跳 `/mdm/modeling/1`；DB 复核 change #3=APPROVED、记录 v5、v4/v5 快照链齐。
  - **反骚扰判据（`PublishOutcome.changed` 双向成立）**：同一实体同一 HOURLY 配置，当日 **05:00/06:00/07:00/08:00/09:00 五次定时执行**（审计 #466–#470，`actor_id/actor_name=NULL` 恰证非人工）**站内信 0 条**；而 09:30 改属性→重发布 ⇒ 消息 #4「主数据分发已发布」、10:13 删测试属性（revision 翻）后点「重新发布」⇒ 消息 #6。定时静默、真发布有声，两个方向各有实证。
  - **定时零发信的当场复验（11:00 正点）**：等到 HOURLY 闹钟再次触发——审计 **#499** `MDM_DISTRIBUTION_EXECUTE` · SUCCEEDED · `started_at=11:00:00.169` · `actor_name=NULL`（定时器，非人工），`yak_mdm_distribution.last_distribute_time=11:00`·3059 条·fail 0；而 `yak_security_message` 顶部仍是 **#6（10:13:51）**，未新增任何一行。与上一轮的 5 次历史定时合并成完整判据：**纯执行（revision 未变）恒不发信，只有真发布才发信**，且是"等下一个整点当场看到"而非回溯推断。
  - **合并完成发信（原判据被 ②③ 连挡两轮，修复后一次通过）**：清洗页选实体「客户（customer）」→ 去重发现选「手机号重复检测」→「执行去重发现」出 **60 个重复组**（首组「手机号=+8613184446671(EXACT) · 2 条 · 置信度 100%」）→ 行内「合并」→ 预览正确呈现「合并后属性（主记录优先，非空补全）」+「合并后来源（多源去重并集）」→「执行合并」toast「合并成功」→ 站内信 #5「**主数据合并完成**」（未读 1→2，正文含主记录 `fb7be899…` 与「1 条重复记录」，`actionPath=/mdm/modeling/1` 同样可回跳）；同页「合并日志」新增「主记录 6 · 主记录 fb7be899… 合并 1 条记录 · root · 10:10:13」，复跑发现 **60→59 组、该手机号组已消失**，分发配置「最近分发」由 3060 条变 **3059 条**（被合并记录转 MERGED 不再 ACTIVE）——三处口径自洽。
  - **notify_mode 与可达性**：`notifyMode=WEBHOOK` 提交返回 **44038**「该通知方式尚未接入，一期仅支持站内信（EVENT）：WEBHOOK」（新 advice 生效后如实回业务码而非 500，文案修正后无重复拼接）；不存在的订阅方账号在列表打红标「账号不可达」；`" event "` 提交后 normalize 存库为 `EVENT`。
  - **遗留清理**：R5 期为翻 revision 临时加的 `test_flag（分发验证标记）` 属性（0 条记录携带）已在「属性」Tab 删除（toast「已删除」），删除动作本身即构成本轮「真重发布」样本之一。
  - **R5 遗留（后台执行审计 actor=NULL）定调**：一期**保留现状**——actor 为空正是「这条执行来自定时器而非人」的唯一可观测证据（本轮判定定时是否发信完全依赖它），给后台执行补 actor 反会让手动/定时两类执行在审计里混同；若总览需要区分展示，走 R7 的「来源=定时/手动」标注，不改审计语义。

### R7 总览与闭环可视化（review P2/P3）　✅ 已落地并实测验收
- 总览接 `/api/v1/mdm/overview` 六卡 + 管线状态条（采集/加工/清洗/审批/分发节点挂真数与红点）；清洗页补 57 UI/忽略组/按 ruleType 过滤；实体动线以 entityId 串联。
- 实施记录（2026-09-21）：
  ① **总览后端从「无界 list + N+1」改为 SQL 有界聚合**——原实现走 `entityRepository.findAll()` 再逐实体四次 count，违反 `docs/home-overview-contract.md`；改为 `MdmOverviewMapper` 一次聚合出六卡与管线、一次 `LIMIT` 出实体卡片（默认 10、硬上限 50）。容错口径照契约执行：任一项取不到回 `-1`，前端渲染「-」，**不把查询失败伪装成 0**。「加工」节点真相在数据开发域、MDM 侧无表，按同一口径固定 `-1` 而非编一个 0。
  ② **忽略组做成账本，而不是记录状态**——`MdmRecordStatus` 是生命周期枚举，表达不了「确实重复、但业务上确认不合并」，硬塞 `IGNORED` 会污染状态机与分发口径。新增 `V16__create_mdm_dedup_ignore.sql`（`project_id/entity_id/rule_id/match_key/match_basis/reason/created_by`，唯一键 `(project_id, rule_id, match_key)`）：
     - **为何按规则维度**：`match_key` 是把若干属性用 `CHAR(1)` 拼出来的串，语义随规则变——手机号规则下的键和姓名+城市规则下的键不是同一种东西，跨规则共享静音会误伤。故「忽略只对当前规则生效，换规则即重新出现」（弹窗与页面各写明一句）。
     - **为何 `utf8mb4_bin`**：列必须二进制比较。用表默认 `unicode_ci` 会把大小写/重音不同的两个键判成同一个，一条忽略静音两组；且 `JSON_UNQUOTE(JSON_EXTRACT())` 产出的键本身是 `utf8mb4_bin`，与 `*_ci` 子查询列相比即 1267（同一坑 R6 已踩过）。
     - **键原样入库、零归一**：只校验长度（≤512），不 trim 不 lower——排除是拿这个键去比 SQL 聚合结果，任何改写都产出一条永不生效的静音记录（`ignoreKeepsMatchKeyByteExactAndRegistersOnce` 钉住）；同键重复登记幂等。
     - **排除留在 SQL 侧**（`countDedupKeys` 内 `NOT IN (SELECT match_key …)`）：聚合后再过滤会让分页 total 与页内容口径不一致（总数 10、首页只给 9）；`#{}` 绑定也避免把用户键拼进 `${}`。
     - 撤销即删账本行，删不到回 **44039 `DEDUP_IGNORE_NOT_FOUND`**；删规则连带清该规则的账本（规则没了键就无意义），删除确认框如实提示这一句。
  ③ **ruleType 过滤补到读侧并暴露为页面筛选**——`GET /clean/rules` 收可选 `ruleType`，非法值按 `INVALID_CLEAN_RULE` 回业务错，不让前端拼错参数却"看起来能用"地拿到全集。清洗页规则表加类型列 + `去重/标准化/补全/全部` 分段筛选，**默认「全部」**，避免出现「刚创建的规则落在当前筛选下看不见」。
     - **顺带修一个真崩溃**：`MdmCleanRuleVO` 对 STANDARDIZE/COMPLETE 返回 `fields=null`，而表格无条件 `record.fields.map(...)`——非去重规则一进列表即整行抛错。TS 契约同步改为 `fields: MdmMatchField[] | null` + `rawExpr`（原始表达式串），非去重行改摊 `rawExpr`；编辑入口的取舍见 ⑦。
  ④ **entityId 进 URL 串动线**——清洗页选实体写回 `?entityId=`（`replace:true`，不推历史栈），刷新/回退不丢上下文；总览实体卡片「去清洗」即按此深链，形成「总览 → 实体 → 清洗」一条线。审批侧同理走 `?status=PENDING`，但落点在验收后一度因 `/mdm/approval` 被降级成 redirect 而改指 `/approval/todo`（见 ⑩ 第一条），现随台账页接回菜单而**最终定为 `/mdm/approval?status=PENDING`**（见 ⑪）：卡片与节点的「待审批变更」数字来自 MDM 变更单表，点进去必须是同一批行；审批中心待办混着数据资产/建模等其他域的单据，落到那里就是「数字与列表对不上」。
  ⑤ **总览页替换占位**——六卡 + 管线状态条（红点=待处理数）+ 实体卡片列表；节点跳到**真正处理这件事的页面**（采集→`/sync/batch-link-up`、加工→`/data-development`、清洗→`/mdm/cleansing`、审批→`/mdm/approval?status=PENDING`、分发→`/data-service/overview`），MDM 不再自建平行入口（唯一的例外是审批节点落在 MDM 自己的变更单台账：那里只有查进度与撤回，通过/拒绝仍只有审批中心一处，不构成第二套审批面）。接口失败时显示「总览加载失败 · 请刷新」，不降级成"看着像没有主数据"。
  ⑥ **R6 遗留「来源=定时/手动」标注：一期不做**——总览是聚合视图，要区分得把执行明细搬进总览（新接口/新列），而审计里 `actor=NULL` 已经足以反查这条执行来自定时器（本轮 R6 判据完全依赖它）；为标注改审计语义反而让手动/定时混同，收益不抵代价。
  ⑦ **补齐 ticket 57 的前端交付（review 1.6「标准化/补全前端为零」）**——全仓 grep 证实 `/clean/rules/typed`、`/transform/*` 无任何调用方、清洗页按钮只写「新建去重规则」，属实。规则弹窗改为**类型感知编辑器**：
     - 一个表单按 `ruleType` 渲染对应三段之一（去重=匹配字段 + 组合条件；标准化=属性 + 原值→目标值 平铺行；补全=属性 + 默认值 行），`buildRuleExpr` 一处组装成各类型 JSON；**类型创建后不可改**（后端 `updateGeneric` 按存量 `ruleType` 校验表达式），编辑态即锁死并说明原因。
     - 「编辑」对三种类型全部恢复（此前对非去重类型藏入口是权宜：表单只会写去重表达式，用它编辑等于覆盖别人的规则）；规则表「规则内容」列不再摊原始 JSON——标准化显示 `gender：M→1、F→2`、补全显示 `grade 空值填「普通」`；「组合条件」列并进内容列（只在多字段去重时出现），避免三行规则里两行空。
     - 行内「去发现」把该去重规则直接填进发现区，省一次下拉选择；「预览影响」拉 `GET /clean/transform/{ruleId}/preview`，弹窗逐条显示 `属性: 原值 → 新值`（只摊真被改掉的属性，整条快照会淹没变更点），底部如实写「将更新 N 条生效记录」，`affectedCount=0` 时执行按钮禁用；执行前二次确认文案点名「**直接改写记录属性，不生成变更审批单、不可回滚**」——这是清洗链路与 R4 审批链最大的口径差，必须在按下之前说清。
     - 表单侧拦掉两处「静默吃掉输入」的组合：同一属性下重复原值、同一属性多个默认值（组装成对象时同名键后写覆盖先写），校验直接报错而不是让用户配了两条。
  ⑧ **「启用」开关接到执行侧**——此前 `enabled` 只存不读（去重发现/预览/执行全都不看它），停用一个规则在业务上等于没停，开关是装饰。现 `requireEnabled` 在 `findDuplicates`/`previewTransform`/`applyTransform` 三个入口统一拦（回 `INVALID_CLEAN_RULE` 并点名规则名），页面同步：只有**启用中**的去重规则进「去重发现」下拉，停用规则行内不给预览入口（Tooltip 说明「启用后才能预览与执行」），切换停用且该规则正被选中时清空选择。+2 单测（`findDuplicatesRejectsDisabledRule`、`previewTransformRejectsDisabledRule`）。
  ⑨ **预览响应改为「总数全量、明细有界」**——`TransformPreview` 加 `truncated`，明细上限 `MAX_PREVIEW_ITEMS=50`：明细是给人判读规则的样本，不该跟着实体规模线性膨胀响应体（当前 demo 实体 3,059 条生效记录）；`affectedCount` 仍是全量口径，前端在 truncated 时明说「只展示前 50 条样本，执行仍按全量」。+1 单测钉住「70 条命中 → 总数 70、明细 50、truncated=true」。
  ⑩ **实测当场挖出并修掉的四处（都不是 R7 新写的逻辑引入的，但都归在 R7 判据下）**：
     - **审批深链是一个「看着能用」的死链**——总览点「审批」节点，URL 落到 `/approval/todo` 且 `?status=PENDING` 无影无踪：M0 菜单重排（`5462d6eb5`）已把 `/mdm/approval` 改成纯 `redirect`（`config/routes.ts:59`），redirect 不带 query。当场先止血：总览两处（卡片 + 节点）直连 `/approval/todo`，少一跳也不再假装参数生效。**连带遗留**：`src/pages/mdm/approval/index.tsx`（R4 交付的变更单台账，含「撤回」）自那次重排起**无任何路由可达**，而审批中心各页全无撤回入口 ⇒ MDM 变更撤回当前零 UI 入口，属菜单重排的漏账。R7 内不改别人的交付面，验收后单独定调接回菜单，见 ⑪。
     - **编辑回填全空**——标准化/补全规则点「编辑」后名称在、映射/补全行全空（表单 store 实测为 `defaults:[{}]`）。根因是弹窗 `destroyOnClose` + 表单 `preserve={false}` + `Form.useWatch` 首帧还没有值三者叠加：`setFieldsValue` 先写 store，但首帧按 `?? 'DEDUP'` 兜底挂错了 `Form.List`，等 watch 翻正、正确那段挂载时，上一段的卸载把刚写入的行一起清掉。**修法**：`formRuleType` 的兜底直接读 `form.getFieldValue('ruleType')`，保证首帧就挂对段落（用户后续点选仍由 watch 驱动）。修复后实测两类均完整回填（`member_level：VIP→1 / SVIP→2` 两行、`city 空值填「未采集」`）。
     - **发现区空态文案把「全部停用」说成「筛选挡住了」**——停用唯一去重规则后（此时筛选就是「全部」），下拉空态仍提示「切回「去重」或「全部」」，把人支使去点一个没问题的地方。改为按三种原因分别文案：被类型筛选挡住 / 去重规则已全部停用 / 还没建过去重规则（后两种靠 `rules.some(ruleType==='DEDUP')` 区分），实测停用态显示「去重规则已全部停用，启用后才能执行发现」，启用后回到正常占位。
     - **44022/44023 的码文案还写着「去重规则」**——ticket 57 之后这两码由三类清洗规则共用（`MdmCleanService.get` 与 `validate*` 都是通用路径），实测直接出现了自相矛盾的提示「**去重规则不合法**：规则「会员等级码值标准化（R7 验证）」已停用」。改名为「清洗规则不存在/清洗规则不合法」（码值不动，`REQUIREMENTS.md`/issue 56 里的历史表述不改），下次重启生效。
  ⑪ **验收后定调：变更单台账接回 `/mdm` 菜单（2026-09-21 追加）**——⑩ 第一条的漏账由用户定调「接回菜单」而非删页，落地时守住三条：
     - **不改已应用迁移，用 V2038 覆盖**：`V2036__domain_navigation_regroup.sql` 已在库，按其 §5 的同一形状新增 `V2038__reinstate_mdm_change_ledger_menu.sql`（纯 upsert，幂等）把 `mdm-approval` 置回 `visible=1 active=1`，并重申 root 授权（防有环境重建过角色绑定）。
     - **menuCode 是 RBAC 契约，只改显示名**：menuCode 仍是 `mdm-approval`、权限仍 `mdm:read`、路径仍 `/mdm/approval`；名称改「主数据变更」——页面只有查进度与撤回，沿用「主数据审批」会在侧栏与「审批中心 › 待办中心」形成两个都像能审批的入口，且与同组 主数据总览/建模/识别/清洗 的命名一致。页面标题与副标题同步（副标题点名「撤回在途申请」这一本页独有动作）。
     - **redirect 必须删，不能与真路由并存**：`config/routes.ts` 那条一跳保底去掉，否则 `/mdm/approval` 被声明两次且永远跳过页面。菜单可达性由三处契约钉住：`securityMenuCodes` 补 `mdmApproval`、`navigation.ts` 登记路由（`menuGroup: 'mdm'`, order 50, icon `workflow` 与 DB `icon_key` 对齐）、`navigationMenuContract.test.ts` 的迁移清单加 V2038（否则有效目录里退役行不被覆盖 ⇒ 报「前端声明了后端没有的菜单」）。
     - **总览审批深链回到台账**：卡片与节点改指 `/mdm/approval?status=PENDING`（该页真的读这个 query），落点不再是对不上数的跨域待办清单——理由见 ④。原「先直连 `/approval/todo` 止血」保留在 ⑩ 里作为过程记录。
- 验证状态：`./mvnw -o -pl yak-ops-business/yak-ops-business-mdm test` → **Tests run: 226, Failures: 0, Errors: 0 / BUILD SUCCESS**（较上轮 +3；改完 44022/44023 文案后复跑仍 226 全绿，无单测钉住旧文案）；`yak-ops-ui` `tsc --noEmit` **199 = 基线**、`src/pages/mdm` + `src/services/mdm` **0 报错**，`biome lint` 对 `cleansing/index.tsx`、`overview/index.tsx` 无诊断。⑪ 之后：`jest src/config/navigation.test.ts src/config/navigationMenuContract.test.ts` → **2 suites / 20 tests 全绿**（含新加的「台账在 mdm 组内且 /mdm/approval 可解析」正向用例），`tsc` 仍 **199 = 基线**，`biome lint` 对 ⑪ 改动的 7 个文件无诊断。
- **待下次重启生效（两项）**：① ⑩ 第四条 44022/44023 的文案（纯文案，码值不变）；② `V2038` 菜单迁移。二者影响面不同，实测已分清：root 验收账号走 `security:root` 旁路菜单校验，**UI 侧改完即时可见可用**（本轮实测即如此）；V2038 真正修的是**非 root 角色**——重启前 `mdm-approval` 在库里仍是 `visible=0 active=0`，其菜单码不随菜单接口下发，这些角色既看不到该项、直连 `/mdm/approval` 也会撞 403。故 V2038 不是装饰，是 RBAC 真相回正。
- **已知限制（如实记录，一期不改）**：
  - `previewTransform`/`applyTransform` 服务端仍 `listActiveByEntity`（LIMIT 10000）全量拉到 JVM 逐条判规则，未下推 SQL——命中判断要读 JSON 属性，下推需为每种规则类型写方言 SQL，收益不抵复杂度；万级实体下「预览」等价于一次全表扫 + 全量传输，是否要加游标看真实数据量再定。
  - `applyTransform` 直接 `update` 记录并 bump `version`，**绕过变更单/审批**（清洗是"批量改写"，与人工改一条本就不同口径）。若后续要求清洗也过审批，属新流程设计而非本 slice 修补。
- 实测记录（2026-09-21，用户 rebuild + 重启后端、起 `:8000` 后全程页面真实操作；登录 root、空间「默认空间」=project 1）：
  - **V16 与迁移面**：`flyway_schema_history_mdm` 顶格 `16 create mdm dedup ignore · installed_on=2026-09-21 11:18:45 · success=1`，`yak_mdm_dedup_ignore` 可查（重启未重复执行，符合「已应用不改」口径）。
  - **去重发现出组（顺带复验 R6 的两处修复）**：清洗页 `?entityId=1` 直达即自动选中「客户（customer）」；「去重发现」下拉能选到**存量带多余 `"and"` 键**的 `手机号重复检测`（`CleanJson` 读侧宽容生效）→「执行去重发现」出 **共 59 个重复组**（6 页 × 10），首组「手机号=+8613187132846(EXACT) · 2 条 · 置信度 100%」——与 R6 修完 `NULLIF(…,'null')` 后的 59 组（合并掉 1 组后）完全一致，无假组复现。
  - **忽略 → 消失 → 账本 → 撤销 → 复现（双向判据成立）**：行内「忽略」弹窗点名该组（`手机号=+8613187132846(EXACT)`、组内 2 条）并写明「忽略只改『是否再提示』，不动任何数据」；原因下拉预置首项（能默认就不留空），选「测试/占位数据」+ 备注「R7 实测：验证忽略账本」提交 ⇒ 待处理列表 **59 → 58 组**、该组当场消失；「已忽略组」账本出现一行 `手机号=+8613187132846(EXACT) · 测试/占位数据：R7 实测：验证忽略账本 · root · 2026-09-21T12:34:02`；点「撤销忽略」（二次确认「撤销后该重复组会重新出现在去重发现结果里」）⇒ **58 → 59 组**、该组回到首行、账本回「该规则下暂无忽略组」；DB 复核 `select … from yak_mdm_dedup_ignore` **0 行** ⇒ 撤销是真删账本行，不是打标记。
  - **「忽略只对当前规则生效」按结构判据钉住（页面上不可观测）**：demo 实体只有一条去重规则，换规则重新出现这件事在 UI 上无法演出真样本，故查结构而非硬造：`SHOW INDEX FROM yak_mdm_dedup_ignore` ⇒ 唯一键 `uk_yak_mdm_dedup_ignore (project_id, rule_id, match_key)`（忽略天然按规则维度，跨规则不共享静音），且 `match_key` 列排序规则为 **`utf8mb4_bin`**（同表其余文本列仍 `unicode_ci`）——键按二进制比较，不会把大小写/重音不同的两组并成一组；发现侧 `MdmRecordMapper.countDedupKeys` 的排除子查询 `NOT IN (SELECT match_key FROM yak_mdm_dedup_ignore WHERE project_id=# AND rule_id=#)` 同样按 `rule_id` 收口，两处口径一致。
  - **总览挂真数**：`/mdm/overview` 六卡 = 实体 1 / 生效记录 3,059 / 待审批变更 0 / 清洗规则 2 →（本轮建两条、删一条临时规则后复访）3 / 分发目标 1 / 订阅方 1；管线「采集 1 › **加工 -** › 清洗 2·已合并 1（灰）→ 复访 3·已合并 1 › 审批 0 › 分发 1」——加工节点按 `-1` 口径渲染「-」而非假 0，`ATTENTION_META` 只有真待办才亮红点（审批 0 无红点、清洗「已合并」为历史结果故灰色不告警）；实体卡片显示 `记录 3,059 · 分发 1 · 订阅 1`，「去清洗」实跳 `/mdm/cleansing?entityId=1` 且实体已选中。
  - **审批节点跳转的三段过程**：原始状态是死链——点「审批」落 `/approval/todo` 且 `?status=PENDING` 被 redirect 吞掉（详见 ⑩）；当场止血为直连 `/approval/todo`（少一跳、不再假装参数生效）；⑪ 把台账接回菜单后定为 `/mdm/approval?status=PENDING`，实测点节点与点「待审批变更」数字均落到该屏且状态筛选自动为「审批中」，卡片数字与台账条数一致。
  - **工单 57 前端链路（编辑器 → 列表 → 回填）**：类型筛选「补全」下点「新建清洗规则」，弹窗类型自动落在**补全**（跟随当前筛选，省一次点选），段落变为「补全默认值（仅当属性为空时填入）」、名称占位变「如：会员等级补全」、说明为「给空值属性填默认值，已有值的记录不动」；属性下拉可检索出实体 5 个属性（`城市（city）`…），填 `city 空值填「未采集」` 保存 ⇒ 列表新行内容列显示 chips 而非原始 JSON。再新建一条标准化（切类型 radio 后段落即时换成「值映射（原值 → 目标值）」）预览用；两类「编辑」均完整回填（标准化两行 `VIP→1 / SVIP→2`、补全 `city / 未采集`），且编辑态三类类型 radio 全 `disabled`、当前项勾选，与「类型创建后不可改」一致。删除临时规则走二次确认「删除标准化规则 / 确定删除规则「码值截断验证（R7 实测）」？」，删后列表即刻 3 行。
  - **预览影响：0 命中与截断两个分支各有实证**：`城市缺失补全` 预览 ⇒ 「没有记录会被改动」+「受影响记录 0 条，无需执行」，且**「执行清洗」按钮 `disabled`**（0 命中不给执行）；`member_level: PLATINUM→1` 预览 ⇒ 明细 50 行、底部「**将更新 525 条生效记录** / 明细只展示前 50 条样本，执行仍按全量 525 条生效；清洗直接改写记录属性，不生成变更审批单」；`truncated` 字段确认由重启后的后端返回（同端点直连 JSON 里有 `"truncated":true`）。二次确认文案点名「将按规则「…」直接更新 525 条生效记录的属性值，不生成变更审批单、执行后不可回滚。」——点「返回」中止，DB `member_level` 分布 `PLATINUM=525` **纹丝不动**（且 525 与预览数逐一吻合，证明全量口径不是估的）。
  - **停用开关：UI 与后端两侧都拦得住**——停用标准化规则 ⇒ 行内「预览影响」转 `ant-typography-disabled` + Tooltip「规则已停用，启用后才能预览与执行」；停用唯一去重规则 ⇒ 「去重发现」下拉不再给选项（并显示 ⑩ 修好的准确空态）。后端侧门禁直连端点复验（带 `X-YAK-SECURITY-PROJECT-ID:1`）：`GET /clean/transform/2/preview` 与 `POST /clean/dedup/discover`（传停用规则 id）**均回 44023**「…规则「会员等级码值标准化（R7 验证）」已停用，请先启用后再执行」，HTTP 层是 200 + 业务码（R6 补的 `MdmExceptionHandler` 生效，不再裸 500）；`applyTransform` 因会真写 525 条 demo 记录**不点**，其门禁由 `previewTransformRejectsDisabledRule`/`findDuplicatesRejectsDisabledRule` 两单测与同一 `requireEnabled` 代码路径覆盖。全部规则已恢复启用。
  - **⑪ 台账接回菜单的实测（同日后半程）**：侧栏 数据治理 › 主数据 第 5 项已是「主数据变更」→ `/mdm/approval`（直连时 URL 与 query 原样保留，不再被 redirect 吞）；`?status=PENDING` 由页面真的读取 ⇒ 状态下拉自动落「审批中」。为验「撤回」这一**只有本页才有**的入口，先造在途单：实体详情「记录」Tab 行内「变更申请」→ 改 `city` 为「上海市-菜单接回验证」→「提交审批」toast「变更申请已提交，等待审批中心处理」⇒ 台账「审批中」**共 1 条**（`#4 · fb7be899… · 修改 · {"city":"上海市-菜单接回验证"}`，行内动作「审批单 + 撤回」），同刻总览「待审批变更」=**1**（点该数字实跳的正是这一屏）；点「撤回」→ 二次确认点名「将同时撤销审批中心在途单据；仅发起人可撤回」→「确认撤回」toast「已撤回」⇒ 筛选回「暂无数据」、总览该卡同回 **0**。DB 复核：`yak_mdm_change #4 = WITHDRAWN`、`yak_approval_instance #10 = CANCELED`（`finish_time=13:28:16`，证明确实连带撤了审批中心的单），而记录 `fb7be899…` 仍是 **v4 / city=浙江市**（撤回不改数据）。不带筛选的台账共 4 条（#1/#3 已生效、#2/#4 已撤回），#4 作为本轮样本留档，与 R4 的 #2 同形。


> 依赖关系：R1→R2 是主链路硬顺序；R3/R4 可与 R2 并行；R5 依赖 R2 有数；R6/R7 收尾。原 review.md 的 P0 各项映射：1.1→R4，1.2/1.3→R0/R2，1.4→R2，1.5→R4，1.6→R7(57 UI)+R4，1.7→R1+R7，1.8→R5，1.9→随 R1 一并回填引用校验。

---

## 五、风险与待验证（开工前各半天内可定）

1. **sync sink 建表能力**：落地表若不存在，OVERWRITE/UPSERT 是否自动建表？不自动→MDM 预建 DDL（方案已含，只需确认谁执行：数据开发预建任务或 saveGuide 前置 SQL）。
2. **数据开发 SQL 任务的执行连接**：确认其执行上下文即平台业务库（否则 R2 落点改"任务目录 MDM TaskExecutor"，quality 四件套模板已备好，属方案内切换而非重设计）。
3. **DataServiceSourceProvider 的 Revision 语义**：MDM 记录表是活数据非不可变资产——发布粒度应为"实体查询定义"（SQL 模板固定、数据实时查表），确认 resolve 出的 definition 不锁数据快照即可成立。
4. **GUIDE_SINGLE 落地表列集合**：源列改名对可用，但落地表需要额外系统列（批次号/来源键）——确认 `mapping.columns` 之外的列缺失是否致写失败；必要时落地表就按源列全建，系统列放加工任务补。
5. **平台库自注册数据源的凭证治理**：若安全侧不接受，走"内置平台数据源"小契约（见二节末注），不阻塞 R0~R4。
