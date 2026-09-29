# 语义单线化统一计划（Semantic Unify Plan）

> 定位：把「同一个 SemanticQuery 由两条执行实现（A 编译器 / B dataset 通道）提供」统一为**单一语义实现**——dataset 运行时成为唯一 SQL 生产面，semantic 收敛为「守卫 + 结构翻译」壳。消除双实现造成的语义歧义（口径/能力不一致），并最终减少代码量。
> 触发背景：`data-agent-business-flow.md` / `data-agent-technical-architecture.md` 梳理后确认 A/B 双路线残留 4 处歧义（对比算子静默丢失、指标 businessFilter 缺失→错数风险、DERIVED 不展开、生效粒度不同源）。
> 原则：**统一过程中绝不允许静默错数**——能力未承接时显式拒绝（`[DATASET_CHANNEL_UNSUPPORTED]`），不静默给出不同口径。

## 0. 终态架构（目标）

```text
SemanticQuery（api DSL，不变）
  → Guard（不变：存在性/白名单/requires/权限）
  → DatasetAnchoredQueryGateway（唯一执行通道：结构翻译为 DatasetQueryRequest）
      └─→ dataset 运行时（唯一 SQL 生产面）
            · DatasetQueryCompiler：承接 JOIN / DERIVED 展开 / businessFilter / 对比(MET-3) / 生效粒度
  → QueryResult + caliber（不变）
compile/ 包删除：SemanticSqlCompiler / JoinPathResolver / IdentifierGuard（白名单迁入 dataset）/ RowPolicyProvider 拼接点
DataSourceSemanticExecutionAdapter：A 路线的执行信道（run_function 另找信道或并入 dataset 只读通道）
```

迁移完成条件（验收）：

- 所有业务对象锚定数据集（无「仅物理四元组」对象）或 dataset 通道提供等价能力；
- dataset 编译器具备 A 的全部能力，语义层无第二套 SQL 生产逻辑；
- 同一声明式查询的 golden 快照与 caliber 在迁移前后逐字一致（迁移用 A 的 golden 作为 dataset 侧验收基准）；
- `compile/` 包、快照测试（~1350 行）可一次性删除；边界白名单收紧（semantic 不再允许 compile→api 之外依赖）。

## 1. 迁移阶段

| 阶段 | 内容 | 完成后语义一致性 | 删除/新增 |
| --- | --- | --- | --- |
| **T1 安全垫**（本次实施） | B 路线对齐：指标 businessFilter 拼入、生效粒度(effectiveGrain) 桶+caliber 对齐；**能力缺口显式拒绝**：comparePeriod、DERIVED 指标 → `[DATASET_CHANNEL_UNSUPPORTED]` | 消除错数风险与静默丢失；未承接能力不再静默给出不同口径 | B 通道内部对齐；错误码+文档登记 |
| **T2 dataset 编译器拓展①** | DERIVED 展开迁入 dataset 编译器（derivationJson→原子底座+时间周期+业务限定）；B 翻译不再拒绝 DERIVED | 锚定对象 DERIVED 口径与 A 展开一致 | dataset 编译器新增；A 的 expandDerived 逻辑冻结 |
| **T3 dataset 编译器拓展②** | JOIN 能力迁入（DIRECT 关系 BFS → dataset 多表编译）；语义层维度/指标可跨对象 | 跨对象查询在 dataset 侧达成 | dataset 编译器新增多表；A 的 JoinPathResolver 冻结 |
| **T3b（决策：不做）** | 多跳 JOIN / 跨对象过滤 **不再承接**——规划以「dataset 提前 JOIN 宽表」替代（对象属性直接来自宽表，语义层无需多跳 JOIN） | 显式拒绝保持（拒绝优于错数）；宽表化提供等价跨对象能力 | 无新代码 |
| **T4 dataset 编译器拓展③** ✅ 已交付 | 对比算子（MET-3：当期/上期 CASE 掩码 + `_yoy`）与生效粒度迁入；`DatasetComparison` + 共享 `PeriodShift` | comparePeriod 在 dataset 侧达成三列口径（多对比 `[COMPARE_METRIC_MIXED]`、缺窗口 `[COMPARE_PERIOD_REQUIRED]`） | dataset 编译器新增；A 的 MET-3 逻辑冻结（shiftBound 委托共享件） |
| **T5 收敛清理** ✅ 已交付 | 删除 A 编译器（SemanticSqlCompiler/JoinPathResolver/IdentifierGuard）+ 快照测试；SemanticQueryManager 单通道（未锚定 [NOT_ANCHORED] 显式拒绝）；compile 包仅保留共享契约件（MetricDerivationExpander/RowPolicyProvider）；边界/契约文档同步 | 单一语义实现；歧义归零 | 净减 ~1250 行（编译器三件 871 + 快照测试 385）+ 依赖简化 |

