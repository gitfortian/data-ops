# Wave 2 · P1 版本语义纠偏工单

> 前置：W1 全部合入（消费读发布态的习惯先在 P0 对象立住，再铺 P1）。每对象独立 PR；共同依赖 S1-S4。
> 阻塞裁决：W2-1←Q5、W2-2←Q4、W2-3←Q3（见 README）。

---

## W2-1 ｜P1｜metric 快照从「变更流水」纠偏为「发布触发」

**现状问题**：
- 快照在创建/更新/状态变更时自动追加（`MetricCatalogService.java:139,211,248-249`），非发布触发→版本表是审计流水不是发布史；`MetricPO.java:73,76` 的 `version` 兼做乐观锁与版本号（`:176-178` expectedVersion 手工比对）。
- 无 DRAFT/PUBLISHED（`MetricStatus.java:4-5` 仅 ENABLED/DISABLED）、无 publish 端点（`MetricController.java:55-134`）、无回滚。
- `toJsonSnapshot`（`MetricCatalogService.java:575+`）手写清单**漏 compositions**（:544-569 引用关系含 COMPOSITION 但快照未收）。
- 级联删除时**物理删版本行**（`:299-301`），违反 append-only。
- 前端：版本 Tab 已渲染列表但不展示 snapshot（`pages/metric/detail/index.tsx:136-146,292-304`）；`getVersion` 端点已建（`MetricVersionController.java:50-60`）、service 已封装（`services/metric/api.ts:83-85`）但零调用方。

**改动点**：发布态接入（S1 枚举，ENABLED/DISABLED 保留为正交开关）；快照触发改 publish（checksum 幂等）；update 只改草稿；toJsonSnapshot 改序列化整个聚合（含 compositions，弃手写清单）；删除改逻辑删或保留版本行；主表加 published_version_id 指针，`version` 列语义拆分为 draft_revision；消费方（DWS/ADS 驱动建模读指标口径处）切读发布快照；前端换 S4 面板（打通 snapshot 渲染与 getVersion 消费）。存量数据按 Q5 决议迁移（新 V 号）。

**验收**：更新指标不发布→建模侧口径不变；发布→版本+1 且含 compositions；回滚可用；删除指标后版本行仍在（或按决议说明去向）。

---

## W2-2 ｜P1｜semantic 标准快照语义纠偏（改前→当前）+ publish/rollback

**现状问题**：
- `yak_semantic_standard_version`（V3 迁移）头注释自陈快照=「修改前完整状态(version=被替换掉的版本号)」，`StandardCatalogService.java:149-150` 先 `recordSnapshot(existing)` 再应用更新（码集路径 :311 同）→ **当前版本永无快照**，抽屉里看到的"vN"其实是被 vN 替换掉的旧内容。
- 状态 ENABLED/DISABLED（`StandardStatus.java:5-8`）非发布态；`SemanticStandardController.java:66-210` 无 publish/offline/rollback（:112-119 的 publish-approval 是审批生效非版本发布）；全模块 grep rollback/restore/activate 零命中。
- 前端 `StandardVersionsDrawer.tsx:25` 自陈「本期仅支持查看，不支持一键回滚」，:41 裸 `JSON.stringify`。
- 业务过程/域无 status 无 version（`SemanticProcessPO/DomainPO`）；分层有 status 无版本；标准字段 version 仅乐观锁注释自陈。

**改动点**：快照语义倒正（发布时落**当前**内容全量，主表 version 与快照 version 对齐；存量错位数据出迁移说明不改史——历史"改前"快照保留为只读流水，新表或新列区分，实施时给方案）；接 S1+六端点（PUT 改草稿）；前端抽屉换 S4 面板获得 diff+回滚。Q4 若拍板"过程/域仅补 status"：`SemanticProcessPO/DomainPO/` 加 status 列（新 V 号）随本单做，不建版本表。

**验收**：编辑→发布→版本列表末位=当前内容；任选两版 diff 正确；回滚一步生效；码集同链路验证。

---

## W2-3 ｜P1｜dataset 回切端点 + 上下线绑定版本

**现状问题**：
- `DatasetVersionWriter.java:65-104` 每次 append 后立即 `updateCurrentVersion`（:81 唯一写点）→ 只追加无回切；`DatasetController.java:87-119` 无 activate/rollback。
- `DatasetStatus.java:3-6` 仅 ONLINE/OFFLINE，无 DRAFT；上下线不触碰版本（`DatasetManager.java:29-42` 仅 updateStatus）。
- 快照混合粒度：SQL 型全量、任务源型存 `source_task_revision_id` **指针**（跨对象版本链耦合，Q3）。
- 前端：分析侧仅版本下拉预览（`pages/data-analysis/dataset/detail/index.tsx:101-144`）、目录侧只读表格，无回滚。

