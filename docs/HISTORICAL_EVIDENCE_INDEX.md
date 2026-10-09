# 历史交付与审核证据地图

> Class: Evidence / Historical · **本文件是导航，不具有需求、架构决策或线上验收权威**。

仓库分散保存大量带时间戳的 QA、UI/Architecture Review、旧阶段交接材料。为保证可追溯性，本批只增添索引，不搬迁、改写、合并、删除旧文件。

| 证据族 | 可直接进入的索引 | 解释 |
|---|---|---|
| 10 月 1 日测试 | [20261001](20261001/README.md) | 资产与治理、标准/指标/建模测试快照及截图 |
| 10 月 3 日检查 | [20261003](20261003/README.md) | 00–08 分域检查、环境与核实修复 |
| 10 月 4 日审核 | [20261004](20261004/README.md) | 产品审核与迭代设想，并非批准的任务 |
| 前端 Review | [frontend-review](frontend-review/README.md) | 10 月 2–3 日 UI 评审、覆盖材料及诊断附件 |
| 架构 Review | [architecture-review](architecture-review/README.md) | 2026-10-03 架构与迁移盘点的静态资料 |
| 主题 Review / 核销 | [reviews](reviews/README.md) | P0/P1、资产、消费等特定时点证据 |
| V1 历史资料 | [v1](v1/README.md) | 初版领域流程、模块盘点及交互审查 |
| QA 与测试 | [test](test/README.md) | 浏览器 QA 和语义标准报告 |

## 历史阶段里程碑（根目录文档，无移动）

- [Phase 7 最终验收记录](phase7-final-acceptance.md)
- [Phase 7 真实环境证据矩阵](phase7-runtime-evidence-matrix.md)
- [Phase 8 运行时测试矩阵](phase8-runtime-test-matrix.md)
- [Phase 9.2 模式发现实现记录](phase9.2-pr2-pattern-discovery-engine.md)

## 当前权威从哪里读

1. 当前批准的 Product Truth、决策、Feature 状态：见 [Product baseline](product/README.md) 和 [Document Governance](product/DOCUMENT_GOVERNANCE.md)。
2. 各领域业务不变量及架构/API 边界：见业务模块中的 `DOMAIN.md / REQUIREMENTS.md / ARCHITECTURE.md / DEPENDENCIES.md`。
3. 构建、发行、Flyway 迁移与恢复：见 [Release](release/RELEASING.md) 和 [CI 验证入口](engineering/ci-impact-validation.md)。
4. 整洁性治理已经合并的阶段证据：见 [#345 P1 收口](engineering/code-cleanliness-p1-closeout.md) 与 [P2 综合审计](engineering/p2-backend-cleanliness-audit.md)。

**证据未自动核销：**某项旧测试的结果、某个 gap 的存在、某份计划的“下一步”，都不能自动转化为 2026-10-09 的缺陷状态或新的开发任务。
