# Ticket 122：前端·采集任务页

**对应需求：** 元数据中心界面 | **阶段：** P4 | **模块：** data-ops-ui

**What to build：** 采集/对账任务的管理页：作用域选择、cron、dry-run 预览、立即运行、运行历史（四计数 + 状态 + 耗时）。

**Blocked by：** 111、116、135（对账任务在同一页里出现，只是 `provider_type` 不同）

**验收清单（逐条对齐 `docs/INTERACTION_PRINCIPLES.md`：能选择就不填、能默认就不留空）**
- [ ] 作用域**从数据源/语义分层配置带出**，不让人手填库名
- [ ] 任务编码自动生成；cron 给 presets 下拉 + 人话预览（"每日 03:00"），不让人写表达式
- [ ] **新建默认关闭** + **必经 dry-run 预览**才允许启用（未 dry-run 不允许启用）
- [ ] "立即运行"按钮 + 运行历史抽屉（`collect_run`：NEW/CHANGED/UNCHANGED/GONE、`SUSPECT`/`FAILED` 原因、耗时）
- [ ] 批量删除/忽略类操作必经预览确认
- [ ] 物理采集与投影对账**同一套页面**（不为对账再开一个入口）
- [ ] 页面只经 `src/services`/`rest` 层取数，不直连 DTO（plan §9 T14）
