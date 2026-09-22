# 数据生命周期（TTL）—— 开发计划

## 0. 硬性开发约束（不可打破）

> 与 [model dev-plan.md《硬性开发约束》](../model/dev-plan.md) 一致，本节为生命周期模块适用的硬性要求；与任何交付进度冲突时以约束为准。

1. **契约先行**：`yak-ops-business-lifecycle` 根目录维护契约文件集（README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW）；每个 ticket 开工第一步更新契约文件集，契约 diff 先于代码 diff。
2. **前端契约文件只读**：`yak-ops-ui/` 下所有 `.md` 绝不修改；新菜单 menuCode 需同步登记 `src/constants/securityMenuCodes.ts` 并过 `navigationMenuContract.test.ts`。
3. **项目全局规范**：`CODE_STYLE.md`、`yak-ops-ui/FRONTEND_CODE_STYLE.md`、`docs/architecture/PROJECT_SCOPE.md`（project_id 只取服务端可信上下文、不建物理外键）、`docs/home-overview-contract.md`、菜单授权契约。
4. **交互原则**：`docs/INTERACTION_PRINCIPLES.md`——能默认就默认（粒度=日、end=3、prefix=p）、能带出就不填（三段值随分层预填、编码自动生成）、危险操作必过预览确认。
5. **数据库迁移**：自持 `db/migration/yak-lifecycle` V1 起编，历史表 `flyway_schema_history_lifecycle`；菜单注册进 yak-security 链，取 **V2031**。
6. **错误码段**：47001~47099。权限码 `data-lifecycle:read/create/update/delete`。
7. **执行边界**：一切对存储的 DDL/查询仅经 `DataSourceExecutionProvider`；lifecycle 是平台唯一 TTL DDL 执行出口，modeling"永不执行 DDL"（D3）保持不变。
8. **SPI 纪律**：对 modeling 的新读取需求以**新增** `ModelTtlQueryApi` 实现，不改动既有 `ModelQueryApi` 签名。

## 1. 里程碑

| 里程碑 | 内容 | Ticket |
|---|---|---|
| M1 底座+策略 | 模块可启动、菜单可见；策略/绑定/生成 API 可用 | 80~83 |
| M2 下发通道 | 预览→确认→下发→重试→漂移闭环 | 84~86 |
| M3 可观测 | 监控与存储 API、快照任务 | 87 |
| M4 界面 | 策略页、模型 Tab、向导、监控、存储页 | 88~89 |

## 2. 状态总表

| 编号 | Ticket | 阶段 | 状态 |
|---|---|---|---|
| 80 | 生命周期模块骨架 + 菜单权限 | P1 | ready-for-agent |
| 81 | TTL 策略 CRUD + 分层默认策略 | P1 | ready-for-agent |
| 82 | 模型绑定与继承解析 + modeling SPI 扩展 | P1 | ready-for-agent |
| 83 | TTL 语句生成器（Doris/Paimon） | P1 | ready-for-agent |
| 84 | TTL 分区预览 | P1 | ready-for-agent |
| 85 | 策略下发 + 下发流水 + 审计 | P1 | ready-for-agent |
| 86 | 失败重试定时 + 漂移检测 | P1 | ready-for-agent |
| 87 | TTL 监控 + 存储统计 API（快照任务） | P2 | ready-for-agent |
| 88 | 前端·策略管理页 | P1 | backlog |
| 89 | 前端·模型 Tab + 下发向导 + 监控/存储页 | P2 | backlog |

## 3. 依赖图

```
80 ──▶ 81 ──▶ 82 ──▶ 83 ─┐
                          ├─▶ 84 ─▶ 85 ─▶ 86 ─▶ 87
                    (生成为预览/下发前置)          │
                              88 ◀──(81/82/83 API) │
                              89 ◀────────────────┘
```

## 4. 关键口径与假设

- 三段值统一以"天"存储；MONTH/YEAR 粒度下 start 按"周期个数"换算（见 design §3.2）。
- 下发确认 token：preview 接口返回带 5 分钟时效的签名 token，dispatch 必带（防盲发）。
- 环境为真实 Doris 数据源时 E2E 才做真实 ALTER 验证；无 Doris 环境时以 FAILED/降级路径 + 单测为准，并在验证记录中如实声明。

## 5. 跟踪约定

状态：`backlog → ready-for-agent → in-progress → in-review → done`；完成后在票内追加"验证记录"。
