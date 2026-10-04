# Phase 7 收尾进度与验收证据

日期：2026-09-30
基线：`main` / `764dd93c`（本任务工作分支从该提交开始）
范围：Phase 7 issue 校准、F-001 Asset Governance Hub 实施与验收状态

本文记录当前证据和待验收项，不替代 ACCEPTED Product Decision 或 APPROVED Feature Spec，也不将未获批准的需求变成实现指令。

2026-10-04 追加：[R1 首批实施记录](golden-sample/implementation-20261004.md)补充物理表登录态 API、八分区与 Asset 项目隔离证据。下文保留 2026-09-30 历史审计；新增证据未覆盖全部七场景，不能据此关闭 #185 或宣称 PD-001 DONE。

## 当前结论

Phase 7 尚未完成总体验收。`main` 已包含 Asset 统一发现入口、Metadata 专业技术目录、Asset Section 及多个治理域只读摘要；完整的物理表、Model、Metric、Dataset 登录态用户路径，以及权限/故障隔离场景尚无一套统一、当前的 E2E 验收记录。

现行产品依据：

- `docs/product/decisions/PD-001-asset-governance-hub.md`：Asset 是普通用户的默认发现与治理入口；Metadata 是技术元数据能力。
- `docs/product/features/F-001-asset-governance-hub.md`：定义统一 Asset 用户旅程和接受条件。
- `docs/product/features/F-001-A-asset-section-contract.md`：定义分区事实归属、状态及适用矩阵。

## 实现范围核对

| 能力 | 当前实现证据 | 仍需验收或决策 |
|---|---|---|
| Asset 目录与详情 | `AssetController` 独立 Section API；Asset Detail 按分区加载；Metadata 实体可从 Asset Catalog 进入 | 物理表、Model、Metric、Dataset 的登录态逐项验收；核对 Owner、状态、source identity 和回链 |
| Metadata 技术工作台 | `/api/v1/metadata/overview`、`/search`、实体详情/变更读取及 Asset Catalog Metadata 视图 | 项目权限、空结果/异常表达、Metadata 与 Asset 往返路径的运行态证据 |
| Quality | Quality-owned Asset Section 提供纳管/监控/最近执行摘要与 Quality 页面动作；规则模板、监控报告和执行工作区已有 | 真实样本页面验收；“质量评分”和“质量问题处理生命周期”需要单独产品契约，不从旧 issue 直接派生状态机 |
| Lineage | 有界图查询、字段节点类型、关系证据、上下游与影响展示 | Asset/Metric 入口和字段级实际样本的端到端证据；不得将图关系推断为真实消费用户 |
| Metric / Semantic | Metric 详情有定义、标准关联、版本、血缘与引用使用能力 | 通过业务术语查找指标并回到 Asset 的完整用户路径证据 |
| Lifecycle | Lifecycle 页面有策略、模型 TTL、下发/重试、监控和存储统计；Asset Section 读取 Model TTL | Model Asset 的登录态验收；归档管理及扩展对象生命周期需单独明确产品范围 |
| Security | Asset Section 展示分类、适用 READ 策略配置摘要、脱敏匹配状态及专业域动作 | 逐项核验权限、空事实和故障表达。对象级合规结果仍是 `UNAVAILABLE`；若要求对象级合规事实，需先明确 Security 的稳定对象映射 |
| Provider 覆盖诊断 | Asset source Provider 对账状态和 Section 查询日志可提供部分运行证据 | F-001 对诊断呈现位置仍有未决问题；本记录不新增管理页面或产品入口 |

## 本次代码修正

Usage 的结构依赖子项成功查询 Lineage、但目标资产尚未登记时，返回 `EMPTY`、零条已知下游引用及明确原因；查询异常继续返回 `UNAVAILABLE`。这与 Lineage Section 对“未登记节点”和“查询失败”的区分一致，不改变 Lineage Truth。

