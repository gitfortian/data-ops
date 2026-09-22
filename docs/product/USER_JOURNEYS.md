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

~~~text
Development / Workflow
 -> governed consumption contract
 -> Analysis / Dashboard / API / Agent / downstream
~~~

成功结果：生产侧和消费侧通过稳定契约连接，不由每个消费产品重复定义数据访问和业务口径。

具体默认契约由 Product Decision 决定。

## J4. 从发现问题到定位影响

~~~text
Runtime / Quality Alert
 -> governed object
 -> Lineage
 -> Upstream / Downstream
 -> Owner
 -> Rerun / Fix / Notify
~~~

成功结果：用户从异常直接定位来源、影响范围和负责人。

## J5. 从敏感数据到安全消费

~~~text
Metadata Discovery
 -> Classification
 -> Security Policy
 -> Consumption Query
 -> Mask / Deny
 -> Audit
~~~

成功结果：安全策略真实进入消费链路且可审计。

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

## Journey 规则

每条 Journey 都要有：

- 入口
- 产出
- 跨域回链
- 错误/阻断态
- 权限和 Project Space
- E2E acceptance

只增加模块局部功能、却不改善 Journey 的 Feature 应降低优先级。