每个阶段的验收原则：

- **口径等价（非 SQL 文本逐字）**：A 路线在物理表层（对象→db/tbl/col）、dataset 侧在数据集快照层（version.sql FROM），基线不同故 SQL 文本不可能逐字一致；验收基准改为**列集合、分组语义、聚合底座、过滤条件与 caliber 与 A 一致**——dataset 侧编译测试以 A 的列级断言（SemanticSqlCompilerSnapshotTest 的 columns/caliber 期望）为基准逐列对齐；
- **caliber 等价**：timeGrain/版本/业务过滤摘要两实现一致；
- **拒绝优于错数**：未完成承接的能力保持 `[DATASET_CHANNEL_UNSUPPORTED]` 显式拒绝，不静默缩水；
- 每次删除 A 的一块前，先让 B 的对应能力上线并过等价验收，再删，保证任何时刻语义至少有一种可用实现。

## 2. 本次实施（T1 安全垫）范围

文件：`semantic/gateway/dataset/DatasetAnchoredQueryGateway.java`

1. **指标 businessFilter 拼入**：`def.businessFilterJson`（`[{attr,op,value}]`，建模侧已结构校验）翻译为 `DatasetFilter` 加入请求 filters（AND 组合，WHERE 语义与 A 的 `appendBusinessFilters` 一致）——消除「锚定对象查口径指标多算」的错数风险；
2. **生效粒度对齐**：桶收集与 caliber 的 timeGrain 采用 `ref.timeGrain() ?: def.timeGrain()`（effectiveGrain，对齐 A）；DERIVED 阶段仍拒绝，故本阶段的兜底只包含 ATOMIC 指标定义粒度；
3. **能力缺口显式拒绝**：
   - `ref.comparePeriod() != null` → `[DATASET_CHANNEL_UNSUPPORTED]`（sdk：对比算子待 T4 承接；当前可改用未锚定对象走 A 或拆两次查询组合）；
   - `metricType == DERIVED` → `[DATASET_CHANNEL_UNSUPPORTED]`（派生展开待 T2 承接）。

新增错误码 `[DATASET_CHANNEL_UNSUPPORTED]` 登记进 REQUIREMENTS（B 路线节）与错误码文档。

## 3. 风险与回退

- T1 会**收紧**锚定对象可查询范围（带对比/DERIVED 的查询被拒绝）——这是有意的安全收缩，非功能回退（原行为是静默错数或静默丢能力）；业务方可先摘除锚定走 A，或等待 T2/T4；
- 拒绝消息含明确 solution，前端/调试台可按 `[CODE]` 前缀区分；
- 每阶段独立提交、独立验收，可单独回退，不影响未迁移对象（未锚定对象在 T2~T4 期间仍走 A）。

## 4. 关联

- 业务流：`data-agent-business-flow.md` §③；架构：`data-agent-technical-architecture.md` §2/§8；
- 契约：`data-ops-business-yak-ops-business-semantic/REQUIREMENTS.md`（B 路线节 FR-3.8）、`DEPENDENCIES.md`（Dataset corridor）；
- 代码：`gateway/dataset/DatasetAnchoredQueryGateway.java`、`query/SemanticQueryManager.java`（分流不变）、`compile/`（T2~T5 冻结/删除）。