# 本体驱动数据智能体 · 代码理解导览（总—分）

> 定位：面向「代码开始失控、需要重新掌握全貌」的开发者的**按业务流代码梳理**。
> 本文档与 `docs/agent/data-agent-business-flow.md`（业务流）、`data-agent-technical-architecture.md`（架构）互补：
> 那两篇是"现状说明"，本篇是**带你读代码的路线图**——每一步给出：业务是什么、代码在哪、怎么读、关键机制、业务案例。
> 适用范围：`data-ops-business-ontology` / `data-ops-business-dataset` / `data-ops-business-semantic` / `data-ops-business-agent`。

---

# 上篇 · 总览：先建立整幅画面

## 0.1 一句话定位

这是一个「**本体作为口径地基 → 数据集作为取数闸门 → 语义层把提问翻译成受控查询 → 智能体让 LLM 只会"说话和选工具"、永不接触 SQL**」的数据分析链路。

核心设计信念（贯穿全文，读任何模块时先记住这四条）：

1. **发布即承诺**：只有 PUBLISHED 的对象/属性/指标进入查询面，DRAFT/DEPRECATED 不可见。
2. **结构化分层**：LLM 只负责"问什么语言 / 调哪个工具"，SQL 是语义层编译产物——模型永不亲手拼 SQL。
3. **拒绝优于错数**：能力没承接时显式报 `[错误码]`，绝不静默给出错误口径。
4. **每次取数必然留痕**：query_log / step / performance / turn_event 四层证据可重建一次完整回答。

## 0.2 四模块分工（一张职责表）

| 模块 | 目录 | 角色 | 一句话职责 |
| --- | --- | --- | --- |
| 本体 | `data-ops-business-ontology` | 地基（truth 面） | 业务世界的"字典"：对象/属性/指标/关系/术语建模，能力物化，发布即承诺，变更单。**不执行数据** |
| 数据集 | `data-ops-business-dataset` | 闸门（执行面） | 冻结上游 SQL 为不可变版本快照，提供字段 schema + 只读查询运行时。**唯一 SQL 生产面** |
| 语义 | `data-ops-business-semantic` | 翻译官 | 把结构化 `SemanticQuery` 守卫校验后，**结构翻译**为 dataset 请求；返回结果+口径。**不碰物理列名** |
| 智能体 | `data-ops-business-agent` | 调度者 | 自然语言入口：提交/执行分离、ReAct 推理、12 个工具、SSE 流式、HITL 澄清、证据留痕 |

依赖方向（单向、由架构测试强制）：

```text
agent ──gateway──▶ semantic ──gateway──▶ ontology(读) / dataset(执行)
                       └────────────────▶ dataset ──▶ 数据源 SPI 执行
```

- semantic 消费 ontology 只通过 `gateway/ontology/OntologyDefinitionAdapter.java`（全模块唯一允许 import ontology 的文件，只读）。
- agent 消费 semantic/dataset 只通过 `gateway/` 下的适配器（唯一走廊）。
- ontology、dataset、semantic 都是 Bean 直调，**不过 HTTP**。

## 0.3 主线业务案例（全文反复回扣）

**业务背景（来自 `docs/agent/seed-order-domain.sql`，电商订单域）**：
- 业务域：订单域（`yak_onto_domain`）
- 三个对象：销售单 `sales_order`（ENTITY/PROCESS）、客户 `customer`、商品 `product`，均已 PUBLISHED
- 销售单关键属性：`order_no`(标识)、`order_date`(时间维)、`order_status`(口径关键：仅 PAID 计入销售额)、`region`(区域)、`amount`(金额 MEASURE)、`quantity`(数量)
- 五个指标：`order_gmv`(销售额 SUM 口径 filter 仅PAID)、`order_cnt`(订单量 COUNT)、`customer_cnt`(客户数 COUNT_DISTINCT)、`avg_order_value`(客单价 AVG)、`total_quantity`(销量 SUM)
- 两条关系：销售单-客户、销售单-商品（MANY_TO_ONE，DIRECT）
- 12 条术语：销售额/GMV、客单价、订单量、客户、商品、已支付……

**贯穿全文的问题**：

> 「**上个月华东区域销售额是多少？同比呢？**」

| 环节 | 这段对话在系统里发生了什么 |
| --- | --- |
| ① 建模 | 本体里已经有 `sales_order` 对象、`region` 维度属性、`order_gmv` 指标（口径=仅已支付） |
| ② 锚定 | `sales_order.dataset_id` 指向数据集；`amount` 属性 `dataset_field_id` 指向该集字段 |
| ③ 语义 | LLM 把这句话翻译成 `SemanticQuery(sales_order, [region], [order_gmv{timeGrain=MONTH, comparePeriod=YOY}], [], 上月窗口, limit)` |
| ④ 会话 | 智能体提交轮次、ReAct 推理、调用 `run_semantic_query` 工具 |
| ⑤ 执行 | 语义层守卫 → 翻译成 dataset 请求 → SQL 编译 → 只读执行（当期/上期/同比三列） |
| ⑥ 输出 | QueryResult+口径 回喂 LLM → 自然语言回答 → 口径卡 + 证据链落库 |

