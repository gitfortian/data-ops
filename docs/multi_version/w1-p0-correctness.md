# Wave 1 · P0 生产正确性工单

> 契约依据：设计基线 §三 C1-C5。五单互不依赖，可按模块并行分 PR；共同前置：S1-S3（S4 仅 W1-4 前端部分需要）。
> 每单验收含 README「全工单统一验收底线」。

---

## W1-1 ｜P0｜lifecycle TTL 策略版本化（全项目最高风险点）

**现状问题**：改错 `destroy_days` 一旦被下发即清生产分区且无法回退策略。
- `TtlPolicyService.java:192-196` `update()` 就地 `updateById` 覆盖；`:200-202` 审计 before/after 传 `Map.of()`/null。
- `TtlPolicyController.java:45-111` 仅 page/get/create/update/delete/status/layer-template，无 publish/offline/rollback。
- lifecycle 全部迁移仅 `V1__create_lifecycle_tables.sql`（5 张表），无策略版本表。
- 前端 `PolicyEditDrawer.tsx:154` 直提，无「影响 N 个绑定模型、需重新下发」提示；DRIFT 检测（`ModelTtlBindingService.java:199-214`）只兜存储端漂移，兜不住策略被写坏。

**改动点**：
- 新迁移 `yak-lifecycle/db/migration/.../V2__ttl_policy_version.sql`：按 C2 建 `yak_lc_policy_version`（payload_json 全量策略快照）；主表 `yak_lc_policy` 加 `status/published_version_id/latest_version_no/draft_revision`。存量行回填：status=PUBLISHED + 拍一条 v1 快照（迁移脚本内 INSERT...SELECT）。
- `PUT /{id}` 改存草稿（draft_revision 自增，线上读已发布快照）；新增 C3 六端点；publish 走 checksum 幂等（S2）。
- 下发链路（dispatch/`ModelTtlBindingService`）改读 `published_version_id` 快照内容。
- 审计接 S3 `AuditDiffs`；前端 `PolicyEditDrawer` 保存/发布两钮分离 + 编辑时展示绑定模型数（binding 表已有数据）+ 发布前影响确认。

**验收**：页面实测——编辑草稿不改变下发所用值→发布→版本 Tab（S4 面板）出现 v2→改回旧值发布 v3→回滚 v1→生效内容与 v1 等价；审计里能看 diff。

---

## W1-2 ｜P0｜sync-offline 离线作业草稿/修订双表

**现状问题**：保存即覆盖线上同步定义，同步逻辑改错无法回滚。
- `OfflineJobDefinitionPO.java:22` `definitionJson` 单列就地覆盖；`:41` `version` 只是行内自增乐观锁，非内容版本。
- `OfflineJobDefinitionService.java:257,282` 覆盖写、`:297-310` `nextVersion()` 仅 +1 后 `updateById`（`OfflineJobDefinitionRepositoryAdapter.java:69`）。
- 迁移 `yak-offline-sync` V1-V5 无 revision 表。

**改动点**：
- 照 dev-task 双表模式（`V1__baseline_data_development.sql:38,51` 的 draft/revision 结构）：新 `yak_offline_job_revision`（C2 规范 + `(job_id,checksum)` 幂等索引）；definition 表拆出草稿载体（可加 `draft_json` 列或复用原列语义改为草稿，实现时选**改动最小**者并在 PR 描述注明）。
- `PUT` 保存→草稿；`POST /{id}/publish`→追加 revision + 移发布指针，执行调度器改读已发布 revision；C3 其余端点补齐。
- 状态收敛到 S1 `PublishState`（消灭 releaseState 字符串字面量）。
- 前端：integration/batch-link-up 编辑页保存/发布分离 + 接 S4 面板（该模块当前版本 UI 为**零**）。

**验收**：改草稿不发布→手工触发执行仍跑旧定义；发布后执行读新；回滚一版后执行定义等价历史版。

---

## W1-3 ｜P0｜modeling 消费方读已发布快照 + 回滚两步 + 发布前强制保存

**现状问题**（破坏"发布态=生产态"的核心反例）：
- `ModelStructureService.java:111-124` 保存结构就地覆盖活表，`:126` 置回 DRAFT；`published_version_id` 虽在（V16 迁移），但**血缘/指标/派生/DDL 下发直读活表** → 未发布草稿实际生效。
- `ModelVersionService.java:114,121-125` 回滚=恢复结构后**立即置 PUBLISHED**，当前草稿被覆盖（`ModelVersionPanel.tsx:131-147` 虽有 Popconfirm，但两步语义缺失）；快照只含 structure 不含名称/描述/分层（docs §三.3）。
- 前端 `pages/modeling/detail.tsx:1496-1510` `handlePublish` 只调 `publishModelingModel`，未先 saveStructure → 静默漏发。
- `ModelVersionRepositoryAdapter.java:85-95` nextVersionNo 用 selectCount+1（删除计数风险）。

