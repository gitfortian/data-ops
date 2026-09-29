# Metric Domain

## 核心概念

### 指标（Metric）

面向业务的度量，回答"业务看什么"。三种类型：

| 类型 | 语义 | 示例 |
| --- | --- | --- |
| `ATOMIC` | 原子指标，直接聚合 | GMV = SUM(order_amount) |
| `DERIVED` | 派生指标，原子 + 维度限定 | 支付GMV = SUM(order_amount) WHERE pay_status=1 |
| `COMPOSITE` | 复合指标，多指标运算 | 转化率 = 订单量 / UV |

### 不变量

1. **编码唯一**：`(project_id, metric_code)` 唯一，DB 唯一键兜底；编码创建后不可改。
2. **编码自动生成**：从名称自动生成（如 `GMV`→`gmv`），用户可改，创建后不可改。
3. **项目空间归属**：全部业务行带 `project_id`，只取服务端可信上下文（`CurrentProject`），不建物理外键。
4. **删除 = 物理删除**：指标删除前校验下游引用；存在 Definition Validation 或 Publication ledger 时禁止物理删除，保留可追溯证据。
5. **停用是软路径**：被引用的指标应先停用；停用被引用指标时给出阻断提示。
6. **口径引用**：`caliber_id` 引用 semantic 口径标准（松散 ID），`cal_rule` 从标准带出（只读）；口径未覆盖时 `cal_rule` 手填。
7. **血缘登记自动写入**：`metric_dependency` 由主表变更时自动写入（服务层保证一致性），不手工维护。
8. **复合指标组成**：`metric_composition` 记录子指标引用和运算表达式，供影响分析追溯。
9. **复合快照**：immutable `MetricVersion` 固化组成 token 及每个引用的精确子指标版本；Validation 不读取可变组成表补写历史事实。

### Definition Validation

Validation 绑定精确的 immutable `MetricVersion` 和 snapshot digest，并追加保存问题、执行主体、provider 覆盖状态与时间。结果使用 `PASSED` / `FAILED` / `NOT_APPLICABLE`；provider 覆盖单独使用 `READY` / `UNAVAILABLE` / `FORBIDDEN`。引用已确认删除或定义不完整属于 `FAILED`；provider 不可读时属于 `NOT_APPLICABLE`，不能伪装成通过或对象删除。

### Published Metric Contract

显式发布账本记录被发布的 `MetricVersion`、digest 和 publication-time gate evidence；active publication 是当前生效版本指针。创建/编辑 Draft 不自动发布，不移动 active pointer。Withdraw 追加事件并清除 active pointer，历史账本继续保留。

### 标签（Tag）

运营标记（如"核心指标""一级指标"），平铺无层级。业务域组织通过 `metric.domain_id` 引用 semantic 业务域，不重复建分类树。

### 对外引用契约（消费方视角）

- 消费方（dataset/dashboard）通过 Metric SPI 保存引用；新 governed reference 绑定 active Published Metric 的精确版本。历史仅有 metricId 的记录保留并显式表现为版本未知；展示名经 SPI 批量解析。
- 消费方**不解析**指标内部结构、不 join 本模块表。
- Reference Usage 只证明下游保存了引用；Observed Usage 由 Consumption owning domain 提供，二者不能合并。