---

# 下篇 · 分步详解

---

## 第 1 步：本体建模（ontology）—— 业务世界的"字典"

### 1.1 八类建模实体（对应 8 张 `yak_onto_*` 表）

| 概念 | 表 | 关键字段 | 业务含义 |
| --- | --- | --- | --- |
| 业务域 | `yak_onto_domain` | parent_id(树) | 域分组（"订单域"） |
| **对象** | `yak_onto_object` | `code`(唯一)、`status`(DRAFT/PUBLISHED/DEPRECATED)、`concept_type`(ENTITY/PROCESS)、`dataset_id`(锚定)、`identify_by`(JSON 标识属性集)、`anchor_status`(OK/BROKEN) | 查询的统计主体（销售单/客户/商品） |
| **属性** | `yak_onto_attribute` | `logical_name`(对象内唯一，逻辑名!)、`role`(DIMENSION/MEASURE)、`data_type`、`is_time`、`is_pk`、`ds_id/db_name/tbl_name/col_name`(物理四元组，A2)、`dataset_field_id`(锚定，B 权威)、`allowed_operators`(JSON 物化白名单)、`requires_json`(约束声明)、`ai_context` | 对象字段的语义化定义；**逻辑名是查询面唯一字段引用** |
| **指标** | `yak_onto_metric` | `code`(唯一)、`metric_type`(ATOMIC/DERIVED/COMPOSITE)、`aggregation`(SUM/AVG/COUNT/COUNT_DISTINCT/MAX/MIN)、`base_attribute_id`、`business_filter_json`(口径过滤)、`time_grain`、`unit`、`version`、`status` | 度量口径的第一语义（`order_gmv`） |
| 关系 | `yak_onto_relation` | `join_type`(DIRECT/INDIRECT)、`join_conditions`(JSON)、`cardinality` | MVP 仅 DIRECT 参与 JOIN 编译 |
| 术语 | `yak_onto_glossary` | `term`、`target_type/target_code`、`ai_context`(synonyms/examples/instructions) | 自然语言→概念的匹配词表 |
| 函数 | `yak_onto_function` | `binding_type`(v1 仅 SQL_TEMPLATE)、`sql_template`、`use_rule`、`scenario_category` | "过程计算"型取数（只读强制） |
| 动作 | `yak_onto_action` | `risk_level`(HIGH/MED/LOW)、`effect` | **仅登记，执行端明确拒绝** |

另外还有流程表（`yak_onto_process_flow` / `yak_onto_process_step`，只读知识展示）和变更单（`yak_onto_change_order`）。

**读代码入口**：
- 控制器：`ontology/controller/v1/OntologyObjectController.java`（对象+属性）、`OntologyMetricController.java`、`OntologyRelationController.java`（REST 前缀 `/api/v1/ontology/*`）
- 业务承担者（Manager 而非 Service）：
  - `entity/OntologyEntityManager.java` — 对象/属性生命周期
  - `metric/OntologyMetricManager.java` — 指标生命周期
  - `relation/OntologyRelationManager.java` — 关系
  - `glossary/OntologyGlossaryManager.java` / `function/OntologyFunctionManager.java` / `action/OntologyActionManager.java` / `flow/OntologyProcessFlowManager.java`
  - `change/OntologyChangeOrderManager.java` — 变更单
- 每个实体都有 `POST /{id}/publish` 与 `POST /{id}/deprecate`。

### 1.2 核心机制①：能力物化 `OntologyCapabilityDeriver`（属性级"算子法律"）

文件：`ontology/capability/OntologyCapabilityDeriver.java`

属性保存时自动按 数据类型+is_time 推导 `allowed_operators` 白名单并回填（纯函数，无 IO）：

| 数据类型 / 角色 | 推导出的算子白名单 |
| --- | --- |
| number | eq/neq/gt/gte/lt/lte/in/not_in/between |
| string | eq/neq/in/not_in/like |
| date/timestamp 或 is_time=1 | eq/between + 指标算子 yoy/mom |
| boolean | 仅 eq |
| json | 暂不开放 |

**业务案例**：`amount`(金额) 是 number → 白名单含 `gt/gte/lt/…`；`order_status`(订单状态) 是 string(枚举) → 只含 `eq/in/not_in`。所以"金额>1000"合法，"订单状态>PAID"会被守卫拒绝——不是写在某个 Java 判断里，而是**建模时物化进列、查询时对照列**。这是"语义承诺由物理能力反向派生"（A2 公理）。

