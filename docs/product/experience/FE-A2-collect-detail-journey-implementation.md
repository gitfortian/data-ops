# FE-A2 / 3｜采集任务详情的执行、恢复与精确来源回链

日期：2026-10-14。依据 #513（用户故事地图）、#539（J1 六屏）、#550（IA v0.8.2 最终原型候选）、#559（FE-A 分阶段交付）。

## 这次正式 React 代码解决什么

#561 已打通 Live DataSource→ODS→同一原始源表；#564 已在 Metadata 采集任务列表页实现“保存任务后下一步”操作，但已有的采集任务**详情页**仍只能读配置、关联表与打开历史抽屉。用户通过任务详情或刷新后无法在同一个工作场景重新预演、确认正式采集并解释本次回执。本次将相同工作流落实到 Task detail，直接复用 Metadata Owner 的 `getCollectJob` / `dryRunCollectJob` / `runCollectJob` / `pageCollectRuns` / `searchMetadata` 已有协议，不创建新任务或 Run 真相表。

## 行为合同和证据边界

1. 通过 `/data-metadata/collect/:id` 先由 `getCollectJob(id)` 读取当前 Project 内**持久 Job**，并核对 URL 来源数据源 ID（如有）。不允许仅由 URL hint/列表中旧对象直接执行。
2. HARVESTED 任务可复用既有 `CollectionJourneyContinuation`，按已保存任务的库、Schema、LIKE 表范围展示，提供建议预演、次级直接采集、确认正式范围、真实 Run/目录、历史核对，支持收起/恢复。REGISTERED 仅保留已保存配置与历史，不提供执行按钮。
3. 预演/正式执行只有具备 `data-metadata:read` + `data-metadata:create` 时提供可用入口，并依赖服务端权限作为最终裁决。执行中防止重复点击，正式执行强制再次确认**持久任务范围**。
4. Run 返回必须校验 `jobId`、正整数 `runId`、本次 `dryRun` 模式；不匹配或超时视为**结果未知，建议查历史**，不能推断成功/失败、不能生成假的运行编号。收到有效响应仍据 `status`、partial failures 判读“成功/待核对”；不把预演成功冒充真正目录采集。
5. 每次执行回执后重新获取当前持久 Job，确保新 `dryRunPassed`、范围、调度事实来自 Metadata Owner；重读失败明确提示不可核验。用户切换项目/撤权/任务后，异步回执及确认操作由 scope-epoch 丢弃。
6. 从已验证源表进入定义且保存任务后，`collectTaskDetailWithOriginPath(job, sourceOrigin)` 传递精确的 dataSourceId、database、schema（可选）、table。详情页重新核对 `getCollectJob` 的 HARVESTED 任务 ID / 来源 ID / 任务覆盖范围，才展示“返回原来源表”；它调用既有 `/data-source?sourceId=...&database=...&schema=...&table=...`，DataSource Owner 再以列表+源详情+表检索验证。普通任务列表只包含 dataSourceId 时**不推断**精确表，也不显示回源按钮。
7. 本次运行快照与当前关联表目录是独立事实；RunResultModal / RunHistoryDrawer / CollectedJobTables 沿用现有 Owner。读目录失败不宣称表不存在，旧入口仍完整保留。

## 验证与未完成事项

- 回归：`detail.test.tsx` 覆盖持久任务读取、预演成功≠正式采集、明确正式确认、只读角色、坏 Run 回执/历史恢复、收起/恢复、精确回源及跨 Schema 拒绝；`collectionHandoff.test.ts` 覆盖候选来源坐标与真实 Job 作用域双向吻合、伪造来源、缺少表坐标等负例。
- Product Guard / Architecture Static / affected Metadata Jest / TypeScript baseline / 生产构建门禁**以最新 CI 为准，绝不预先认定通过**。
- 真机 320/390/768/1440 视口、屏幕阅读器、跨 Project 权限撤销、真实 DB/Schema 漂移、断线/超时后的服务端 Run 证据，尚须在真实环境 J1 黄金故事签收。
- 仍不代表 v0.8.2 整站菜单/视觉冻结已签收，也不新增 DR-06/07/08 逻辑物理映射、DataDev 任务引用或协作写合同。
