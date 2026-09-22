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
| Dataset / 数据集 | 面向消费的稳定数据契约，是 Dashboard/API/Agent 的默认数据入口 | dataset |
| Asset / 数据资产 | 对可治理数据对象的统一身份、状态与 360° 聚合视图 | asset |
| Lineage / 血缘 | 数据对象之间的来源、加工、依赖和影响关系 | lineage |
| Evidence / 证据 | 来自查询、运行、质量、审计等可验证事实，不等于 LLM 输出 | source domain |
| Project Space | 数据和操作的业务隔离边界 | platform security |

## 禁止歧义

以下概念不得在不同模块重新定义同名不同义：

- 业务域
- 业务过程
- 指标
- 口径
- 数据集
- 数据资产
- 发布
- 上架
- 版本
- 负责人

发现歧义时，应先修产品词汇与 Owner，再修代码，不允许通过前端文案掩盖。
