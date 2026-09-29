# docs/semantic — 语义模块(数据标准 / 业务语义 / 数仓分层)规划文档

> 本目录服务于**独立模块 `data-ops-business-semantic`**(决策 E,2026-09-14 确认):
> 数据标准(命名/类型/码值/单位/口径/安全)、业务域/业务过程/标准字段集、业务过程↔源表关联、数仓分层配置。

## 与 docs/model 的关系

| 目录 | 管什么 |
| --- | --- |
| `docs/model/` | 数仓建模规划主线:需求(modeling-requirements)、建模开发计划与状态总表(dev-plan)、建模功能 ticket 01~29、43~48(issues/) |
| `docs/semantic/`(本目录) | 语义主线(M4):语义开发计划与状态总表(dev-plan)、语义 ticket 30~42(issues/)、M4 整合方案(m4-integration)、模块级设计与复审 |

语义相关 ticket 在 `docs/semantic/issues/` 下(30~37 semantic 主体、38/39 modeling 消费、40/41/42 跨模块),状态总表在 `docs/semantic/dev-plan.md` 第 2 节;建模消费侧 43~48 在 `docs/model/dev-plan.md` 跟踪,其语义依赖引用 `../../semantic/issues/`。

## 文档清单

| 文档 | 内容 |
| --- | --- |
| [dev-plan.md](./dev-plan.md) | 语义主线开发计划与状态总表(30~42)、关键决策 A7/A8/A9、硬性约束引用(与 model dev-plan 第 0 节一致) |
| [m4-integration.md](./m4-integration.md) | M4 语义主线整合方案:四项整合决策、闭环链路、范围修正 |
| [module-design.md](./module-design.md) | 模块设计:定位与边界、依赖规则、**SPI 契约面(方法级)**、数据归属与 scoping、接线清单(Maven/迁移/菜单/权限/错误码/前端/审计)、闭环链路、风险权衡。是 ticket 30 契约文件集的直接输入 |
| [issue-review-2026-09-14.md](./issue-review-2026-09-14.md) | 30~47 复审记录:走查结论、12 处缺口及补充去向、新增票 48 |
| [issues/](./issues/) | 语义 ticket(30~42):数据标准/预置/管理/业务域/业务过程/标准字段/源表关联/分层/套用/推荐/反馈 + new_issue 清单 |

## 决策索引

| 决策 | 内容 | 出处 |
| --- | --- | --- |
| A~D | 单一语义管道 / 冷启动反转 / 消费者驱动界面 / 映射=派生自动记录 | [m4-integration.md](./m4-integration.md) 第 2 节 |
| E | **semantic 独立成模块;依赖仅 modeling→semantic 单向;跨模块数据只存松散 ID;42 推送式上报;46/47 后端归 modeling** | 同上 第 8 节 |