> 记忆点：`allowed_operators` 是**属性级法律**，后面语义守卫（`SemanticQueryGuard`）和 dataset 通道都会做双保险检查它。

### 1.3 核心机制②：发布即承诺 `OntologyEntityManager.publish`

文件：`ontology/entity/OntologyEntityManager.java`（第 117 行起）

发布是事务内的硬校验闸门，全部拒绝都带可操作原因：

- 仅 DRAFT 可发布；
- 必须有至少一个属性、至少一个主键属性（`is_pk=1`）；
- PROCESS 概念必须含 `is_time=1` 时间维 + `role=MEASURE` 事实属性；
- 每个属性要么**锚定** `dataset_field_id`、要么物理四元组 `ds_id/db_name/tbl_name/col_name` 齐备（A2 映射完整性）；
- `identify_by` 引用的必须是已登记属性；
- 通过后置 PUBLISHED 并记 `PUBLISH` 审计（`yak_onto_change_log`）。

**业务案例**：`sales_order` 发布时校验——有主键 `order_no`、有时间维 `order_date`、有事实属性 `amount`(MEASURE)、每个属性都锚定了数据集字段——通过才可上线查询。

**指标按类型分闸发布**（`OntologyMetricManager.publish`）：
- ATOMIC：统计主体必须 PROCESS、`base_attribute` 属于主体且 role=MEASURE；
- DERIVED：`derivationJson={atomicCode,timeCycle?}`，引用的原子指标必须已发布且同主体（口径链 派生→原子→字段）；
- COMPOSITE：`derivationJson={refs,formula}`，禁自引用、防循环 DAG——**当前仅登记，执行端拒绝**（`COMPOSITE_NOT_EXECUTABLE`）。

### 1.4 核心机制③：锚定（本体 → 数据集）

- 对象锚定：`yak_onto_object.dataset_id` 指向数据集；
- 属性锚定：`yak_onto_attribute.dataset_field_id` 指向该数据集字段；
- 绑定在 DRAFT 期（`bindAnchor`），发布后切换受控；
- 锚定有状态 `anchor_status`(OK/BROKEN)，数据集版本发布后由监听器重算（`mapping/AnchorInvalidationListener` 的 AFTER_COMMIT），锚坏了要重新处理（锚定一致性网关 SEC-4）。

> 锚定是"**语义层从此不再关心物理库表**"的关键——对象/属性一旦锚定，查询面只认 datasetId+fieldId。

### 1.5 核心机制④：已发布不可静默改（GOV-6 变更单）

已发布对象的属性集修改走 `POST /objects/{id}/change-orders` 变更单：登记 diff（增删改的属性）+ 影响面（引用的已发布指标/流程）→ apply 原子生效 + 版本递增（`bumpVersionsViaChangeOrder`）。拒绝"直接改已发布对象"。

---

## 第 2 步：数据集与锚定（dataset）—— 数据执行的"闸门"

### 2.1 数据模型：元数据 + 不可变版本快照（3+1 张表）

| 表 | 内容 | 关键点 |
| --- | --- | --- |
| `yak_dataset` | 数据集身份 | 状态 ONLINE/OFFLINE；`current_version_id` 指针 |
| `yak_dataset_version` | **不可变版本快照** | 只追加；version_no 单调；`sql_content` LONGTEXT（被冻结的源 SQL）；`schema_snapshot` JSON；source_type(SQL_QUERY/QUERY_REVISION/TABLE/VIEW) |
| `yak_dataset_field` | 每版本字段 schema | (version_id, field_id) 复合主键；physical_name/dataType/default_role |
| `yak_dataset_query_performance` | 每次执行耗时诊断 | SQL 脱敏+哈希，失败也落 |

**数据真相**：dataset **不存业务数据行**——数据永远在外部数据源；它冻结的是"这条 SQL 长什么样"（可执行的源契约），版本只追加、`current_version_id` 是唯一可变指针。

读代码入口：
- 稳定 Facade：`DatasetService`（生命周期）、`DatasetQueryService`（查询）、`DevelopmentDatasetFacade`（数据集节点开发）
- `definition/DatasetManager`、`publication/DatasetPublisher`（发布/版本追加）

### 2.2 生命周期：ONLINE 才可查

- 数据集状态只有 ONLINE/OFFLINE（无"草稿"态；"发布版本"=追加不可变版本 + 移动指针）。
- 查询入口硬校验：OFFLINE 直接拒绝「只有 ONLINE Dataset 可以查询」（agent 网关标记 `[DATASET_OFFLINE]` 并落 REJECTED 留痕）。

### 2.3 查询运行时五段管线（核心中的核心）

