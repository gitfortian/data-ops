# R13-C3｜ADS 只读交接证据 API 与真相域盘点

状态：**技术盘点完成，C3 生产实现未开始**。  
基线：C1 #599 已合并；C2 #604 已合并；C3 UX 原型 #603 已合并；产品尚未逐项签收 UX Gate。  
关联：Issue #602（C3 只读交接证据）、Issue #605（Metric 引用身份与核验边界）。

## 1. 盘点原则

本文只把当前主线代码中实际存在的 API、权限声明和稳定关联字段当作证据。前端服务文件证明客户端所使用的路由，不单独证明后端权限、业务语义或数据归属；涉及授权和写入边界时同时检查 Controller 与服务/DTO。

- 不根据名称相似、自由文本 keyword 或“最近记录”推断 ADS 与下游对象有关联。
- 不用空数组代表请求失败、无权访问或接口未接入。
- 不调用执行、发布、同步、授权、Key 变更等写接口来“探测”状态。
- 当前合同已保存只证明 Modeling 合同持久化，不证明 Runtime、Quality 或消费可用。

## 2. Modeling 合同（本模块已接入）

证据文件：
- `data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/controller/v1/AdsApplicationDesignController.java`
- `data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/derive/AdsApplicationDesignService.java`
- `data-ops-ui/src/services/modeling/adsApplication.ts`

| 操作 | 路由 | 权限/语义 |
|---|---|---|
| 当前合同 | `GET /api/v1/modeling/models/{modelId}/ads-application` | 项目必需；READ |
| 历史修订 | `GET .../history` | 项目必需；READ；最多 50 条 |
| 静态校验 | `POST .../validate` | 项目必需；READ；只校验合同，不执行 SQL |
| 保存修订 | `PUT .../ads-application` | 项目必需；READ + UPDATE；原子校验 revision 和目标结构指纹 |

C2 已在模型详情嵌入六步工作台，固定上游模型 ID、版本 ID、版本号、checksum；保存后回读合同 revision/hash。目标结构变化时需重新核对并静态校验。合同中的 `DELIVERY_NOT_CONFIGURED` 是 Modeling 自己的设计配置状态，不是 Dataset/Service/Usage 原域授权状态。

## 3. Metric / Semantic

证据文件：
- `data-ops-business/data-ops-business-metric/src/main/java/io/yak/ops/business/metric/controller/v1/MetricPublicationController.java`
- `data-ops-business/data-ops-business-metric/src/main/java/io/yak/ops/business/metric/controller/v1/MetricVersionController.java`
- `data-ops-ui/src/services/metric/api.ts`
- `data-ops-ui/src/services/metric/types.ts`

已核实的只读路由：
- `GET /api/v1/metrics/{metricId}/publication`：当前发布合同或 null。
- `GET /api/v1/metrics/publications/active`：当前项目可见范围内的活跃发布合同列表。
- `GET /api/v1/metrics/{metricId}/versions` 与 `GET .../{versionNo}`：按 Metric ID + 版本号读取历史版本。
- `GET /api/v1/metrics/{metricId}/versions/{versionNo}/publication-readiness`：发布就绪证据。

发布合同具有 `metricId`、`metricVersionId`、`metricVersion`、`snapshotDigest`、`publicationEventId` 等字段。Controller 受项目作用域和 Metric READ 权限约束。

**阻断性发现：** 当前 ADS 合同只保存 `metricVersionId`。C1 的 `AdsApplicationDesignService.validateAndFreeze` 将该值写入合同，但未通过 Metric 原域验证。Metric 版本详情路由需要 `metricId + versionNo`，而活跃发布列表不是以 `metricVersionId` 为路径参数的精确查询。因此仅凭当前 ADS 字段，无法在所有状态下可靠证明“对应的 Metric 版本存在、仍发布、快照未漂移”。活跃列表匹配不到也不能证明 Metric 不存在（可能已撤回、不可见或接口不可用）。

**处理方式：** C3 不得把该引用显示为已核验。使用 `UNKNOWN` 或“引用身份不足，待完成契约设计”的明确状态；先完成 Issue #605 的引用身份和跨模块核验边界设计。不得为此直接添加 Modeling → Metric 反向 Maven 依赖，Metric 当前已经依赖 Modeling API。

## 4. DataDev / Runtime

证据文件：
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/controller/v1/DevelopmentTaskExecutionController.java`
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/controller/v1/DevelopmentModelTaskBindingController.java`
- `data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/handoff/DevelopmentModelTaskBinding.java`
- `data-ops-ui/src/services/data-development/api.ts`

