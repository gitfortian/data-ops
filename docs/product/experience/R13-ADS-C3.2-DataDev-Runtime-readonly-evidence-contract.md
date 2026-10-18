# R13-C3.2｜ADS → DataDev / Runtime 只读证据关联契约提案

> 状态：**源码盘点与契约提案，待 Modeling / DataDev / Runtime Owner 评审；不包含生产代码变更，也不授予任何任务创建、绑定或执行权限。**  
> 基线日期：2026-10-18  
> 关联：C3 总 Issue [#602](https://github.com/gitfortian/data-ops/issues/602)、本设计 Issue [#609](https://github.com/gitfortian/data-ops/issues/609)、DR-07 [#557](https://github.com/gitfortian/data-ops/issues/557)、Metric 引用核验 [PR #608](https://github.com/gitfortian/data-ops/pull/608)。

## 1. 决策摘要

**推荐方向：DataDev 持有显式、不可变的 ADS 合同修订关联；Runtime 持有真实执行记录；Modeling 只读取经授权的最小证据 DTO。**

当前不能把 ADS 合同直接关联到 DataDev/Runtime 执行记录。虽然各域分别存在模型、任务、修订与执行对象，但缺少从 ADS 的 modelId + contract revision/hash 到精确 TaskRevision、再到精确 executionId 的已批准关联链。

本提案不授权创建关联记录。先由领域 Owner 签收字段与安全边界；后续是否需要单独的人工关联/任务交接写操作，必须另立决策，不能借 C3 只读证据卡启用。

## 2. 源码级事实

### 2.1 Modeling：ADS 合同的真相归属

主要源码：
- `data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/controller/v1/AdsApplicationDesignController.java`
- `data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/derive/AdsApplicationDesignService.java`
- `data-ops-ui/src/services/modeling/adsApplication.ts`

当前路由与语义：
- GET /api/v1/modeling/models/{modelId}/ads-application：读取当前合同。
- GET .../history：读取最近最多 50 条不可变修订。
- POST .../validate：服务端静态校验，不执行 SQL。
- PUT .../ads-application：以期望修订号、目标结构指纹进行并发保护并保存。

AdsApplicationDesignService.Saved 保存 modelId、revision、contractSha256、contractJson、updatedBy、updatedAt。合同自身冻结上游 modelId/versionId/versionNo/checksum/layerCode、输出字段、业务键和目标结构指纹；Metric pin 已冻结 metricId/metricVersionId/metricVersion/snapshotDigest/publicationEventId。

**Modeling Truth Owner：** ADS 合同内容、修订号、合同哈希、目标结构指纹和上游模型版本 pin。Modeling 不拥有 DataDev TaskRevision 或 Runtime execution 的真实状态。

### 2.2 DataDev：任务、TaskRevision 与现有绑定草案

主要源码：
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/controller/v1/DevelopmentModelTaskBindingController.java`
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/handoff/DevelopmentModelTaskBinding.java`
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/handoff/DevelopmentModelTaskBindingCoordinator.java`
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/handoff/DevelopmentModelTaskBindingRepository.java`

现有 DevelopmentModelTaskBinding 字段包括：
projectId、modelId、modelVersionId、modelVersionNo、mappingRevisionId、mappingSha256、逻辑/物理/标准快照摘要、nodeId、taskRevisionId、taskRevisionNo、taskDefinitionSha256、目标数据源/表、bindingDigest、idempotencyKey 与状态。

但这不是可以直接复用的 ADS 关联契约：
- 该实现明确是 DWD/物理模型交接语义，依赖 PublishedDwdDesignEvidenceProvider。
- Controller 和 Coordinator 都由 data-ops.dwd.binding-write-authorized=true 条件控制，缺省为禁用。
- 其状态 BINDING_RECORDED_NOT_EXECUTABLE 明确不等于执行授权或数据已写入。
- 现有对象没有 adsContractRevision 与 adsContractSha256，不能证明绑定的是哪一版 ADS 输出合同。

**DataDev Truth Owner：** Task/TaskRevision 身份、任务定义摘要、显式关联记录及关联记录的审计历史。不得通过 Modeling 直接读写 DataDev 数据表来绕过 Owner 边界。

### 2.3 Runtime：执行记录不包含完整的 ADS 归属

主要源码：
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/controller/v1/DevelopmentTaskExecutionController.java`
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/execution/model/DevelopmentTaskExecutionDetail.java`
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/execution/DevelopmentTaskExecutionService.java`

现有执行详情含 id、nodeId、任务名称/类型、runtimeExecutionId、重试来源、状态、操作者、持续时间、错误信息、输出及开始/结束时间；不含 ADS 合同修订/hash，也不直接携带 taskRevisionId/taskDefinitionSha256。

GET /api/v1/data-development/executions 支持分页、关键字、状态、任务类型、触发类型和时间过滤，但没有 ADS modelId 或 ADS contract revision 的精确过滤条件。名称/keyword/时间接近不是可审计的关联证据。

另需注意：
- GET /api/v1/data-development/executions/{id} 调用 controlService.refresh(id)。
- GET /api/v1/data-development/executions/active?nodeId=... 调用 controlService.reattachActive(nodeId)。

上述调用不是纯粹读取一份静态历史 DTO；它们可能刷新或重新挂接运行状态。C3 不应把这些 GET 端点直接当作无副作用证据接口，须先审查其具体运行时交互与持久化行为。

**Runtime Truth Owner：** execution ID、真实状态、起止时间、错误/结果摘要及重试关系。Runtime 状态不能由 Modeling 复制后变成 Modeling 自有真相。

## 3. 推荐的关联身份

未来若经 Owner 决策批准，建议 DataDev 拥有一条只表达“此任务修订被明确关联到此 ADS 合同修订”的不可变记录。

### 3.1 DataDev-owned ADS Task Association（提案字段，非现有 DTO）

| 字段 | Owner / 来源 | 目的 |
|---|---|---|
| associationId | DataDev | 关联记录稳定主键 |
| projectId | 服务端当前项目上下文 | 强制项目隔离；禁止由客户端决定 |
| adsModelId | Modeling 身份，保存时精确核验 | 定位 ADS 模型 |
| adsContractRevision | Modeling 合同回读 | 固定关联的合同修订 |
| adsContractSha256 | Modeling 合同回读 | 防止同 ID 下关联漂移 |
| targetStructureFingerprint | Modeling 合同/当前结构核验 | 固定输出结构的内容身份 |
| upstreamModelId、upstreamVersionId、upstreamVersionNo、upstreamChecksum | ADS 合同冻结 pin | 记录任务关联时使用的上游精确版本 |
| nodeId | DataDev | 指向任务节点 |
| taskRevisionId、taskRevisionNo、taskDefinitionSha256 | DataDev | 固定不可变任务修订及定义摘要 |
| associationDigest | DataDev | 对以上身份字段做规范化摘要，供回读校验 |
| idempotencyKey | DataDev | 未来显式写操作防重复；不构成授权 |
| createdBy、createdAt、state | DataDev 审计 | 追溯关联动作与当前关联状态 |

说明：
1. 此表是领域设计提案，不代表主线已有该 DTO、表或 API。
2. 如果未来 TaskRevision 的目标是已发布 ADS 物理模型，还需在 Owner 评审中明确是否增加 adsModelVersionId/adsModelVersionNo；不能用目标表名替代版本身份。
3. 修改任何被冻结的身份都应产生新关联修订，不得原地改写历史记录。
4. 关联记录只证明“关联关系已记录”，不证明任务已发布、执行已成功、目标数据已落地或质量已通过。

### 3.2 Runtime execution 的二级关联

Runtime 执行应通过已核验的 DataDev association 精确到 taskRevisionId + taskDefinitionSha256，并记录真实 executionId/runtimeExecutionId。如果当前 Runtime 持久化结构不能证明一次执行确实使用了指定 TaskRevision，则该次执行必须显示 UNKNOWN，不得仅凭 nodeId 归因。

推荐 Runtime 证据最小字段：
- executionId、runtimeExecutionId；
- associationId 或经 Owner 批准的等价关联键；
- taskRevisionId、taskDefinitionSha256；
- 源域状态、开始/结束时间、可安全展示的错误摘要、重试父子关系；
- checkedAt 与来源域。

Runtime 是执行状态的唯一 Truth Owner。工作台只展示最小摘要，不暴露 SQL、完整 config、token、密钥或敏感输出。

## 4. 查询边界提案

候选 API（**仅用于讨论，当前不存在，不可调用**）：

- DataDev Owner：按当前项目及 adsModelId + adsContractRevision + adsContractSha256 查询显式关联。
- 若找到关联：返回精确 associationId/taskRevisionId/taskDefinitionSha256，并校验关联摘要与 ADS 合同冻结身份。
- Runtime Owner：按显式关联键或精确 executionId 查询执行证据，不允许从分页列表、任务名称或“最近一次运行”推断。
- Modeling 调用方仅消费最小 DTO；不能直接依赖 DataDev/Runtime 内部 Repository，也不能写对方数据库。
- 所有端点必须使用服务端当前项目上下文；分别校验 Modeling READ、DataDevelopment READ 与 Runtime 读权限。某域权限不足不得由另一域的 READ/UPDATE 权限替代。

若当前没有关联记录，只有在关联机制已部署、查询成功且调用者有权确认的前提下，才可返回 NOT_CONFIGURED 或 NO_RECORD。若关联机制/反查端点尚未接入，必须返回 NOT_CONNECTED；请求失败、超时、DTO 不兼容则为 UNKNOWN。跨项目对象不能泄露存在性。

## 5. 状态词典与证据前提

| 状态 | 必须具备的前提 | UI 允许表达 | 禁止表达 |
|---|---|---|---|
| NOT_CONFIGURED | 关联能力已接入且查询成功，确认当前合同尚未配置关联 | 尚未关联任务 | “没有任务记录” |
| NOT_CONNECTED | 尚无获批的关联机制/API | DataDev/Runtime 关联尚未接入 | “未执行”或“执行失败” |
| NO_RECORD | 精确关联已核验，查询成功且源域确认无 execution | 该任务修订尚无运行记录 | “任务不存在” |
| PENDING / RUNNING | Runtime 返回匹配的真实 execution ID 与状态 | 等待中/运行中 | 由时间或任务节点推断运行中 |
| SUCCEEDED | Runtime 对精确 execution ID 返回成功终态 | 该次运行成功 | “数据质量通过”或“消费者已授权” |
| FAILED | Runtime 对精确 execution ID 返回失败终态 | 该次运行失败及安全错误摘要 | 由异常日志关键词猜测失败归属 |
| STALE | 关联冻结的 ADS 合同 hash、目标结构或 TaskRevision 摘要与精确源域事实不匹配 | 关联版本需要复核 | 仅因存在更新版本就自动判旧版本失效 |
| FORBIDDEN | 当前项目内的源域权限检查明确拒绝且可安全展示 | 无权读取该证据 | 暴露其他项目对象存在性 |
| UNKNOWN | 源域不可用、超时、身份不完整或执行归属无法证明 | 暂无法核验 | 空数组、成功或失败 |
| NOT_APPLICABLE | 经批准的业务规则证明该 ADS 场景不需要 DataDev/Runtime 关联 | 不适用及原因 | 通用兜底状态 |

## 6. 读写边界与 DR-07

- 当前 DR-07 #557 状态仍是 DECISION_REQUIRED / NO_WRITE_AUTHORIZATION。
- 本提案不启用 data-ops.dwd.binding-write-authorized，不调用绑定 POST，不创建 TaskRevision，不执行/重试/取消任务，不执行 SQL。
- 现有 gated binding 是 DWD 交接草案，不能因“已有代码”就推导为 ADS 可用的通用关联服务。
- 人工跳转 DataDev 与系统创建/关联任务必须分开：跳转只传递只读上下文；它不写入关联、不代表任务已经关联。
- ADS 关联是否适用 DR-07，还是需要独立的 ADS/DataDev/Runtime Owner 决策，必须由 Owner 明确签收；本提案不擅自扩张 DR-07 的授权范围。
- 任何后续关联写操作都需要独立的权限、人工确认、幂等、审计、并发与回滚设计；不得混入只读证据卡 PR。

## 7. 安全与故障矩阵

| 场景 | 预期状态/行为 |
|---|---|
| 合同不存在或合同读取失败 | Modeling 原域错误状态；不拼接任何下游证据 |
| 合同有明确 revision/hash，尚无关联能力 | NOT_CONNECTED |
| 关联功能已上线、查询成功、当前合同无关联 | NOT_CONFIGURED |
| 关联引用到另一个合同 revision/hash | STALE 或源域定义的冲突状态；不自动切换 |
| TaskRevision ID 存在但定义摘要不一致 | STALE，拒绝归因执行 |
| 同一节点已有另一 TaskRevision | 不按节点 ID 将历史运行归给当前合同 |
| execution 只匹配 nodeId，缺 taskRevision/association 证据 | UNKNOWN，不显示成功/失败 |
| 当前用户无 DataDev/Runtime READ | FORBIDDEN（不得泄露跨项目对象） |
| 跨项目的 model/task/association/execution ID | 视作不可见；不泄露对象存在性 |
| Runtime 超时或状态刷新失败 | UNKNOWN，保留最近一次带时间戳的已核验结果（若有），标明过期；不能改成空记录 |
| 重试 execution | 以源域真实的 retryOfExecutionId 建立关系；不把父执行状态覆盖为子执行状态 |
| TaskRevision 更新、ADS 新建合同修订 | 保留历史关联；新合同修订不自动继承为已核验关联 |
| 用户手动打开 DataDev | 只传递 adsModelId/revision/hash 等上下文；没有显式关联回执时仍为 NOT_CONNECTED |

## 8. 决策选项

| 选项 | 方案 | 结论 |
|---|---|---|
| A | 复用当前 gated DWD Model↔Task binding | 不直接采用：语义为 DWD、无 ADS contract revision/hash、受 DR-07 门禁控制 |
| B | DataDev-owned ADS association + Runtime exact execution evidence | **推荐进入 Owner 评审**；边界清晰，但需要先批准关联模型与必要写操作 |
| C | 用任务名称、模型名称、SQL 文本、keyword 或最近执行时间匹配 | 禁止：不可审计且容易把别的任务状态归给 ADS |
| D | Modeling 直接查询 DataDev/Runtime 数据库或直接依赖内部 Repository | 禁止：破坏真相域与模块依赖边界 |
| E | 证据卡暂时展示 NOT_CONNECTED 并提供带上下文的人工跳转 | 当前安全默认；不伪造闭环 |

**推荐决策：** 批准 B 的领域边界作为后续设计方向；在 DataDev/Runtime Owner 明确批准关联记录写权限、查询 API 与审计合同之前，生产 UI 采用 E。该建议尚未获 Owner 签收。

## 9. 验收矩阵

后续若进入实现，至少需要：

1. 合同 revision/hash 与关联记录精确匹配；篡改/过期 hash 被拒绝。
2. 同一 ADS 合同关联不同 TaskRevision、同一 TaskRevision 关联冲突合同、幂等重试与并发修改均有确定结果。
3. 项目隔离、Modeling READ 但无 DataDev READ、DataDev READ 但无 Runtime READ、权限撤销均有拒绝测试。
4. execution 仅有 nodeId、没有 association/taskRevision pin 时不能归因；同一节点多次执行、重试链路正确区分。
5. 关联存在但没有运行记录，显示 NO_RECORD；运行成功/失败/运行中只按 Runtime 原域事实展示。
6. 超时、不可用、响应不兼容和状态刷新失败显示 UNKNOWN，不回退 mock。
7. 测试覆盖不调用 POST bind、执行、重试、取消、SQL、发布和授权接口；binding-write-authorized 默认仍关闭。
8. PR 合并/CI PASS 与真实环境验收分别记录；C3 的证据卡不宣称质量、Dataset、Service 或消费授权已闭环。

## 10. 当前结论与下一步

本轮只冻结 DataDev/Runtime 的事实、关联身份建议和 Owner 决策门槛。**未新增生产行为、未开启写权限、未创建任务或运行记录。**

下一步：
1. 产品负责人确认 B/E 的产品方向。
2. DataDev 与 Runtime Owners 评审字段、API、状态映射和读写边界。
3. 明确是否需要单独的 ADS 关联写授权；DR-07 不因本提案自动变化。
4. Owner 签收后，再拆分只读证据查询实现与任何关联写操作；后者必须另行授权。