```text
DatasetQueryService.query
  → DatasetQueryGovernor    # ① 护栏：短TTL缓存(Caffeine)/并发信号量([QUERY_CONCURRENCY_LIMIT])/审计
  → DatasetQueryCoordinator  # ② 协调：ONLINE校验 → 版本解析(当前指针或显式versionNo)
                              #     → 字段解析 → JOIN目标解析(同数据源约束) → 适配器选择
  → DatasetQueryCompiler     # ③ 编译：只读强制(DatasetSqlSafety) → 投影/GROUP BY(聚合/时间桶)
                              #     → WHERE(算子+字面量转义) → ORDER → LIMIT clamp(≤1000)
  → DatasetSourceQueryAdapter # ④ 适配：SQL_QUERY(默认30s/上限120s) | QUERY_REVISION(修订快照)
  → SqlExecutionRuntime      # ⑤ 执行：平台执行 SPI，只读策略统一兜底
```

文件：`dataset/DatasetQueryService.java`、`query/DatasetQueryGovernor.java`、`query/DatasetQueryCoordinator.java`、`query/DatasetQueryCompiler.java`。

**业务案例（一次普通明细查询）**：前端调用 `POST /api/v1/datasets/{datasetId}/query`（dimensions/metrics/filters/sorts/limit）→ Governor 先查 3 秒缓存（同请求直接回）→ 未命中就过信号量 → Coordinator 确认 ONLINE、锁定当前版本 → Compiler 把字段/过滤/聚合编译成 `SELECT … FROM (<源SQL>) yak_dataset_source WHERE … GROUP BY … LIMIT …` → SqlExecutionRuntime 执行。

### 2.4 安全底线（读 Compiler 时注意）

- **只读强制**：`DatasetSqlSafety.requireReadOnlyQuery`——单语句、必须 SELECT/WITH 开头、禁 INSERT/UPDATE/DELETE/…/FOR UPDATE，超 500KB 拒绝；
- **标识符白名单**：所有物理字段名必须匹配 `[A-Za-z_][A-Za-z0-9_$]*`（防注入）；
- **值全字面量转义**：字符串 `'`/`\` 转义，数值 BigDecimal 规范化，IN 上限 100 值、单值 ≤4000 字符、过滤条件 ≤50；
- **LIMIT 收敛**：默认 200、上限 1000，`limit+1` fetch 探测截断。
- 每次尝试**恰落一条** `yak_dataset_query_performance`（成功/失败都落，失败记 failureStage/errorType）。

### 2.5 JOIN 与对比（T3/T4 迁移后由 dataset 编译器承接）

- **JOIN**（`DatasetJoin`）：跨对象维度单跳 DIRECT → `LEFT JOIN (<目标源SQL>) j1 ON 基准字段 = j1.目标字段`，仅同数据源（`[UNSUPPORTED_CROSS_DATASOURCE]`）；
- **对比指标**（`DatasetComparison`）：当期/上期用 `CASE WHEN time >= start AND time < end THEN measure END` 期掩码聚合 + `(cur-prev)/NULLIF(prev,0)` 同比列——**不进 WHERE**，上期边界由共享 `PeriodShift` 计算；
- 时间桶 `DatasetTimeBucket(field, grain)`：桶字段须 DATE/DATETIME 且不能同时作普通维度，表达式经 core 的 `TimeGrainBinder`。

---

## 第 3 步：语义层（semantic）—— 把业务提问变成受控查询

### 3.1 结构化 DSL（对外契约，`api/` 包零依赖）

```text
SemanticQuery(
  objectCode,                       # 对象逻辑码，如 sales_order
  dimensions[],                     # 维度：属性逻辑名，如 ["region"]
  metrics[{code, timeGrain?, comparePeriod?}],   # 指标编码；grain=DAY/WEEK/MONTH/QUARTER/YEAR
  filters[{logicalName, op, value}],# 过滤（逻辑名+白名单算子）
  timeRange{field?, start?, end?},  # 左闭右开
  limit?)
