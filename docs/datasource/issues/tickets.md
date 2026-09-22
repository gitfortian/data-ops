# 数据源模块——Ticket 清单

> 状态：v1.0（2026-09-22）
> 来源：[docs/v1/modules/数据源-现状与能力分析.md](../../v1/modules/数据源-现状与能力分析.md) §6 缺失能力表，逐项拆票
> 编号从 **01** 起，模块内有序

## 一、Ticket 总览

| 编号 | Ticket | 优先级 | 阻塞于 | 状态 | 模块 |
|---|---|---|---|---|---|
| 01 | 删除/变更引用守卫 | P0 | — | ready-for-agent | datasource + 8 个下游模块 |
| 02 | JDBC 驱动包上传后端端点 | P1 | — | ready-for-agent | datasource |
| 03 | 连接健康定时巡检 | P1 | — | ready-for-agent | datasource |
| 04 | SQL 执行审计观测页面 | P1 | — | ready-for-agent | ui + boot(菜单迁移) |
| 05 | 凭证静态加密 | P1 | — | ready-for-agent | datasource |
| 06 | 数据源变更/删除对外事件 | P2 | 01 | ready-for-agent | datasource |
| 07 | 权限编码统一对账迁移 | P2 | 04（迁移取号顺延） | ready-for-agent | common + boot + ui |
| 08 | 数据源页目录树 | P3（可选） | — | backlog | ui |

## 二、依赖图

```
01 ─▶ 06（同文件 DataSourceManager，先 01 后 06）
04 ─▶ 07（安全目录迁移取号顺延）
02 / 03 / 05 / 08 相互独立
```

## 三、明确不做（记录在案）

- realtime/job/workflow/development 的 JSON 内嵌引用守卫（LIKE 扫描假阳性风险，01 票尾记录，需专项）
- 密钥轮换（05 只做静态加密 + 存量洗数）
- 插件热卸载（02 只做上传即注册）
- 下游订阅 `DataSourceChangedEvent` 的实际改造（06 只立契约）
- `/catalog/diagnostics` 下线决策（分析文档 §3 B3 的二选一，留给产品拍板，不占票）
