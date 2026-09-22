# 数据生命周期（TTL）—— Ticket 清单

> 状态：v1.0（2026-09-19）
> 模块：`yak-ops-business-lifecycle`（新建）
> 编号接 data-security 的 79，从 **80** 开始；硬性约束见 [dev-plan.md](../dev-plan.md)

## 一、Ticket 总览

| 编号 | Ticket | 阶段 | 阻塞于 | 状态 | 模块 |
|---|---|---|---|---|---|
| 80 | 模块骨架 + 菜单权限 | P1 | — | ready-for-agent | lifecycle |
| 81 | TTL 策略 CRUD + 分层默认策略 | P1 | 80 | ready-for-agent | lifecycle |
| 82 | 模型绑定与继承解析 + modeling SPI | P1 | 80,81 | ready-for-agent | lifecycle+modeling |
| 83 | TTL 语句生成器（Doris/Paimon） | P1 | 82 | ready-for-agent | lifecycle |
| 84 | TTL 分区预览 | P1 | 83 | ready-for-agent | lifecycle |
| 85 | 策略下发 + 流水 + 审计 | P1 | 84 | ready-for-agent | lifecycle+datasource(复用) |
| 86 | 失败重试定时 + 漂移检测 | P1 | 85 | ready-for-agent | lifecycle |
| 87 | TTL 监控 + 存储统计 API | P2 | 85 | ready-for-agent | lifecycle |
| 88 | 前端·策略管理页 | P1 | 81,82 | backlog | ui |
| 89 | 前端·模型 Tab + 下发向导 + 监控/存储页 | P2 | 83~87 | backlog | ui+lifecycle(组件) |

## 二、依赖图

```
80 ─▶ 81 ─▶ 82 ─▶ 83 ─▶ 84 ─▶ 85 ─▶ 86
                      └───────▶ 87
88 ◀─ (81,82)    89 ◀─ (84~87)
```

## 三、菜单结构

见 [menu.md](../menu.md)：组 `data-lifecycle`（策略管理 / TTL 监控 / 存储统计）+ 模型详情内嵌 Tab。

## 四、落地路线

| 阶段 | Ticket | 内容 |
|---|---|---|
| 后端 | 80~87 | 骨架→策略→绑定→生成→预览→下发→重试→监控/统计 API（先后端整链） |
| 前端 | 88~89 | 策略管理页 → Tab/向导/监控/存储页 |

## 五、一句话总结

> 十票打通"策略→绑定→生成→预览→确认下发→重试→监控→统计"闭环；平台记状态，Doris/Paimon 执行清理。