```

- 字段引用一律**逻辑名**，物理列名只在编译管线内部出现；
- 它是 **MCP/Agent/调试台的公共契约**（api 包零依赖）；
- `ComparePeriod`（YOY/MOM）以 `comparePeriod` 出现，编译器生成当期/上期/同比三列，**不生成窗口函数**。

文件：`semantic/api/SemanticQuery.java` 及各 Definition/View 记录。

### 3.2 五项守卫 `SemanticQueryGuard`（先回答"合法吗"）

文件：`semantic/guard/SemanticQueryGuard.java`

1. 权限（`semantic:query`）；
2. 对象/属性/指标**存在且 PUBLISHED**（未发布 → `OBJECT_NOT_PUBLISHED`）；
3. 维度角色必须 DIMENSION（`INVALID_DIMENSION_ROLE`）；
4. 过滤算子 ∈ 属性 `allowedOperators` 白名单（`OPERATOR_NOT_ALLOWED`，yoy/mom 是指标算子不可作过滤）；
5. requires 约束求值：`ATTR_VALUE_DOMAIN`（数值域，如 数量必须 > 0）/ `METRIC_APPLIES_TO`（指标适用范围，如查销售额必须附 `order_status=PAID` 过滤）。

失败形态：`validate()` 返回结构化的 `GuardReport{passed:false, violations[], suggestions[]}`（200 返回，业务语义而非 HTTP 错误）；`execute()` 入口失败则抛 `[GUARD_REJECTED]`。

> 记忆点：**Guard 只回答"合法吗"，不拼 SQL；Compiler 只回答"长什么样"，不做权限决策**——职责严格分离。

### 3.3 单一执行面：数据锚定通道 `DatasetAnchoredQueryGateway`（T5 后的唯一通道）

文件：`semantic/gateway/dataset/DatasetAnchoredQueryGateway.java`

这是全系统**最值得精读的一层**——语义 DSL → dataset 请求的结构翻译：

```text
SemanticQuery ──translate──▶ DatasetQueryRequest(datasetId, dimensions[], metrics[],
                           filters[], limit, timeBucket?, joins[], comparisons[])
```

翻译管线（dry-run 与 execute 共用同一管线，产物一致）：

1. **维度**：逻辑名 → `dataset_field_id`；本对象没有的维度 → 跨对象解析：唯一定位宿主对象（该逻辑名 role=DIMENSION）→ 必须存在 base→host 的 DIRECT 单跳关系 → 生成 `DatasetJoin`；多宿主报 `[AMBIGUOUS_DIMENSION]`，多跳报 `[DATASET_CHANNEL_UNSUPPORTED]`（T3b 决策：不做，拒绝优于错数）；
2. **指标**：`metric_code` → 定义 → COMPOSITE 拒绝（`COMPOSITE_NOT_EXECUTABLE`）→ DERIVED 经共享 `MetricDerivationExpander` 机械展开为原子底座 → 生效粒度 `ref.timeGrain() ?? def.timeGrain()`（ref 优先，桶/口径对齐）→ 指标自带业务限定 `businessFilterJson`（口径过滤）翻译为 `DatasetFilter` AND 拼入（**口径恒拼接**）；
3. **过滤**：逻辑名 → 锚定字段，**双保险白名单**，时间范围翻译为 `GTE start AND LT end`（左闭右开）；
4. **时间桶**：粒度合法校验 → 对象唯一时间属性（无 → `[NO_TIME_ATTRIBUTE]`，多个 → `[AMBIGUOUS_TIME_FIELD]`）→ 自动补 `order_date` 维度列 + `DatasetTimeBucket`；
5. **对比**：YOY/MOM → `DatasetComparison`（唯一性：单查询仅一个对比指标 `[COMPARE_METRIC_MIXED]`；必须完整窗口 `[COMPARE_PERIOD_REQUIRED]`）；
6. **limit**：`1 ≤ limit ≤ maxLimit` 收敛，缺省用 defaultLimit。

**业务案例（回扣主线问题）**：

> 「上个月华东区域销售额，同比呢？」→
> `SemanticQuery(sales_order, ["region"], [order_gmv{timeGrain=MONTH, comparePeriod=YOY}], [], timeRange=上月, limit)`
> 翻译产物：维度 `["region"]`、指标 `[amount SUM]` + `DatasetComparison(amount, SUM, YOY, order_date, 上月初, 本月初)`、无 WHERE 窗口（进期掩码）、`MetricCaliber(order_gmv, v1, MONTH, 元)`。

### 3.4 返回契约：结果必带口径

`QueryResult{columns, rows, caliber, compiledSql, elapsedMs, truncated}`：
- 列投影：dataset bindings 按 fieldId 映射回**语义逻辑名 alias**（`sales_amount`/`sales_amount_prev`/`sales_amount_yoy`）；
- `caliber`（口径：指标编码/版本/时间粒度/业务过滤摘要）**必须附尾**——"口径透明"是缺省即缺陷的合同项；
- `compiledSql` 携带 `[dataset-anchored:…]` 执行标记。

### 3.5 本体定义如何进入语义层（读代码注意的边界）

`semantic/gateway/ontology/OntologyDefinitionAdapter.java` 是**唯一允许 import ontology 的文件**：`snapshotFor(objectCode)` 在编译期冻结定义快照 `DefinitionSnapshot`（只含 PUBLISHED；DIRECT 关系；ATOMIC 附聚合底座、DERIVED 投影原子口径链、COMPOSITE 仅登记）——**编译期不回读本体，快照冻结**。

---

## 第 4 步：智能体会话（agent）—— 让 LLM 只会"说话"，不碰 SQL

### 4.1 稳定入口（REST 契约，`controller/v1/AgentController.java`）

Controller 只依赖 4 个稳定 Facade：`AgentChatService` / `AgentSessionQueryService` / `AgentConfigManageService` / `AgentReportService`。

| 端点 | 说明 |
| --- | --- |
| `POST /chat/turns` | 提交/恢复合一 `{sessionId, message, toolResults?}` → 返回 turnId 立即应答 |
| `GET /chat/turns/{turnId}/events` | SSE 事件流，`Last-Event-ID` / `cursor` 续播 |
| `GET /sessions` / `GET /sessions/{id}/history` / `GET /sessions/{id}/observability` | 会话列表/历史/trace v2/观测矩阵 |
| `GET /turns/{turnId}/trace` | trace v2 span 树 |
| `DELETE /sessions/{id}`、`PUT /sessions/{id}`、`POST /sessions/{id}/cancel` | 会话生命周期 |
| `GET /config`、`PUT /config/{key}` | 动态配置热生效 |
| `POST /queries/page`、`POST /reports/page`… | 查询审计 / 报告管理 |

### 4.2 核心机制①：提交/执行分离（HTTP 线程绝不推理）

文件：`conversation/AgentChatService.java` → `submitTurn`：

```text
归属校验(首访自动绑定会话+标题) → 会话级 stripe 串行化
→ hasActiveTurn 单飞检查(QUEUED/RUNNING/WAITING_INPUT 任一存在即拒绝)
→ 输入投影落 yak_agent_turn(QUEUED) → turnDispatcher.kick() 即时唤醒 → 返回 turnId
```

### 4.3 核心机制②：轮次状态机（CAS 单赢家）

文件：`conversation/AgentTurnDispatcher.java`（调度）、`AgentTurnExecutor.java`（执行）

```text
QUEUED ──claim──▶ RUNNING ──complete/fail/cancel──▶ COMPLETED/FAILED/CANCELLED(终态)
   │                │──HITL──▶ WAITING_INPUT ──requeue──▶ QUEUED(续跑同一轮)
   └──cancel──▶ CANCELLED     └──启动清孤儿: RUNNING→INTERRUPTED(诚实终态)
