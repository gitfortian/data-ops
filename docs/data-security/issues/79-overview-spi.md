# Ticket 79：安全总览与对外 SPI 闭环

**目标**：安全态势入口 + 对外三个 SPI，让数据服务/预览/建模消费安全能力，形成业务闭环。

**总览**（`SecurityOverviewService`，服务端聚合）：定级对象数、各等级分布、待确认候选数、策略数、脱敏策略命中数、合规最近汇总、近期拒绝次数。全部走 repository 聚合查询，不内存统计。

**对外 SPI**（`io.yak.ops.business.security.api`，impl 在 `application/spi` `@Component`）：
- `SecurityClassificationQueryApi.find(objectKey)` → 等级/分类视图。
- `SecurityMaskingApi.resolve(objectKey)` + `mask(value,algoCode,params)`。
- `SecurityAccessDecisionApi.decide(actor,roles,objectKey,action)`。

**消费方接线（文档 + 契约，落地在下期 UI/联调）**：data-service 出数前 `decide`→`resolve`→`mask` 并 `record` 访问流水；预览跳转复用同一 SPI。

**错误码**：45090~45099。
**验收**：SPI impl 委托单测；overview 聚合委托 repository 单测。