对应回归测试：`AssetDiscoverServiceTest.usageDistinguishesMissingLineageRegistrationFromQueryFailure`。在本工作分支执行 `mvnw -pl data-ops-business/data-ops-business-asset -am -Dtest=AssetDiscoverServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，结果为 `BUILD SUCCESS`，20 项通过、0 失败；该修正尚未进入 `main`。

跨域定向回归另执行 Metadata、Lineage、Metric、Asset 的 5 个测试类，共 31 项通过、0 失败；前端 Asset Section 与 Lineage 布局 2 个 Jest suites 共 8 项通过。以上均为单元/组件级测试，不替代后文登录态 E2E。

## Issue 校准记录

- [#193 实施路线规划](https://github.com/gitfortian/data-ops/issues/193)：已按 PD-001 更新计划顺序，并因拆分完成关闭。
- [#201 Metadata Profile](https://github.com/gitfortian/data-ops/issues/201)、[#204 Metadata Detail UI](https://github.com/gitfortian/data-ops/issues/204)、[#205 Metadata Governance SPI](https://github.com/gitfortian/data-ops/issues/205)、[#206 单体 Asset Profile](https://github.com/gitfortian/data-ops/issues/206)：原实现方案与 F-001-A 分区契约冲突，已标记为 `not planned` 并关闭；这不代表父 Feature 或 Phase 7 已验收。
- [#188 Metadata Feature](https://github.com/gitfortian/data-ops/issues/188)、[#194 Metadata 实施任务](https://github.com/gitfortian/data-ops/issues/194)：已调整为 Metadata 专业技术工作台，并保持 open，等待项目隔离、页面路径和回链验收。
- 当前 `phase7` 标签为 13 open / 13 closed（收尾前为 18 open / 8 closed）。继续 open 的完整列表：[#185](https://github.com/gitfortian/data-ops/issues/185)、[#186](https://github.com/gitfortian/data-ops/issues/186)、[#187](https://github.com/gitfortian/data-ops/issues/187)、[#188](https://github.com/gitfortian/data-ops/issues/188)、[#189](https://github.com/gitfortian/data-ops/issues/189)、[#191](https://github.com/gitfortian/data-ops/issues/191)、[#192](https://github.com/gitfortian/data-ops/issues/192)、[#194](https://github.com/gitfortian/data-ops/issues/194)、[#195](https://github.com/gitfortian/data-ops/issues/195)、[#196](https://github.com/gitfortian/data-ops/issues/196)、[#199](https://github.com/gitfortian/data-ops/issues/199)、[#200](https://github.com/gitfortian/data-ops/issues/200)、[#209](https://github.com/gitfortian/data-ops/issues/209)。#185 是最终总验收；Feature、实施父任务和健康度范围仍各自有未验收结果。
- 目前 closed：[#190](https://github.com/gitfortian/data-ops/issues/190)、[#193](https://github.com/gitfortian/data-ops/issues/193)、[#197](https://github.com/gitfortian/data-ops/issues/197)、[#198](https://github.com/gitfortian/data-ops/issues/198)、[#201](https://github.com/gitfortian/data-ops/issues/201)、[#202](https://github.com/gitfortian/data-ops/issues/202)、[#203](https://github.com/gitfortian/data-ops/issues/203)、[#204](https://github.com/gitfortian/data-ops/issues/204)、[#205](https://github.com/gitfortian/data-ops/issues/205)、[#206](https://github.com/gitfortian/data-ops/issues/206)、[#207](https://github.com/gitfortian/data-ops/issues/207)、[#208](https://github.com/gitfortian/data-ops/issues/208)、[#210](https://github.com/gitfortian/data-ops/issues/210)。其中 #201/#204/#205/#206 是按现行产品契约标记 `not_planned`，不是已实现；#193 是路线拆分规划完成；其他关闭项在下方附源码/测试核验与仍缺的整体验收范围。

已关闭 issue 的复核结论：#190、#197、#198、#202、#203、#207、#208、#210 在 main 对应代码中可找到 Metric/Semantic、Lineage、Metadata Search、Asset Catalog、Usage 和 Asset Detail 的页面、API 或领域测试；这些 issue 当前没有 GitHub 验收评论或链接到逐条用户路径的证据。它们保留已关闭状态，不因父 Feature/#185 未完成而机械重开；本地没有可用的已认证后端 E2E 环境，所以上述源码证据不等同于运行态验收，跨对象类型和回链的最终证据仍由 #185 收口。

## E2E 验收清单

以下场景仍需在可登录的前后端环境中执行，并记录环境、测试账号权限、样本身份、脱敏 API 响应、页面截图和默认分支提交：

本机 `localhost:8000` 有 UI 开发服务响应，但后端 `localhost:8080` 未监听；当前没有可执行已认证端到端场景的完整运行环境。

1. Physical Table：从 Asset Catalog 找到表，查看技术元数据、Quality、Security、Lineage、Usage 和 Governance；进入适用的专业页并返回原 Asset。
2. Model：确认 Quality/Physical Metadata 为 `NOT_APPLICABLE`，Lifecycle 按真实策略返回 `OK`、`EMPTY` 或 `UNAVAILABLE`。
3. Metric：查看定义、标准、版本、Lineage 和 Usage；核对物理表专属分区为 `NOT_APPLICABLE`。
4. Dataset：分别识别页面活动、结构引用、声明订阅和实际成功消费，检查来源域与时间范围。
5. 权限：受限用户不能读取敏感摘要、证据或动作；权限拒绝不得伪装为空结果。
6. 故障隔离：让单个依赖不可用，验证只有对应 Section 为 `UNAVAILABLE`，其余详情仍可使用；恢复后复测。
7. Project 隔离：相同源对象在不同 Project 的结果不能互相泄露。

## 关闭门槛

PR #219 虽已合并，其记录写明当时未运行测试；Product Guard 又因缺少 Change Type 声明失败。因此它不能作为 Phase 7 的 CI/验收通过证据，后续集成仍需满足仓库交付门槛。

细任务需有 `main` 提交与对应验收证据；Feature 需满足自己的产品结果；最后由 #185 汇总七项能力及端到端证据。`PD-001 Implementation` 当前为 `PARTIAL`，只有所有已接受条件都有证据后才能更新为 `DONE`。未经基线验证的成功指标继续保持定性信号，不填造数字。