```

- 调度：`@Scheduled(2s)` 扫 QUEUED 批量出队 + `kick()` 即时唤醒；启动时先清孤儿 RUNNING→INTERRUPTED；
- 执行：CAS 认领（幂等，同一轮只认领一次）→ 解码输入 → 订阅 `AgentRuntime` 事件流 → **事件先落 `yak_agent_turn_event` 再投递** → 逐帧持久化 → 终态收敛（未闭合工具调用补 ABORTED 帧，防"停止后卡片永久转圈"）。

### 4.4 核心机制③：ReAct 运行时（洋葱中间件）

文件：`runtime/AgentRuntime.java`（懒组装单例）+ 各 Middleware

框架是 **AgentScope 2.0.2**（非 Spring AI/langchain4j），模型 OpenAI 协议（stream=true）。中间件链（注册顺序即执行顺序）：

```text
LlmResilienceMiddleware     # 单次硬超时 + 重试分类(超时不重试/401·403·429不重试/5xx重试)
                            # + 调用级记账 KIND_LLM_CALL
→ ToolAuditMiddleware       # 工具零侵入落 step（KIND_TOOL_CALL，含 GUARD 拒绝检测）
→ SystemPromptAssemblyMiddleware  # 能力域提示词贡献者按序追加（失败静默降级）
→ LongTermMemoryPromptMiddleware  # 长期记忆召回注入（"## 长期记忆"段）
→ CompactionMiddleware      # 跨轮上下文压缩（防超窗静默失败，缺省开）
```

事件防腐 `AgentEventCodec`：AgentScope 事件流 → 10 种领域 `ChatTurnEvent`；未知事件显式降级忽略。

### 4.5 核心机制④：12 个业务工具（薄壳，零直连数据面）

| 工具 | 能力域 | 条件 |
| --- | --- | --- |
| `run_semantic_query` | 聚合/口径（指标、跨对象） | `yak.semantic.enabled` |
| `search_concepts` | 概念检索、消除术语歧义 | 同上 |
| `get_object_schema` | 概念卡（对象+属性+算子+指标口径） | 同上 |
| `run_function` | 过程计算（FUNC-1 只读模板） | 同上 |
| `get_object_instance` | 对象实例态取数（主键快照+单跳关系） | 同上 |
| `run_dataset_query` | 分组聚合/过滤/排序（结构化规格） | 常驻 |
| `list_datasets` / `get_dataset_fields` | 目录/字段发现 | 常驻 |
| `current_date_info` | 相对时间（"上个月"）基准 | 常驻 |
| `request_clarification` | HITL：问题不明确时打断向人提问 | 常驻 |
| `analyze_with_python` | 本地进程计算（信号量/超时强杀） | `yak.agent.python.enabled=true` |
| `save_analysis_report` | 结论落 `yak_agent_report` | 常驻 |

**能力域路由**（`CapabilityRoutingPromptContributor` 把决策树写进系统提示）：口径二义 → `search_concepts`/澄清；聚合指标 → `run_semantic_query`；明细 → `run_dataset_query`；实例 → `get_object_instance`；过程计算 → `run_function`；相对时间 → `current_date_info`。

### 4.6 核心机制⑤：工具 → 网关 → 数据面（唯一走廊 + 同边界留痕）

```text
run_semantic_query ─▶ gateway/SemanticGateway(DataSourceSemanticGatewayAdapter)
                       └─▶ semantic 的 SchemaSearchReader / SemanticQueryManager / SemanticFunctionExecutor
                       └─▶ 每次执行恰落一条 query_log（含 semantic_ref）