运行记录只读路由：
- `GET /api/v1/data-development/executions`：支持 pageNo/pageSize、keyword、status、taskType、triggerType、时间范围；没有 ADS modelId 过滤。
- `GET /api/v1/data-development/executions/active?nodeId=...`：以 DataDev nodeId 查询活动运行。
- `GET /api/v1/data-development/executions/{executionId}`：查询运行记录。

运行接口有项目作用域与 DataDevelopment READ 权限。现有 Model↔Task 绑定记录确实含 `modelId/modelVersionId/nodeId/taskRevisionId`，但对应的 `DevelopmentModelTaskBindingController` 整体受 `data-ops.dwd.binding-write-authorized=true` 条件控制（默认缺失时禁用），其读取路由按 `bindingId`，没有普遍可用的 ADS modelId 反查端点；注释明确要求 DR-07 Owner approval 后才能启用相关绑定能力。

**结论：** 当前不能从 ADS modelId 可靠地定位任务/运行。禁止通过模型名、task keyword 或最近执行记录猜测关联；在关联 API 与 DR-07 边界明确前，显示 `NOT_CONNECTED`，不查询执行记录来推断成功或失败。

## 5. Quality

证据文件：
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/controller/v1/QualityExecutionController.java`
- `data-ops-business/data-ops-business-quality/src/main/java/io/yak/ops/business/quality/controller/v1/QualityExecutionWorkspaceController.java`
- `data-ops-common/src/main/java/io/yak/ops/common/bean/dto/quality/QualityExecutionWorkspaceDTO.java`
- `data-ops-ui/src/services/data-quality/api.ts`

只读路由：
- `POST /api/v1/data-quality/execution/workspace/page`：过滤项包括 keyword/objectKeyword、dataSourceId、monitorId、执行状态、检查结果、触发类型等。
- `GET /api/v1/data-quality/execution/workspace/{executionNo}` 与 `GET .../{executionNo}/logs`：按已有执行号读取详情/日志。
- 传统执行接口提供 `POST /api/v1/data-quality/execution/page`、`GET .../{executionNo}` 和状态查询。

接口受项目作用域、Quality EXECUTION_READ 等权限保护，并受 `ConditionalOnQualityEnabled` 配置影响。查询 DTO 没有 ADS modelId 作为稳定关联键；当前 ADS 合同也没有 quality monitor/rule ID 或质量运行 ID。

**结论：** 无稳定关联时不能查询“最近一条”并归因给 ADS。未接入或无法建立关联显示 `NOT_CONNECTED`；只有未来通过明确引用成功查询且确认无记录时，才能显示 `NO_RECORD`。没有质量运行证据绝不能显示通过。

## 6. Dataset

证据文件：
- `data-ops-business/data-ops-business-dataset/src/main/java/io/yak/ops/business/dataset/controller/v1/DatasetController.java`
- `data-ops-business/data-ops-business-dataset/src/main/java/io/yak/ops/business/dataset/controller/v1/DatasetDevelopmentSourceController.java`
- `data-ops-business/data-ops-business-dataset/src/main/java/io/yak/ops/business/dataset/DevelopmentDatasetFacade.java`
- `data-ops-ui/src/services/dataset/api.ts`

只读路由：
- `GET /api/v1/datasets`
- `GET /api/v1/datasets/catalog?datasetIds=...&onlineOnly=...`
- `GET /api/v1/datasets/{datasetId}`
- `GET /api/v1/datasets/{datasetId}/development-source`：仅按 Dataset ID 查询其 DataDev 来源（如果适用）。

Dataset API 有 `PROJECT_REQUIRED` 项目作用域。其开发来源关联到 DataDev node/task revision；当前 ADS 合同没有 `datasetId`，也没有指向 Dataset 的稳定 source identity。

**结论：** Dataset 列表/目录存在，不等于当前 ADS 已生成或已发布 Dataset。C3 不应通过名称相似或输出表名自动关联；在明确引用前显示 `NOT_CONNECTED`。

## 7. Data Service 与访问配置

证据文件：
- `data-ops-business/data-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/controller/v1/DataServiceController.java`
- `data-ops-business/data-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/controller/v1/DataServiceRuntimeController.java`
- `data-ops-business/data-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/controller/v1/DataServiceAccessController.java`
- `data-ops-business/data-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/controller/v1/DataServiceConsumerController.java`
- `data-ops-ui/src/services/data-service/api.ts`

可用只读证据包括：
- `GET /api/v1/data-service` 与 `GET /api/v1/data-service/{id}`（DataService READ）。
- `GET /api/v1/data-service/{id}/runtime`（RUNTIME）。
- `GET /api/v1/data-service/{id}/logs`、`GET /api/v1/data-service/{id}/logs/{invocationId}`（OBSERVE）。
- `GET /api/v1/data-service/access-overview`、`GET /api/v1/data-service/{id}/keys`、`GET /api/v1/data-service/{id}/ip-access`（ACCESS）。

访问配置读取可能涉及敏感的 Key 元数据；C3 只可使用经安全审查的最小化状态 DTO，绝不展示密钥/令牌或调用写接口。运行状态、服务 enabled、API Key 数量或 IP 规则都不等于某个具体消费者已经拥有有效访问权。

当前 ADS 合同没有 `dataServiceId`，也没有消费方 principal、服务访问范围或授权决策引用。因而不能将服务存在/运行健康直接解释为消费授权已核验。

## 8. Consumption / Usage

证据文件：
- `data-ops-business/data-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/discovery/ProductDiscoveryController.java`
- `data-ops-business/data-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/SubscriptionController.java`
- `data-ops-business/data-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/source/DataServiceUsageEvidenceController.java`
- `data-ops-ui/src/services/consumption/api.ts`

只读路由：
- `GET /api/v1/consumption/products`
- `GET /api/v1/consumption/products/{productKey}`
- `GET /api/v1/consumption/navigation/sources/{productType}/{sourceIdentity}`
- `GET /api/v1/consumption/subscriptions?productKey=...`（Asset READ，项目作用域）。

消费订阅表示声明的消费依赖，不是底层数据访问授权。消费证据同步是 `POST /api/v1/consumption/usage-evidence/data-service/synchronize`，要求 Asset UPDATE 且会写入归一化证据；C3 禁止调用该接口。当前 ADS 合同没有 `productKey` 或可映射到 Consumption 的稳定 `sourceIdentity`。

**结论：** 未能核验产品身份或消费方引用时显示 `UNKNOWN` / `NOT_CONNECTED`。不能把订阅存在、产品可发现或 Data Service enabled 解释成访问授权通过。

## 9. 统一状态派生规则

| 状态 | 允许出现的证据条件 | 禁止的推断 |
|---|---|---|
| `NOT_CONFIGURED` | 当前合同字段明确为空，且该域的引用确由合同持有 | 不能用于表示 API 未接入 |
| `NOT_CONNECTED` | 当前应用尚无稳定关联键/API，或该源域集成尚未实现 | 不能说源域没有记录 |
| `NO_RECORD` | 稳定关联已核验、查询成功且调用者有权，源域明确返回无记录 | 不能由空数组/404/超时推断 |
| `PENDING` / `RUNNING` | 源域明确返回相应状态及证据 ID | 不能由时间新旧推断 |
| `SUCCEEDED` / `FAILED` | 源域真实终态和可追溯执行 ID | 不能由合同保存或服务存在推断 |
| `STALE` | 源域提供的不可变版本/摘要与合同冻结身份确实不一致 | 不能仅因“不是最新”自动判为失效；要遵循版本政策 |
| `FORBIDDEN` | 源域明确拒绝当前用户/项目读取，且可安全表达 | 不得泄露跨项目对象是否存在 |
| `UNKNOWN` | 超时、源域不可用、响应不兼容、引用身份不足或证据无法核验 | 不能降级为空数组或成功 |
| `NOT_APPLICABLE` | 明确业务规则证明该域不适用 | 不能作为通用兜底 |

## 10. C3 实现前的决策与依赖

1. **先完成 Issue #605**：为 ADS Metric 引用定义完整、可冻结、跨项目可核验的身份，并解决模块依赖方向；未完成前 Metric 卡片不能声称已核验。
2. DataDev 关联需要一个经过治理批准、只读、按项目和 ADS model/version 查询的反查边界；不能在 C3 内启用 DR-07 绑定写门禁。
3. Quality 需要明确可由 ADS contract/source identity 稳定追溯到 monitor/rule/run 的关系；否则只显示 NOT_CONNECTED。
4. Dataset/Service/Consumption 需要明确 owner-approved 的引用或 canonical source identity；订阅与访问授权必须分开建模。
5. 产品先签收已合并的高保真原型和状态词典；之后才能提交 C3 高保证实现方案，再进行生产 API/页面开发。
6. C3 仅允许 GET/只读查询；不执行 SQL、创建/重试任务、运行质量检查、发布 Metric/Dataset/Service、同步消费证据、创建订阅、创建/轮换 API Key 或修改访问策略。

## 11. 本阶段结论

当前有多个独立原域的只读 API，但 **没有一条可直接从 ADS 合同贯通 Metric → Runtime → Quality → Dataset/Service → 消费授权的完整、可证明链路**。这不是页面拼接就能补齐的问题；必须先补齐稳定身份与 owner-approved 关联边界。C3 的首个生产版本应如实显示缺口状态，不以猜测匹配或跨域写操作制造“闭环”假象。
