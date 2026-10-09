# P3 文档与工程秩序 · 综合批次验收记录

> Change Type: TECHNICAL · Product Behavior Changed: No · Class: Evidence / Review。
>
> 治理基线：P2 [#477](https://github.com/gitfortian/data-ops/pull/477) 已合并；本 PR 仅修复文档导航和工程命令，不修改业务接口、项目隔离、权限、SQL、Flyway、Framework Bean 或旧证据本体。

## 原始证据与动作

| 类型 | 经检查事实 | 本批实施 |
|---|---|---|
| Metric 模块入口 | `data-ops-business-metric/README.md` 链接 `../../docs/semantic/metrics/`，当前树并无该路径；存在 `docs/metric/issues/gap-backlog-2026-09.md` | 修复为真实历史问题入口，并补有效 PD-003 当前跨域合同；说明 Ticket 只是历史输入 |
| Framework 构建文档 | `data-ops-framework/README.md` 三处绝对 `D:\baize-works\...` Maven 可执行路径仅在单机存在 | 换为仓库内 `./mvnw -f data-ops-framework/pom.xml`；保留发布与混淆 profile 选项 |
| 对外 README | 中英文首页已有外部产品文档链接，但无内仓 Evidence/Engineering 导航链接 | 两种语言增设 `docs/README.md` 仓库内导航入口；不变更 Demo/外部 Website |
| 日期报告 | `docs/20261001` 7、`docs/20261003` 9、`docs/20261004` 1 篇顶层 Markdown 记录 | 每个目录建立对应索引，逐篇指向原始报告与附件位置 |
| Frontend / Architecture Review | 分别保留当时的分域评审、日志、JSON 证据 | 为各证据族新增主题索引，区分 Proposal 与实施授权 |
| Reviews / V1 / Test | 历史 Review 与 QA 资料目录分散、没有统一入口 | 新增主题索引；reviews 顶层篇目纳入全量链接守卫 |
| Governance | `docs/README.md` 已规定 Product / Evidence 权威层级 | 建立 `HISTORICAL_EVIDENCE_INDEX.md` 入口，不改变 `docs/product/decisions/**`、Feature 状态或原始验收结论 |

## 自动验收与保留清单

- `scripts/architecture/p3-document-entry-integrity.test.mjs` 自动遍历 Root README、全部 Business 顶层 README 和本批治理索引的本地 Markdown/HTML 链接，拒绝不存在的本地目标；外部 HTTP 和 anchor-only 不在范围。
- 守卫明确要求 3 个日期目录与 `docs/reviews` 的每份顶层 Markdown 都在本族 README 有链接，避免新增日期文档继续变成孤岛。
- 守卫要求 Framework README 不重新加入绝对个人 Maven 路径，Metric 当前入口不存在废弃路径，且所有新索引继续包含 Evidence/Historical 非权威提示。
- [`docs/product/DOCUMENT_GOVERNANCE.md`](../product/DOCUMENT_GOVERNANCE.md)、[Release](../release/RELEASING.md)、Flyway 历史文件、Spring/Mapper/Controller、`.zcode/plans` 历史保留原样。

## 未核销与变更限制

这批不推断所有历史 TODO 已消失，不修改 AI Agent 领域开发文档、不批量清空旧注释、不删除 SQL 和迁移版本；每份阶段评审的缺陷真实性由其 own domain 和真实环境证据单独判断。已建立入口与测试，不代表其他数百份历史材料都已作语义逐行审计。

**最终验收**：Product Guard、Architecture Checks 中的可执行 Node 合同、Impact planner 按需测试、Maintainer 人工合并；CI 未全通过前不得声明交付闭环。

## 与主分支 README 重写 PR #482 的并线记录（2026-10-09）

#481 的原始入口修正创建于 #482 合并之前。并线时确认 `main` 已通过 #482 重写中英文首页，且**两份新 README 已各自包含有效的 `docs/README.md` 与 `docs/product/README.md` 链接**。因此保留主分支的产品介绍、启动命令、仓库链接、安全提示和当前文档定位，不恢复本 PR 原始 README 的旧版页面内容、Demo/外部站点导航或旧命令示例。文档入口治理目标仍由新版主分支页面满足。

P3 的双语导航测试相应从“原版 README 必须包含旧外部文档域名”更新为“当前 README 必须包含内部文档权威入口和当前仓库 Issue 入口”。其余 #481 索引、Metric / Framework 文档修正及历史证据完整性守卫全部保留。这是与最新主分支的事实核对，不产生新的产品功能合同。