run_dataset_query  ─▶ gateway/DatasetQueryGateway
                       └─▶ FieldWhitelistValidator（字段白名单前置，拒绝带 [FIELD_WHITELIST_REJECTED]）
                       └─▶ query_log 同边界落账
```

**模型永不接触 SQL**：工具只返回结构化结果文本；工具失败/拒绝以精确错误文本回喂模型**同一轮自纠**，不升级为整流终止。

### 4.7 核心机制⑥：HITL 澄清闭环（防伪）

模型发现提问歧义 → `request_clarification`（框架外置工具挂起，事件 CLARIFY_REQUESTED，轮转 WAITING_INPUT）→ 前端展示澄清选项卡 → 用户作答走 `POST /chat/turns`（toolResults）→ `submitResume`：**校验 feedback.toolCallId 必须匹配反问帧**（防伪造/并发第二反问）→ CAS 回 QUEUED → 以原轮 frozen assistant 节点构造 RESUME 载荷续跑（用户答句不丢失）。

### 4.8 SSE 事件流（10 种帧 + 游标续播）

`TURN_STARTED / THINKING_DELTA / TEXT_DELTA / TOOL_CALL / TOOL_RESULT / CLARIFY_REQUESTED / TURN_FINISHED / TURN_CANCELLED / EXCEEDED_MAX_ITERS / ERROR`

- 帧格式 `event:{type}` + `id:{eventId}` + `data:{ChatTurnEvent}`；`AgentEventStreamTailer` 按游标增量补发（断线用 `Last-Event-ID` 续播）；
- WAITING_INPUT 轮从 0 重放（保证澄清帧对晚订阅者可见），已完成轮最新帧起步不重放；
- 心跳 `:ping` 3s；首帧 TURN_STARTED、终态兜底 TURN_FINISHED；未闭合工具调用终态前强制闭合（`TOOL_RESULT … toolStatus=ABORTED`）。

---

## 第 5 步：结果呈现与证据链（一次提问的可追溯性）

### 5.1 四层证据

| 证据层 | 载体 | 内容 |
| --- | --- | --- |
| 执行证据 | `yak_agent_query_log` | sessionId、requestJson、semantic_ref、status(SUCCESS/FAILED/REJECTED)、rows、elapsed |
| 性能留痕 | `yak_dataset_query_performance` | 每尝试恰一条：阶段耗时、SQL(脱敏)、sourceType、queryId |
| 步骤观测 | `yak_agent_step`（telemetry/AgentStepRecorder 唯一写入口） | 9 种 kind：LLM_CALL/TOOL_CALL/GUARD/HITL/COMPACTION/TURN_SUMMARY/MEMORY_FLUSH/MEMORY_RECALL(+/MEMORY_CONSOLIDATE 预留) |
| 轮次事实 | `yak_agent_turn` + `yak_agent_turn_event` | 状态机 CAS + 事件投递日志（可重建投影） |

### 5.2 前端呈现五件套

- 思考卡（THINKING_DELTA，服务端权威计时）；正文打字机（TEXT_DELTA）；
- 工具卡（TOOL_CALL/TOOL_RESULT，按 toolCallId 原位闭合）；
- **口径卡**：`run_semantic_query` 结果尾随「本次口径：…」（正则提取展示）——回答与口径同屏可查；
- 澄清选项卡（HITL）；回合统计条（N 步/用时/tokens，`!streaming` 时可「重新生成」）；
- trace v2（`GET /turns/{turnId}/trace`）：step 表 → 完整 span 树（LLM_CALL/TOOL_CALL/GUARD/HITL/TURN_SUMMARY，含 errorCode/尝试/耗时）。

### 5.3 记忆（M1 已接线）

自然完成的 START 轮 → `MemoryFlushService.submitAfterTurn` 异步提取（PREFERENCE/FACT/GLOSSARY/LESSON 四类，LEDGER 层）→ 落 `yak_agent_memory`；下轮想起（召回评分：类型权重×置信度×时间衰减 → topK → 注入系统提示）。**M2 巩固管线（合并/固化）repository 已扩展但零调用者——规划中 WIP**。

---

# 回到主线：一次完整提问的端到端时序

```text
用户:「上个月华东区域销售额是多少，同比呢？」
 1  POST /api/v1/agent/chat/turns → turnId（HTTP 线程不推理）
 2  Dispatcher 出队 → Executor CAS RUNNING
 3  AgentRuntime 组装上下文（会话历史 + 记忆召回 + 能力域提示词）
 4  LLM 流式输出：THINKING → TOOL_CALL(run_semantic_query)
 5  工具参数 = SemanticQuery(sales_order, [region], [order_gmv{timeGrain=MONTH,comparePeriod=YOY}],
                             [], 上月窗口, limit)
 6  SemanticQueryManager：快照 → Guard 五项 → 锚定检查
      └─ DatasetAnchoredQueryGateway：结构翻译为 DatasetQueryRequest
          └─ dataset 运行时：Governor → Coordinator → Compiler → 执行
          （维度 region、指标 amount SUM + YOY 三列、口径过滤 order_status=PAID、grain=MONTH）
 7  QueryResult + caliber → 工具回喂 LLM → LLM 组织自然语言回答
 8  证据落库：query_log / step / performance（同边界）
 9  TURN_FINISHED → SSE → 前端渲染：思考卡 → 工具卡(口径) → 正文 → 回合统计条
