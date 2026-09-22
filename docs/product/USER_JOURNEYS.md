# Core User Journeys

后续 Feature 应至少服务以下某一条 Journey，或经过评审新增新的 Journey。

## J1. 从外部数据到可信数据资产

```text
DataSource
 -> Sync / Metadata Harvest
 -> Semantic / Model
 -> Development
 -> Workflow
 -> Quality / Security
 -> Asset
```

成功结果：用户能够得到可追溯、可运行、可治理的数据资产。

## J2. 从业务定义到统一指标

```text
Business Domain / Process
 -> Standard / Caliber
 -> Model
 -> Metric
 -> Validation
 -> Dataset / Dashboard / API
 -> Usage / Impact
```

成功结果：指标不是定义台账，而是被真实消费的统一业务口径。

## J3. 从数据生产到 Dataset 消费

```text
Development / Workflow
 -> Dataset
 -> Analysis / Dashboard
 -> API
 -> Agent
```

成功结果：生产侧和消费侧通过稳定 Dataset 契约连接，而不是各自重复写 SQL 和业务逻辑。

## J4. 从发现问题到定位影响

```text
Runtime / Quality Alert
 -> Asset
 -> Lineage
 -> Upstream / Downstream
 -> Owner
 -> Rerun / Fix / Notify
```

成功结果：用户可以从异常直接定位来源、影响范围和负责人，而不是跨多个后台人工搜索。

## J5. 从敏感数据到安全消费

```text
Metadata Discovery
 -> Classification
 -> Security Policy
 -> Dataset / API / Agent Query
 -> Mask / Deny
 -> Audit
```

成功结果：安全策略进入实际消费链路，并且结果可审计。

## J6. 从数据问题到 AI 证据回答

```text
User Question
 -> Semantic / Metric discovery
 -> Dataset discovery
 -> Structured Query
 -> Evidence
 -> Analysis
 -> Answer / Report
```

成功结果：Agent 的答案建立在平台数据契约与 Evidence 上，而不是自由 SQL 和不可验证文本。

## Journey 规则

每个 Journey 必须具备：

- 明确入口；
- 明确产出；
- 跨模块回链；
- 错误与阻断态；
- 权限和 Project Space；
- E2E acceptance。

一个 Feature 如果只让某一模块“功能更多”，但不改善 Journey，应降低优先级。