**改动点**：新增 `POST /{id}/versions/{no}/rollback`（回切=以旧版内容追加新版并移 current 指针，追加式统一）；上下线与版本绑定（online 要求显式指定/默认 current 版，写绑定列）；Q3 拍板保持指针则在契约 C2 注记「任务源 dataset 的有效性依赖来源 revision 禁物理删」（与 W2-1 的禁删规则互相引用）；前端接 S4 面板。

**验收**：v2 在用时回滚 v1→current 指针移至追加的 v3（内容=v1）；下线再上线恢复到绑定的版本而非隐式最新。

---

## W2-4 ｜P1｜realtime sync 版本列表端点 + 按版回切

**现状问题**：版本骨架完整（`yak_realtime_definition_version`、published_definition_version_id 指针、digest 幂等发布 `RealtimeDefinitionPublisher.java:55-76`、行锁 lockDefinition），但：
- 无 `GET /{id}/versions`、无单版、无按版回切；`RealtimeJobController.java:178-180` `apply-published-version` 只能收敛到**最新发布版**。
- `release_state` 字符串 DRAFT/PUBLISHED 未接枚举；发布态与运行时 desired/observed_state 混列一张表。
- 前端零版本列表（仅 `RealtimeExecutionPanel.tsx:105` 「应用已发布版本」按钮；版本相关 UI 空白）。

**改动点**：补 C3 的 versions 列表/单版/rollback 三端点（rollback=追加式改 published 指针+触发 apply）；`release_state` 换 S1 枚举（运行时状态列保留但重命名注释划清）；前端执行面板加版本 Tab（S4 面板）。

**验收**：发布 v2→按版回切 v1→deployment 收敛后运行定义等价 v1；apply 按钮语义在 UI 文案注明（应用最新发布 vs 回切指定版分列）。

---

## W2-5 ｜P1｜quality 监控 revision 浮出为用户可见版本

**现状问题**：`yak_quality_monitor_revision`（V2 迁移，definition_json 全量+checksum、幂等复用 `QualityTaskPublisher.java:52-61`）**只服务 workflow 执行固定**（`QualityTaskRevisionProvider.java:16-45`）；质量域所有 Controller 零 version/revision 端点、前端零版本 UI（data-quality 全目录无命中）；监控规则本体 `updateById` 就地覆盖、仅 `enabled` 布尔（`QualityMonitorPO.java:24`）→ 保存即生效。

**改动点**：规则编辑改草稿/发布双态（S1；enabled 保留为正交开关）；publish 才追加 revision 并影响执行；既有 revision 表加 created_by/payload 对齐 C2（新 V 号，注意该表 checksum 是 VARCHAR(64) 非 CHAR(64)，统一即可）；补 versions/单版/rollback 端点；前端 quality 监控详情接 S4 面板。workflow 侧 Task Revision Provider 契约已定，回滚时仍追加式，不动绑定语义。

**验收**：改规则不发布→调度执行仍跑旧 revision；发布→新 revision 被 pin；回滚生效且执行链路无断。

---

## W2-6 ｜P1｜data-service 节点补回滚入口

**现状问题**：草稿→Revision→Runtime 三层与 publish/revisions 端点已齐（`services/data-development/data-service.ts:149-165`），但回滚只能经发布中心 activate（dev-task 通道），节点编辑器版本 Tab 仅列表（`DataServiceNodeEditor.tsx:555-573`）、API 集市详情页只读版本号一行（`pages/data-service/detail/index.tsx:291`）。

**改动点**：最小接线——节点「版本」Tab 换 S4 面板并接发布中心 `activate/{revisionNo}`（Q1 豁免通道，语义=指针回切，文案注明）；集市详情页加版本历史只读入口。后端不动。

**验收**：节点页任选历史版→激活→Runtime 定义随动；文案正确表达"回切"非"追加"。

---

## W2-7 ｜P1（自 W1-5 拆出，排期可裁）｜mdm 实体配置版本化

**现状问题**：见 W1-5——实体/属性/清洗规则 PUT 就地生效、零版本表。
**前置**：盘点 mdm 落地/采集链路（R1-R7）对活配置的直读点清单，作为独立盘点小 PR 先行；清单出齐后再实施 C2 双表+六端点+消费切读。
**验收**：同统一底线；额外要求落地任务在配置「改了未发布」时行为不变。