10  可「重新生成」，或让模型 save_analysis_report 落报告
```

---

# 附 1：当前"代码失控感"的几个高风险点（自查清单）

| 现象 | 根因（代码层面） | 建议 |
| --- | --- | --- |
| 双路线文档残留 | `compile/` 包已随 T5 删除，唯一执行面=dataset 锚定通道 | ✅ 已处理：`data-agent-business-flow.md` / `data-agent-technical-architecture.md` 已按 `semantic-unify-plan.md` 同步为 T5 单执行面（2026-09-03） |
| 对比指标口径缺 comparePeriod | demo 测试留观察项（D-2）：`MetricCaliber` 未携带 comparePeriod | 已登记缺陷，改代码时核对 `DatasetAnchoredQueryGateway` 对比分支与 `Compiler` 对比列 |
| 消息树"有读接口无写点" | `yak_agent_message` 的 append 由执行器 complete 回填，无独立创建；前端未消费 | 观察项，读 `AgentTurnExecutor.completeAssistant` |
| 行级权限是空拼接点 | `RowPolicyProvider` 为 Noop；dataset 通道对非空谓词显式拒绝 `[ROW_POLICY_UNSUPPORTED]` | S4 预留，三期填实现 |
| COMPOSITE 指标不可执行 | Guard 与通道均 `COMPOSITE_NOT_EXECUTABLE`（仅登记、schema 可见） | 属有意收紧，四期做公式执行 |
| 字符串级错误码魔法串散落 | `[GUARD_REJECTED]` 等以 contains 判定散布各层 | review P2：收敛为常量 |
| M2 记忆巩固零调用者 | `KIND_MEMORY_CONSOLIDATE` 无生产者 | 登记 WIP，勿当已实现 |

# 附 2：推荐的下一步阅读路线（按依赖顺序）

1. `ontology`：`OntologyEntityManager.publish` → `OntologyCapabilityDeriver` → `OntologyMetricManager.publish`
2. `semantic`：`api/SemanticQuery.java` → `guard/SemanticQueryGuard.java` → `gateway/ontology/OntologyDefinitionAdapter.snapshotFor` → `gateway/dataset/DatasetAnchoredQueryGateway.translate`（**最值得精读**）
3. `dataset`：`DatasetQueryService.query` → `DatasetQueryCompiler.compile` → `DatasetQueryCoordinator.query`
4. `agent`：`conversation/AgentChatService.submitTurn` → `conversation/AgentTurnExecutor.execute` → `runtime/AgentRuntime`（中间件链）→ `toolset/RunSemanticQueryTool` → `gateway/DataSourceSemanticGatewayAdapter`

配套测试即活文档：
- 语义翻译/守卫：`semantic/.../e2e/E2eSemanticQueryFlowTest.java`（11 个业务场景）
- 业务演示：`agent/.../demo/DemoBusinessScenarioTest.java`（销售分析/设备故障归因/排产计划/质量追溯 × 3 级复杂度）
- 会话流：`agent/.../conversation/E2eAgentChatFlowTest.java`（提交/单飞/HITL 防伪/取消/越权/SSE 游标）