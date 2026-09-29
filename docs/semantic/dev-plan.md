# 业务语义中心开发计划与跟踪（M4 语义主线）

> 语义主线独立跟踪:数据标准/业务域/业务过程/标准字段/分层/推荐/反馈(30–42)。
> 下游建模消费(43–48:派生建模/血缘/影响分析/主线/建模助手)在 [数仓建模开发计划与跟踪](../model/dev-plan.md) 跟踪。
> 整合方案:[m4-integration.md](./m4-integration.md)。
> Tickets 目录:[./issues/](./issues/)(一个 ticket 一个文件,编号即依赖拓扑顺序)。
> 状态:2026-09-16 自 `docs/model/dev-plan.md` 拆出(语义相关部分独立成文)。

---

## 0. 硬性开发约束(不可打破)

> 与 [数仓建模开发计划与跟踪](../model/dev-plan.md) 第 0 节一致,本节为语义主线适用的硬性要求,与任何交付进度冲突时以约束为准。

1. **契约先行(Contract-First)**:每个 `data-ops-business-*` 模块根目录维护契约文件集(README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW);语义相关 ticket 开工第一步更新 `data-ops-business-semantic/` 契约文件集(及涉及的其他模块:modeling/datasource/agent),契约 diff 先于代码 diff 被审阅。
2. **前端契约文件只读**:`data-ops-ui/` 下所有 `.md` 只阅读遵守、绝不修改;路由 menuCode 过契约测试;与前端契约冲突时上报,由契约维护方更新。
3. **项目全局规范强制适用**:`CODE_STYLE.md`、`data-ops-ui/FRONTEND_CODE_STYLE.md`、`docs/architecture/PROJECT_SCOPE.md`(project_id 只取服务端可信上下文、不建物理外键)、`docs/home-overview-contract.md`、菜单授权契约。
4. **交互原则(2026-09-17)**:`docs/INTERACTION_PRINCIPLES.md` —— 能默认就默认,能选择就不填写,能引用就不重复,能自动就手动;表单设计逐字段自问四句话。
5. **数据库迁移**:semantic 自建 `db/migration/yak-semantic`(V1 起编);菜单注册追加 yak-security 时 semantic 取 ≥V2019。

## 1. 里程碑

| 里程碑 | 阶段 | Tickets | 出口判据(演示场景) |
| --- | --- | --- | --- |
| M4 业务语义主线 | P0.5/P1 | 30~47 | 标准→业务过程→派生建模→映射/血缘→资产→反哺的完整业务闭环;30–42 在本文件跟踪,43–48 在建模侧跟踪 |

## 2. 状态总表(30–42)

> 状态流转:`ready-for-agent`(可开工)→ `in-progress`(进行中)→ `in-review`(待评审)→ `done`(完成)。
> 下游建模消费(43 字段分层映射 / 44 派生建模 / 45 血缘 / 46 影响分析 / 47 主线 / 48 建模助手):跟踪于 [../model/dev-plan.md](../model/dev-plan.md),其语义依赖引用本目录 issues。

| 编号 | Ticket | 阶段 | 阻塞于 | 状态 | 负责人 | 模块 |
| --- | --- | --- | --- | --- | --- | --- |
| 30 | [semantic 模块骨架+标准表结构(含 05 引用预留)](./issues/30-standard-tables.md) | P0.5 | 无 | in-review | | semantic(+modeling V8) |
| 31 | [预置标准(骨架+项目级初始化)](./issues/31-preset-standards.md) | P0.5 | 30 | in-review | | semantic |
| 32 | [标准管理界面(六类全量)](./issues/32-standard-management.md) | P0.5 | 31 | in-review | | semantic |
| 33 | [业务域管理](./issues/33-business-domain.md) | P0.5 | 30 | in-review | | semantic |
| 34 | [业务过程管理](./issues/34-business-process.md) | P0.5 | 33 | in-review | | semantic |
| 35 | [标准字段集(全局字段库+过程引用)](./issues/35-standard-field-library.md) | P0.5 | 34 | in-review | | semantic |
| 36 | [业务过程关联源表](./issues/36-process-source-binding.md) | P0.5 | 34 | in-review | | semantic(+datasource) |
| 37 | [数仓分层管理](./issues/37-layer-management.md) | P0.5 | 31 | in-review | | semantic(+datasource) |
| 38 | [标准自动套用(ODS)](./issues/38-standard-apply-ods.md) | P0.5 | 30, 31, 08, 37 | in-review | | modeling 消费 |
| 40 | [沉淀为标准(双入口)](./issues/40-capture-to-standard.md) | P0.5 | 32, 08 | in-review | | 跨模块 |
| 39 | [标准自动套用(DWD)](./issues/39-standard-apply-dwd.md) | P1 | 30, 31, 05 | in-review | | modeling 消费 |
| 41 | [标准推荐(可升级 agent)](./issues/41-standard-recommendation.md) | P1 | 32, 40 | in-review | | 跨模块 |
| 42 | [标准引用统计与反哺(推送式)](./issues/42-standard-feedback.md) | P1 | 32, 40 | in-review | | 跨模块 |

## 3. 关键决策

| # | 决策 | 来源 |
| --- | --- | --- |
| A7 | M4 语义主线按 [m4-integration.md](./m4-integration.md) 整合:业务过程字段集是逻辑建模的语义来源(决策 A);映射是派生的自动记录(决策 D);预置只做骨架、沉淀为主力(决策 B);界面随消费者开放(决策 C,2026-09-14 修正:六类标准管理界面全量开放) | M4 评审(2026-09-10) |
| A8 | semantic 全部业务表项目级(project_id,PROJECT_REQUIRED,与平台一致);"全局规范层"= 模块级全局;预置 = 平台模板表 + 幂等项目初始化(31) | 模块拆分评审(2026-09-14) |
| A9 | 新建 data-ops-business-semantic(决策 E):依赖仅 modeling→semantic 单向;跨模块数据只存松散 ID(无物理外键,展示名经 SPI 解析);42 推送式上报;46/47 后端归 modeling,semantic 侧前端跳转 | 模块拆分评审(2026-09-14) |

## 4. 跟踪约定

1. 每个 ticket 一个文件,包含:What to build(用户视角端到端行为)、Blocked by、硬性约束、验收清单。
2. 领取 ticket 后**先读第 0 节硬性约束与涉及模块的契约文件**,再开工;完成一项验收就勾选一项。
3. 状态与负责人变更同步更新第 2 节状态总表;ticket 文件的 Status 字段与总表保持一致。
4. 建模侧(43–48)引用语义 ticket 时,相对路径为 `../../semantic/issues/<file>.md`。
5. 如后续迁移到 GitHub Issues:按本表逐条建 issue,以 issue 链接替换"阻塞于",并在本表回填链接。