**改动点**（本单内部分两 PR：后端隔离 / 交互纠偏）：
1. 盘点并切换消费读路径：血缘登记、指标 DWS/ADS 驱动建模、派生、DDL 下发（9-21 新增方言骨架）凡以活表列为源者，改经 `publishedVersionId` 取 `structure_json`；无发布版的老数据以迁移回填 v1 兜底（新 V 号）。
2. 快照扩全量：payload 增名称/描述/分层/业务域等元数据（新 V 号迁移改注释即可，LONGTEXT 无需改型）；publish/rollback 均写新 payload。
3. 回滚两步：`rollback` 端点改为「恢复为草稿（置 DRAFT，不自动发布）」，前端确认文案改「将用 v{n} 覆盖当前草稿，需再次发布才生效」；如需直接生效走「回滚并发布」第二按钮=两步串调。
4. nextVersionNo 改 SQL MAX+1（S2 模板）。
5. 前端发布按钮先 diff「草稿 vs 上次发布」，有未保存内容时提示先保存（复用 S4 的 JsonDiffView）。

**验收**：编辑结构不发布→血缘图/指标引用不变；回滚 v1→模型处于 DRAFT 且线上仍读旧发布版→再发布生效；连发两次相同内容不产生新版本。

---

## W1-4 ｜P0｜workflow 状态枚举收敛 + 回滚端点 + 版本抽屉可操作

**现状问题**：
- 有全量版本链（`yak_workflow_version`，`run_request_json`）但**零回切能力**：`WorkflowDefinitionController.java:117-146` 仅 online/offline/pause/resume + versions 列表，无 activate/rollback；执行固定靠 `active_version_id`（String）。
- 状态用字符串字面量 `"DRAFT/ONLINE/OFFLINE"`（`WorkflowDefinitionManager.java:204,323,343,365`）；`DefinitionState.java:4` 死枚举。
- 前端版本抽屉只读（`WorkflowToolbar.tsx:213-280` 仅卡片展示）；并发控制为内存 `synchronized(state)`+快照回滚，非 DB 行锁。
- `version_kind` 列有 DB 默认值但业务代码从不写入。

**改动点**：
- 引 S1 `PublishState` 替换全部字面量（ONLINE↔PUBLISHED 存量值迁移：新 V 号 `UPDATE ... SET status='PUBLISHED' WHERE status='ONLINE'`，读写口一次性切换，不留双写）。
- 新增 `POST /{id}/versions/{no}/rollback`：追加式（以 v{n} 引擎 JSON 追加新版并置 active + PUBLISHED，语义对齐 digital-screen `DigitalScreenPublisher.java:51-73`）；顺手补 `GET /{id}/versions/{no}` 单版端点（diff 数据源）。
- 并发：rollback/publish 路径改 `SELECT ... FOR UPDATE` 锁 definition 行（对齐大屏），保留内存聚合作为缓存层不动。
- `version_kind` 要么写入真实值要么从 PO 删除——选**删除**（无消费方）。
- 前端 `WorkflowToolbar` 版本区换 S4 面板（列表+查看+diff+回滚）；请求层从 umi request 直用收敛到 `services/workflow/api.ts`+HttpUtils 模式（小重构，单独 commit）。

**验收**：上线→改草稿上线 v2→回滚 v1→执行实例固定到追加的 v3（内容=v1）；页面实测抽屉可操作；定义状态列不再有 "ONLINE" 字面量。

---

## W1-5 ｜P0｜mdm 配置侧草稿/发布 + 审计 diff（记录侧已好，配置侧零版本）

**现状问题**：审批链路与记录版本前端已接完（`services/mdm/api.ts:252-291`、`pages/mdm/approval/index.tsx`、`ChangeHistoryTab.tsx:67-83` 属性级 diff、导航 `navigation.ts:131`）——评审 P0 第 5 条**剩余缺口在配置侧**：
- `MdmEntityController.java:72-77` PUT → `MdmEntityService.java:101-126` 实体/属性/清洗规则就地 `repository.update` 生效；审计 `:113,120` 传 `Map.of()`。
- 全仓无 entity 配置版本表（grep `MdmEntityVersion` 零命中）。

**改动点**：
- 范围裁决（保守）：本单先做**审计 diff 补全**（S3 件，实体/属性/规则三处 PUT）+ 状态字段收敛；实体配置完整版本化（双表+六端点）拆出 W2-7 视排期启动——理由：mdm 落地链路（R1-R7）大量直读活配置，切读发布快照的影响面未盘点，不在 P0 里赌。
- 若 W2-7 启动：`yak_mdm_entity_version` 按 C2，实体建模页编辑→草稿、发布→生效，采集/落地任务读已发布快照。

**验收**：本单——三处 PUT 审计出现真实 before/after；发布/回滚不在本单验收内（明写在 PR 描述，防误报完成度）。
