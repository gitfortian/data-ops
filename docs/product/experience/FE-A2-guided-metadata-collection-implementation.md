# FE-A2 / 2｜来源采集任务保存→预演→正式采集→返回原来源

**实现 PR 基线：** #561 已经于 2026-10-14 合并 main。**产品设计依据：** 用户故事地图 #513、六屏 #539、IA/原型 v0.8.2 #550、实施准入 #559。该 PR 是正式 React/Ant Design 前端的 J1 Metadata 采集**纵向切片**，不是声称完整原型已经重构上线。

## 1. 原问题与解决点

- 现有 Metadata Collection Owner 已有 `createCollectJob/updateCollectJob`、`dryRunCollectJob`、`runCollectJob`、`getCollectJob`、历史与结果面板，但保存任务后只返回普通任务列表。首次使用者不知道当前下一步，也不易理解预演与正式采集区别。
- 从 DataSource 的指定库/Schema/表进入定义时，跨域 URL 只是来源位置提示；未保存之前不能视为已纳管、已采集或合法执行范围。此前任务表单对空 `tablePattern` 的“全表范围”提醒较弱。

本轮在已有采集任务列表页面创建**来源采集下一步**任务区域，不引入新的全局 Job/Run 数据表或伪造阶段；采用单一任务卡聚合：**保存任务→建议预览→确认正式采集→检查结果/历史→返回来源**。表结构字段与物理采集/资产治理仍各自属于原 Owner。

## 2. 真实权限与证据

- **任务定义保存**：继续使用 Metadata Owner 的 `CollectJobRecord` 成功响应，才呈现下一步；未保存的 Drawer 数据绝不执行。
- **预览结果**：只有 `dryRunCollectJob(job.id)` 返回属于该 Job 的有效 `runId`、`dryRun=true` 且 `status=SUCCESS`、无 partial failure，才显示“预览已通过”。预演**不意味着**正式运行或目录更新。
- **正式运行**：复用已有 `Modal.confirm`，展示 Metadata Owner 持久任务的 DataSource/DB/Schema/`tablePattern`，仅确认后调用 `runCollectJob`。原有直接运行保留为次级动作，不把“必须预览成功”虚构成新的后端门禁。
- **执行成功**：必须是同 Job 的有效正式 `CollectRunRecord`，`dryRun=false`，`status=SUCCESS`，无 partial failure；才引导查看已采集表。成功仍不意味着资产已纳管/正式 ODS 已保存。
- **未收到回执 / 失败 / 部分失败 / 可疑超时**：不自动重试、不推断 Run ID，提示到任务历史核对；旧历史查询仍是唯一事实。
- **Project/数据源切换**：清除本页面的后续引导；执行异步结果由 scope epoch 拒绝过期提交。跨域来源必须事先由 DataSource Owner 列表与任务详情验证。
- **返回原来源表**：仅当入口保存了精确来源提示且持久化 Job 在相同 DataSource/DB/Schema 的匹配覆盖范围内，构造已存在的 `buildLiveSourceReturnPath`；DataSource 页面再次独立核验当前 Project 内源身份并重新探查真实表。不是运行回执中的“表已采集”证明。
- **范围 UX**：从表进入时，空匹配式仍是全表；显式提醒含 `_`、`%` 会触发 LIKE 模式匹配，需要操作人复核并预演。没有在缺乏后端字面匹配/转义协议前，直接把 `trade_order` 带入 LIKE 模式冒充精确表。

## 3. 测试与门禁

新增 `CollectionJourneyContinuation.test.tsx`：同 Job Run 身份、预演/正式状态、失败/部分失败/超时未知、权限、来源返回操作。扩展 `operations.test.tsx`：保存成功→预演→正式执行前确认→正式 Run 回执，以及预览失败走历史，防止误报进度。

CI 需包含：Product Guard、Architecture static/backend/frontend Jest + TypeScript baseline + 前端正式构建。**本说明并不代表已完成真实 E2E**：双 Project + 权限撤销、SQL-LIKE 模式范围实测、Run 在服务端异步/超时、窄屏真实浏览器、来源漂移及最终 UI v0.8.2 视觉签收，仍须执行。

## 4. 范围外及后续

- #550 v0.8.2 全站导航与两工作台规模重构仍按设计签收工作推进；此 PR 不改菜单 RBAC/Flyway、Meta/Asset Truth 或其他业务域。
- 完整 FE-A2 还需要详细采集结果页及真实项目级 E2E，并将真实来源→ODS 的黄金故事接到同一部署版本。
- 非 ODS 的 Logical→Physical 正式映射与 DataDev 任务合同仍受 #556/#557 的决策约束，不借本轮 UI 开发扩写 API。
