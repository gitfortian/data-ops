# F-001-B — Asset Section 技术设计

Status: IMPLEMENTING  
状态说明：技术设计依据 F-001-A 已落地，首个 Quality 分区正在实施  
Feature ID: F-001-B  
父 Feature：F-001 — 数据资产治理枢纽收敛  
产品契约：F-001-A — 资产详情分区契约  
日期：2026-09-23

## 1. 用户问题与目标

数据使用者从 Asset 详情判断物理表是否纳入质量管理、最近检查是否通过以及是否存在问题时，需要可信摘要和可继续处理的 Quality 入口。Asset 聚合质量事实，但 Quality 是规则、监控、执行和结果的唯一 Truth Owner。

本设计定义 Asset Section 的稳定读取边界，并以 Quality Summary 为首个端到端实现。目标用户旅程是：在 Asset 找到物理表 → 查看质量摘要 → 进入对应 Monitor / Execution 专业页处理 → 返回原 Asset。

## 2. 边界与复用

- Truth Owner：Quality 拥有 TableAsset、Monitor、Execution 和 RuleExecution；Metadata 拥有物理坐标；Asset 拥有 Asset identity 与 Section 聚合响应。
- Producer / Consumer：Quality read-side 生产对象级摘要；Asset application 消费；Asset Detail UI 消费 Asset Section endpoint。
- 复用：现有 `AssetProvider` 获取 Metadata 坐标、`QualityMonitorReader` / `QualityExecutionReader`、`SectionContract` 五态、Quality 执行工作区和既有独立 Section endpoint。
- 不新增资产表字段、Quality 镜像表、Quality 新状态机、全局 Usage 真相、导航入口或新的详情页。
- 质量对象键由 `dataSourceId + database + schema + table` 构成；不以展示名、Asset id 或大小写敏感字符串作为唯一关联。

## 3. Query 契约

Quality 通过既有 `yak-ops-spi` `SectionProvider` 提供只读对象级 Section，Asset 将 Metadata Provider 的物理坐标放入 `SectionContext.attributes`。Quality 按当前 `CurrentProject` 隔离查询。输出包含：

- 是否已登记为 Quality TableAsset；
- Monitor 总数与启用数；
- 最近一次 Execution 的 executionNo、生命周期状态、检查结论、failed/error rule 数及完成/排队时间；
- Quality 专业页目标（Monitor 列表或最新 Execution 工作区）。

语义规则：

- 目标未登记：返回存在性明确的摘要，Asset 映射为 `EMPTY`；
- 已登记但没有 Monitor：返回 `EMPTY`，保留“已纳管”信息；
- 有 Monitor 但没有执行：`OK`，执行状态表达为 `NOT_RUN`，问题数为 0；
- 最近执行仍 WAITING / RUNNING：如实返回生命周期状态和已有证据；
- 无法解析 Metadata 坐标、依赖未装配或查询失败：`UNAVAILABLE`，不解释成未纳管；
- 非物理表：`NOT_APPLICABLE`，并且不调用 Quality。

`lastIssueCount = failedRules + errorRules`，含义是最近一次执行中失败或错误的规则数，不表示告警事件数。执行仍未结束时只展示已持久化值和其生命周期状态。

Quality API 不返回 PO、Mapper、DAO 类型或 HTTP controller VO；实现使用 Quality owner 内部 narrow Reader/Repository corridor，所有查询受 `CurrentProject` 约束。

## 4. Asset 适配与 Section 响应

Asset 从 Metadata `AssetProvider.refresh(sourceId)` 读取 `dataSourceId/databaseName/schemaName/tableName`，由 Quality `SectionProvider` 查询并返回 Section 五态。Section `summary` 至少包括 `registered`、`monitorCount`、`enabledMonitorCount`、`latestExecution`（可空的显式子对象）；`actions` 携带可由前端路由消费的 Quality Monitor / Execution 页面目标及 identity；Asset 不拼装或持有 Quality 事实。

沿用 `GET /api/v1/assets/{id}/sections/{sectionType}` 渐进加载端点。完整详情 endpoint 中的 Quality projection 也调用同一 `quality(po)` adapter，避免两种读取语义分叉。Quality 摘要同时要求现有 `quality:monitor:read` 与 `quality:execution:read` 权限。

Section endpoint 必须保证本体已成功读取后，单个 Section 故障只降级自身。错误响应不泄漏 SQL、凭据或原始异常。

## 5. Package 与依赖走向

```text
Asset controller -> AssetDiscoverService -> SectionProvider (Quality-owned implementation)
                                      |-> Metadata AssetProvider (coordinates)
Quality SectionProvider -> Quality TableAsset / Monitor / Execution Readers -> repositories -> DAO
```

稳定跨域类型由 `yak-ops-spi` 的 `SectionProvider`、`SectionContext` 和 `SectionContract` 承载；Quality 实现放在 owning module 内。Asset 只注入 SPI，不直接依赖 Quality 内部 Reader、Repository、DAO 或 Mapper；Asset 对 endpoint 输出统一映射为自己的 `AssetSectionResult`。

## 6. 容错、身份与性能

- 先按 sourceType 判断适用性，再查权限，再进行跨域调用。
- Query 使用完整物理键和当前项目上下文，不做无界全量查询；最多读取匹配目标的 Quality TableAsset、Monitor 与一条最新 Execution 摘要。
- 数据库、Schema、表名的空值按 Quality 现有规范归一；表/Schema 比较沿用 source catalog 与 Quality 注册的 canonical identity，不进行模糊包含匹配。
- Metadata 坐标缺失为 UNAVAILABLE；Quality 明确无记录才为 EMPTY。
- 依赖对象缺失、超时、读取异常分别记录 Section status 与耗时；日志只包含 Asset id、Section type、异常类别，不包含数据摘要或原始 SQL。
- 不添加 Asset 缓存或二次持久化；如后续需要缓存，需单独定义可重建缓存及失效证据。

## 7. E2E 验收与证据

1. Metadata 物理表 + Quality 未登记 → Asset QUALITY 为 EMPTY；明确 `registered=false`。
2. 已登记但无 Monitor → EMPTY 且 `registered=true`。
3. 有启用 Monitor、无执行 → OK、`NOT_RUN`、Monitor 入口可定位。
4. 有最近成功 / 失败执行 → OK，状态、结论、failed/error 数、时间与 Quality 执行详情 identity 一致。
5. Metadata 坐标缺失 / Quality 读异常 → UNAVAILABLE；其它 Asset Sections 正常。
6. Model / Metric / Dataset → NOT_APPLICABLE，验证未调用 Quality Query。
7. 用户缺任一 Quality read 权限 → PERMISSION_DENIED，响应不含 Quality 摘要。
8. 多 Project 同一物理坐标 → 只返回当前 Project 的 Quality 事实。

交付证据：Quality Query contract/read-side 行为测试、Asset mapping 行为测试、Section controller contract、依赖方向守卫、Maven 模块编译与目标测试、至少一个使用真实已注册表和执行记录的本地/集成验收记录。测试应验证公开行为，不绑定私有实现。

## 8. 实施拆分

1. Quality 增加 typed object-level Summary Query 和受 Project scope 约束的实现；
2. Asset 接入该 contract 并完成五态映射、Quality 回链和权限行为；
3. Asset Detail 页面调用既有独立 Section endpoint 并呈现摘要及空态/错误态；
4. 完成上述契约与 E2E 证据后再将 F-001-B 标记为 SHIPPED。

本设计不授权扩张 Quality 到 Model / Metric / Dataset，也不改变导航或质量规则、监控与执行生命周期。
