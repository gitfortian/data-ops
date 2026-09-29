# Asset Domain

## 核心概念

### 资产台账（Asset Item）

跨域资产的目录卡片：`asset_key` **直接复用源域血缘登记键生成器**（小写前缀式：`table:{dsId}:{db}.{schema}.{tbl}`、`dataset:{id}`、`chart:analysis:{id}`；MANUAL=`manual:{code}`），与 `yak_metadata_asset` 天然同源（D6）。快照列（name/description/layer/domain/定级）仅供检索过滤并标注来源；业务事实详情永远实时读源域（D1）。

### 上架状态机

`PENDING →(预检+publish) PUBLISHED →(offline 必填原因) OFFLINE →(再 publish) PUBLISHED`；
旁路：`PENDING/OFFLINE →(ignore) IGNORED`（对账不复活，仅刷 reconciled_at；content_hash 变化回 PENDING）；任何非 GONE 态在源消失一个窗口期后 → `SOURCE_GONE`，源回归 → REAPPEARED 变更待确认并恢复原状态。上架 ≠ 源域发布（D3），是治理门面状态。

### 对账（Reconcile）

`AssetProvider` SPI（沿用 task-catalog Reconciler 范式，D2）分批游标（≤500）拉取源域清单 → 按 asset_key upsert：不存在→建 PENDING 行+套编目规则+继承源创建人为 owner（D4 仅此一次）；content_hash 变化→META_CHANGED 变更（台账字段不自动覆盖，确认后覆盖）。`reconciled_at` 落后超窗口（默认 7 天）→ SOURCE_GONE。MANUAL 不参与对账。

### 目录 / 标签 / 编目规则

目录树物化路径 `/1/4/9/`，模板初始化（按分层+域）产生 builtin 行（可改不可删）。标签为跨域业务标签字典。编目规则 DIRECTORY/TAG 两类、多条件 AND、priority 小者优先同类型首条命中即停；**启用前必须 dry-run 通过**（D10）。

### 健康度（D7）

纯函数派生，每日定时重算 + 关键动作即时重算单资产：完整性 40 / 可信度 40 / 活跃度 20；不适用项 N/A 剔出分母，`score = Σ实得 / Σ适用满分 × 100`；A≥85/B≥70/C≥55/D<55。明细公开（每项得分与缺口），杜绝人工改分。

## 不变量

1. **项目空间归属**：全部业务行带 `project_id`，只取 `CurrentProject` 服务端上下文（D11）。
2. **asset_key 项目内唯一**：`uk(project_id, asset_key)`；键由源域生成器产生，禁止自造第二套键。
3. **不写 `yak_metadata_asset`**（D6）：血缘登记与资产台账互不写入。
4. **负责人是治理联系人不是权限主体**（D4/D12）：不建 ACL 表、不做行级过滤。
5. **健康度只读**：任何接口不接受 health_score 入参；仅重算任务可写。
6. **上架必经预检令牌**：precheck 返回 5 分钟 token，publish 必带（48005），对齐 lifecycle 确认令牌先例。
7. **下架必填原因**（48013）；IGNORED 仅 PENDING/OFFLINE 可达（48016）。
8. **统计预算**：概览固定 ≤8 查询 + LIMIT；列表排序仅用台账派生列（§6.5 公式），零跨域调用。
9. **分区容错不伪造**：详情 fan-out 失败分区返回 UNAVAILABLE；评分失败依赖记 0 分并标注。
