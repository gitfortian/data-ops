# Product Glossary

目标：一词一义。此文件只维护跨产品域的稳定概念；模块内部技术术语留在各自 DOMAIN.md。

| 术语 | 产品定义 | Owner |
|---|---|---|
| DataSource / 数据源 | 外部数据系统的可复用连接与访问入口 | datasource |
| Metadata / 元数据 | 关于数据对象的结构、属性与目录事实 | metadata |
| Semantic / 语义标准 | 业务域、业务过程、标准字段、命名/类型/单位/口径等业务标准 | semantic |
| Model / 模型 | 数仓或数据结构的设计定义 | modeling |
| Metric / 指标 | 有明确业务含义和计算定义、可被统一消费的业务度量 | metric |
| Task / 任务 | 可执行工作的不可变或可版本化定义 | task owner / development |
| Workflow / 工作流 | 对任务进行 DAG 编排和运行管理的业务流程 | workflow |
| Dataset / 数据集 | 面向消费的稳定数据契约，拥有自身 definition/release Truth，是 Query/Preview/Export 及后续 Dashboard/API/Agent 的数据入口之一 | dataset |
| Data Service / 数据服务 | 面向消费的稳定服务接口，拥有自身 definition/revision/runtime Truth，通过 active endpoint 提供受治理调用能力 | data service |
| Data Product / 数据产品 | 面向用户的 governed product projection；Phase 4 由 Dataset 或 Data Service owning identity 派生，不是第二份 Dataset/Data Service/Asset Truth | consumption projection; source truth remains owning domain |
| Consumption Contract / 消费契约 | 将 source identity、owner、project、version、lifecycle、availability、quality、security、lineage、access、endpoint、usage 等 owning facts 组合成稳定消费语义的跨域契约 | product contract; facts remain source-owned |
| Consumer / 消费主体 | 对 Data Product 存在声明依赖或实际消费的主体，以 `ConsumerRef(type, sourceDomain, sourceIdentity)` 引用；主体本身仍由 respective domain 拥有 | respective consumer domain |
| Access / 访问状态 | 当前主体是否被允许执行某种消费动作的授权决策；不等于 Subscription 或 Usage | security / access provider |
| Subscription / 订阅 | Consumer 对 Data Product 的声明依赖关系；不等于权限批准，也不等于实际使用 | consumption |
| Usage Evidence / 使用证据 | 某 Consumer 在某时间通过某种方式实际消费 Data Product 的可验证事实；不等于 Lineage 或 Permission | consumption |
| Asset / 数据资产 | 对可治理数据对象的统一身份、状态与 360° 聚合视图 | asset |
| Lineage / 血缘 | 数据对象之间的来源、加工、依赖和影响关系；不等于实际 Consumer Usage | lineage |
| Evidence / 证据 | 来自查询、运行、质量、审计等可验证事实，不等于 LLM 输出 | source domain |
| Project Space | 数据和操作的业务隔离边界 | platform security |

## 消费状态词汇

以下状态维度必须保持正交，不允许压缩成一个模糊的 `status`：

- Source lifecycle：`NOT_PUBLISHED / PUBLISHED / DEPRECATED / RETIRED`
- Availability：`AVAILABLE / UNAVAILABLE / UNKNOWN`
- Access：`ALLOWED / REQUEST_REQUIRED / FORBIDDEN / NOT_APPLICABLE`
- Section / Provider evidence state：`READY / EMPTY / UNAVAILABLE / FORBIDDEN / NOT_APPLICABLE`

其中：

- `EMPTY` = provider 正常且确认没有数据；
- `UNAVAILABLE` = provider/依赖不可用，无法判断；
- `FORBIDDEN` = 当前用户无权读取/执行；
- `NOT_APPLICABLE` = 对当前 product type 或场景不成立。

## 禁止歧义

以下概念不得在不同模块重新定义同名不同义：

- 业务域
- 业务过程
- 指标
- 口径
- 数据集
- 数据服务
- 数据产品
- Consumer
- Access
- Subscription
- Usage Evidence
- 数据资产
- 发布
- 上架
- 版本
- 负责人

发现歧义时，应先修产品词汇与 Owner，再修代码，不允许通过前端文案掩盖。
