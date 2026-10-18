# R13-C3.3｜ADS → Quality 只读证据关联契约提案

> 状态：**源码级设计提案，待 Product / Modeling / Quality Owner 评审；本 PR 不修改生产代码，不新增监控关联写入，不运行质量任务。**  
> 基线：2026-10-18  
> 关联：C3 总 Issue [#602](https://github.com/gitfortian/data-ops/issues/602)、Quality 源码盘点 [#606](https://github.com/gitfortian/data-ops/pull/606)、ADS 证据卡 [#611](https://github.com/gitfortian/data-ops/pull/611)、DataDev/Runtime 关联契约 [#610](https://github.com/gitfortian/data-ops/pull/610)。

## 1. 决策摘要

**推荐：由 Quality 域持有显式、不可变的 ADS 合同修订关联；由 Quality 原域返回精确执行证据；Modeling 只消费最小化、只读 DTO。**

当前不能从 ADS 合同直接定位 Quality monitor 或 execution。Quality 侧确实有监控、不可变 workflow task revision 和执行记录，但 ADS 合同没有 Quality monitor/rule/execution 的稳定引用；Quality execution record 也没有持久化其执行所用的 task revision ID/checksum。因此，单靠现有 API 即使找到某个 monitor，也不足以证明其当前执行结果对应某一版 ADS 合同。

本提案只冻结关联身份、Truth Owner、状态与验收边界。任何 Quality association 的创建/变更都是单独的受治理写操作，不属于当前 C3 只读证据卡；未经 Owner 签收不得实现。

## 2. 源码级事实

### 2.1 Quality Monitor：目标对象与当前结果

源码：
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/controller/v1/QualityMonitorController.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/monitor/QualityMonitorReader.java`
- `data-ops-common/src/main/java/io/yak/ops/common/bean/vo/quality/QualityMonitorVO.java`

现有只读接口：
- `GET /api/v1/data-quality/monitor/{id}`：需要 `QualityPermissionCode.MONITOR_READ`，返回 monitor ID、dataSourceId、database/schema/table、enabled、lastResult、lastExecutionNo、lastRunTime 和当前规则。
- `GET /api/v1/data-quality/monitor/{id}/settings`：需要 MONITOR_READ，可读取运行策略配置。
- Controller 受 `@ProjectScope` 与 `@ConditionalOnQualityEnabled` 约束。

`lastExecutionNo` 是 Quality monitor 自己保存的明确指针，可以作为已核验 monitor 的候选执行引用；它不能单独证明这次执行使用了哪一版规则，也不能证明 monitor 对应当前 ADS 合同。不能只展示 `lastResult` 而不核验精确执行记录。

**禁止把 `GET /monitor/{id}/definition` 当作普通只读证据端点：** 它调用 `editableSnapshot`，该方法会锁定 monitor，事务也不是 readOnly。证据查询应避免调用可能带锁或修改运行时状态的端点。

### 2.2 Quality execution：执行事实与质量结果是两个维度

源码：
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/controller/v1/QualityExecutionController.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/controller/v1/QualityExecutionWorkspaceController.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/execution/QualityExecutionReader.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/repository/QualityExecutionReadRepositoryAdapter.java`
- `data-ops-common/src/main/java/io/yak/ops/common/bean/vo/quality/QualityExecutionWorkspaceVO.java`

可用于精确回读的接口：
- `GET /api/v1/data-quality/execution/{executionNo}`：需要 `QualityPermissionCode.EXECUTION_READ`，返回 executionNo、monitorId、数据源与目标表身份、executionStatus、checkResult、规则计数、时间和规则结果。
- `GET /api/v1/data-quality/execution/{executionNo}/status` 要求 MONITOR_RUN，不作为证据卡的常规读取接口。
- 分页查询接口虽为只读语义，但路径采用 POST；C3 优先使用已知 monitor ID / executionNo 的精确 GET 查询，不使用分页结果猜测关联。

执行详情的规则子项包含 `executedSql`、错误信息及实际值等敏感/过量数据。证据卡不得透传整个执行详情；只返回安全的执行状态、检查结果、规则计数、目标身份摘要、时间和经过净化的错误摘要。

**必须分离两个状态：**
- `executionStatus` 表示运行是否排队、运行中或进入终态；
- `checkResult` 表示质量规则结果，如 PASSED、NOT_PASSED、ERROR、NOT_RUN、RUNNING。

执行流程成功结束不等于质量检查通过。只有来源域确认执行终态，且 `checkResult = PASSED`，才可表达“质量通过”；`NOT_PASSED` / `ERROR` 不得因 executionStatus 正常终态而显示为通过。

### 2.3 Quality task revision：有不可变定义摘要，但执行没有 pin

源码：
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/domain/QualityTaskRevision.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/repository/QualityTaskRevisionRepository.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/task/QualityTaskPublisher.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/domain/execution/QualityExecutionDefinition.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/dao/model/QualityExecutionPO.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/repository/QualityWorkflowExecutionRepository.java`

已存在的能力：
- `QualityTaskRevision` 保存 revision ID、projectId、monitorId、revisionNo、definitionJson、checksum、createdAt。
- `QualityTaskPublisher` 将冻结的 `QualityExecutionDefinition` 序列化后计算 SHA-256 checksum，并发布 revision ID/no 到 Task Catalog。
- `QualityExecutionPlanFactory.freeze` 将 monitor、目标身份和规则快照冻结为不可变执行定义。

关键缺口：
- 当前 `QualityExecutionPO` 记录 projectId、executionNo、monitorId、数据源/目标身份、状态、结果计数、时间和错误，但没有 `taskRevisionId`、`taskRevisionNo` 或 `taskDefinitionSha256`。
- `QualityExecutionDefinition` 本身也没有携带 task revision 身份。
- Workflow 入口虽然接收冻结的 QualityExecutionDefinition，但执行记录不会持久化对应的 task revision ID/checksum；手动/计划任务路径同样没有显式 execution-definition digest。

因此，现有数据不能证明某条执行记录使用了哪个不可变 Quality task revision。不得用 monitorId、执行时间、任务名称或 idempotencyKey 反推 revision。

## 3. 为什么当前不能直接接入 ADS

ADS 合同目前冻结：
- ADS modelId、contract revision、contractSha256；
- 上游模型精确版本 pin；
- 输出字段、业务键、targetStructureFingerprint；
- 可选的 Metric publication pin。

但 `targetStructureFingerprint` 证明的是 ADS 目标结构快照，不是 Quality 的数据源/数据库/schema/table 身份。Quality monitor 目标由 `dataSourceId + databaseName + schemaName + tableName` 表示。不能把列结构 hash、表名、monitor 名称或当前最近一次执行当作二者关联。

当前还缺：
1. Quality-owned 的显式 ADS contract → Quality monitor 关联记录及按 ADS 合同精确查询的只读边界。
2. 关联记录的不可变审计历史、项目隔离、权限检查与幂等/并发保护。
3. 每次 Quality execution 对实际执行定义的持久化 pin（definition checksum；Workflow 触发时还应记录 taskRevision ID/no）。
4. 对应的最小化证据 provider/API，能够把 monitor 目标身份、定义 pin 与精确 executionNo 一起核验。

## 4. 推荐的关联身份（提案，非现有实现）

### 4.1 Quality-owned ADS Quality Association

建议由 Quality 域持有不可变关联记录，字段如下：

| 字段 | 来源 / Owner | 目的 |
|---|---|---|
| `associationId` | Quality | 稳定关联 ID |
| `projectId` | 服务端当前项目上下文 | 强制项目隔离，不接受客户端指定项目 |
| `adsModelId` | Modeling | 定位 ADS 模型 |
| `adsContractRevision` | Modeling 当前合同 | 固定合同修订 |
| `adsContractSha256` | Modeling 当前合同 | 防止同 ID 下合同漂移 |
| `targetStructureFingerprint` | Modeling 当前合同 | 固定 ADS 结构快照；不得替代 Quality 物理目标身份 |
| `qualityMonitorId` | Quality | 指向明确选定的监控 |
| `qualityTaskRevisionId` / `qualityTaskRevisionNo` | Quality | 固定被审核的不可变任务定义版本 |
| `qualityTaskDefinitionSha256` | Quality | 固定任务定义内容身份 |
| `targetDataSourceId`、`databaseName`、`schemaName`、`tableName` | Quality monitor | 固定被检查的物理目标 |
| `associationDigest` | Quality | 规范化关联身份摘要 |
| `createdBy`、`createdAt`、`state` | Quality 审计 | 追溯关联与其状态 |

任何合同修订、监控目标或任务定义变化都不能静默改写历史关联；应创建新的关联修订或明确失效旧关联。关联存在只证明人工审核的关联关系已记录，不证明检查已运行或通过。

**目标一致性是阻断条件：** 在建立关联时，操作者必须明确确认该 Quality monitor 检查的是 ADS 输出目标；Quality 原域返回的目标身份需与关联记录一致。不能仅凭名称相似、同名表或同一数据源推断。

### 4.2 Execution identity

建议 Quality execution 持久化：
- `executionNo`、`monitorId`、数据源/目标身份；
- `executionDefinitionSha256`（对本次实际执行冻结定义规范化计算）；
- Workflow 执行时的 `taskRevisionId` / `taskRevisionNo` / `taskDefinitionSha256`（若有）；
- executionStatus、checkResult、计数、起止时间、重试/取消事实（若来源域实际支持）。

手动/计划运行也应固定本次执行定义摘要；Workflow 运行还应保留 Task Catalog 的精确 revision 身份。若旧执行没有该字段，则历史记录只能显示“执行事实可读、定义版本无法核验”，不能伪装成精确版本匹配。

## 5. 查询边界

候选查询链路（**设计提案，当前没有该 ADS association API**）：

1. Modeling 将当前 `adsModelId + contractRevision + contractSha256 + targetStructureFingerprint` 传给 Quality-owned evidence provider。
2. Quality provider 在当前项目与 Quality READ 权限范围内查找显式关联记录，并核验 associationDigest、Quality monitor 目标身份和 definition checksum。
3. 若关联有效，读取 monitor 的 `lastExecutionNo`；若为空，且查询成功并且调用者有权限，才可返回 `NO_RECORD`。
4. 若存在 executionNo，使用 Quality 原域精确读取 `GET /api/v1/data-quality/execution/{executionNo}`，校验返回的 monitorId、project scope（由源域权限/项目上下文保障）、目标身份与执行定义 pin。
5. Modeling 只接收最小化 DTO；不直接查询 Quality 数据库或内部 Repository，不将 SQL、完整规则配置、实际数据值或通知目标传回前端。

不使用：
- `findLatestForTarget` 之类内部“按目标找最近执行”查询作为 ADS 关联依据；
- monitor 列表/分页、关键字、名称、表名相似度或最近时间进行自动匹配；
- 需要 MONITOR_RUN 权限的状态接口作为普通 READ 证据接口；
- 可能锁定 monitor 的 `/definition` 路由作为纯读取证据接口。

## 6. 状态词典与失败边界

| 状态 / 字段 | 证据条件 | 展示约束 |
|---|---|---|
| `NOT_CONNECTED` | 当前没有获批、可查询的 ADS–Quality 关联能力 | “Quality 关联尚未接入”；不能说无监控/无运行 |
| `NOT_CONFIGURED` | 关联能力已部署、查询成功，但当前 ADS 合同没有关联记录 | “尚未关联质量监控” |
| `NO_RECORD` | 关联和 monitor 身份已核验，查询成功且 Quality 确认没有 executionNo | “该监控尚无运行记录” |
| `PENDING` / `RUNNING` | 精确 executionNo 返回真实运行状态 | 展示运行状态，不显示质量通过 |
| `SUCCEEDED` | 精确 executionNo 为成功终态且 `checkResult = PASSED` | 可显示“质量通过” |
| `FAILED` | execution 失败终态，或 checkResult 为 NOT_PASSED / ERROR | 展示真实失败类别；不得把检查未通过等同于执行基础设施失败 |
| `STALE` | 合同 revision/hash、monitor target 或冻结 definition checksum 与关联身份不一致 | “关联版本需复核”；不自动改绑 |
| `FORBIDDEN` | Quality 原域明确拒绝当前用户读取 | 不泄露跨项目对象是否存在 |
| `UNKNOWN` | Quality 未启用、超时、响应不兼容、旧执行缺 definition pin 或关联无法核验 | “暂无法核验”；不能回退成空记录或通过 |
| `DISABLED` | Quality 原域明确返回 monitor disabled | “监控已停用”；不能当作未执行或通过 |

执行状态与检查结果应在 DTO 中分字段返回；即使统一状态显示 FAILED，也要保留源域原始 `executionStatus` 与 `checkResult`，避免混淆“执行失败”和“检查未通过”。

## 7. 权限与数据最小化

- ADS 证据卡有 Modeling READ，不代表调用者有 Quality MONITOR_READ 或 EXECUTION_READ。
- Quality provider/API 必须独立执行当前项目与对应 Quality READ 权限；任何一项缺失不得通过 Modeling 权限代替。
- `@ConditionalOnQualityEnabled` 可能使 Quality API 未注册；404/超时不能解释为“没有记录”。没有可确认的能力状态时返回 UNKNOWN。
- Quality 执行详情包含 executedSql、规则实际值与错误信息；前端只展示经过审核的摘要、计数、状态和时间。
- 不调用创建/更新/删除监控、执行、取消、调度、SQL 或通知配置写接口；不改变 DR-07。
- Modeling 不直接依赖 Quality 内部 Repository；跨域调用应使用 owner-approved SPI/provider，维持模块依赖方向。

## 8. 决策选项

| 选项 | 方案 | 结论 |
|---|---|---|
| A | 从 Quality monitor 列表或目标表中匹配 ADS | 禁止：缺少稳定 ADS 身份关联 |
| B | 通过 `lastResult` 直接显示质量状态 | 禁止：没有精确 executionNo 和 definition pin 核验 |
| C | Quality-owned immutable association + execution definition pin + exact executionNo | **推荐进入 Owner 评审** |
| D | Modeling 直接查 Quality DB/Repository 或调用创建/执行接口探测 | 禁止：破坏真相域和只读边界 |
| E | 当前证据卡显示 NOT_CONNECTED，等待关联与执行 pin 能力获批 | 当前安全默认 |

## 9. 后续验收矩阵

若 Owner 批准方案 C，实施前必须明确并覆盖：

1. ADS 合同 revision/hash 与 Quality association 精确匹配；跨合同/跨项目引用被拒绝。
2. Quality monitor ID、target identity、task revision ID/no、definition SHA-256 精确匹配；任一漂移不自动修复。
3. 每条执行能证明实际使用的 definition checksum；Workflow 执行还要验证 taskRevision ID/no；旧执行缺 pin 时为 UNKNOWN。
4. 无关联、无运行记录、运行中、运行失败、检查未通过、检查错误、检查通过、监控停用、权限不足、Quality 未启用、超时分别有测试。
5. executionStatus 与 checkResult 分开测试，确保“执行成功但检查未通过”不能显示为质量通过。
6. 只读证据路径不调用 monitor update/delete/run、execution POST、SQL、通知配置或消费授权写接口。
7. 最小 DTO 不暴露 executedSql、实际数据值、通知目标或完整错误堆栈。
8. 后端、前端、项目隔离、权限撤销、Product Guard、Architecture Gate 与分发一致性检查通过；真实环境验收另行记录。

## 10. 当前结论与下一步

本提案是**设计门槛，不是生产实现授权**。现有 Quality 域的 immutable task revision 是可复用基础，但执行记录没有显式 pin 到该 revision；这是必须先补齐的源码级缺口。

下一步：
1. Product / Modeling / Quality Owner 评审是否采用方案 C。
2. 明确关联写操作是否由 Quality 域提供独立的、经授权的管理入口；这不属于当前只读 C3 交付。
3. Owner 签收后，先补齐 Quality execution definition pin 与最小证据 provider，再把 Quality 状态接入 ADS 证据卡。
4. 在批准前，PR #611 中的 Quality 状态继续保持 NOT_CONNECTED。
