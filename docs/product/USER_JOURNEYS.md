# Core User Journeys

后续 Feature 至少服务某条 Journey，或通过 Product Review 新增。

## J1. 从外部数据到可信、可治理的数据对象

~~~text
DataSource
 -> Sync / Metadata Harvest
 -> Semantic / Model
 -> Development
 -> Workflow
 -> Quality / Security
 -> Catalog / Governance view
~~~

成功结果：用户得到可追溯、可运行、可治理的数据对象，并能理解其来源和状态。

## J2. 从业务定义到统一指标

~~~text
Business Domain / Process
 -> Standard / Caliber
 -> Model
 -> Metric
 -> Validation
 -> Consumption
 -> Usage / Impact
~~~

成功结果：指标不是定义台账，而是可验证、可复用、可追踪使用关系的统一业务口径。

## J3. 从数据生产到受治理消费

默认消费路径由 `PD-002-governed-consumption-contract.md` 冻结：

~~~text
Development / Workflow
 -> Published Dataset / Data Service
 -> Asset Governance
 -> Consumption Discovery
 -> Governed Consumption Contract
 -> Access State
 -> Query / Preview / Export / Invoke
 -> Usage Evidence
 -> Consumer / Impact
 -> Asset / Producer backlink
~~~

其中：

- Data Product 是 Dataset / Data Service 的 governed projection，不创建第二份 source Truth；
- Dataset 与 Data Service 共享治理外壳，但分别保留 schema contract 与 service interface/runtime contract；
- Access、Subscription、Usage Evidence、Lineage 是不同事实；
- Subscription 表达声明依赖，Usage Evidence 表达实际发生的消费；
- EMPTY、UNAVAILABLE、FORBIDDEN、NOT_APPLICABLE 必须保持不同语义；
- Consumption Hub、Asset、Dataset/Data Service 可以从不同上下文进入，但最终指向同一 canonical consumption context。

成功结果：生产侧和消费侧通过稳定契约连接，用户能回答“这是什么、能不能用、怎么用、谁在用、变更影响谁”，且不由每个消费产品重复定义数据访问和业务口径。

## J4. 从发现问题到定位影响

~~~text
Runtime / Quality Alert
 -> governed object
 -> Lineage
 -> Subscription / Usage Evidence
 -> Upstream / Downstream / Known Consumers
 -> Owner
 -> Rerun / Fix / Notify
~~~

成功结果：用户从异常直接定位来源、影响范围和负责人。Impact View 可以组合 Lineage、Subscription 与 Usage Evidence，但不得把 downstream lineage 自动推断为真实 Consumer，也不得把 observed Usage 自动生成技术 lineage edge。

## J5. 从敏感数据到安全消费

~~~text
Metadata Discovery
 -> Classification
 -> Security Policy
 -> Consumption Discovery
 -> Access Decision
 -> Query / Invoke
 -> Mask / Deny when supported
 -> Usage / Audit Evidence
~~~

成功结果：安全策略真实进入消费链路且可审计；FORBIDDEN 与 provider UNAVAILABLE 不互相伪装。

## J6. 从数据问题到 AI 证据回答

~~~text
User Question
 -> Semantic / Metric discovery
 -> governed data discovery
 -> Structured Query
 -> Evidence
 -> Analysis
 -> Answer / Report
~~~

成功结果：Agent 答案建立在平台数据契约和 Evidence 上，而不是自由 SQL 或不可验证文本。

Agent 可以在未来复用 Data Product / ConsumerRef / Usage Evidence contract，但 Agent 产品本身不是 Phase 4 的交付范围。

## Journey 规则

每条 Journey 都要有：

- 入口
- 产出
- 跨域回链
- 错误/阻断态
- 权限和 Project Space
- E2E acceptance

只增加模块局部功能、却不改善 Journey 的 Feature 应降低优先级。
