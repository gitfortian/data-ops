# Ticket 125：认证过期 + 治理待办 + 禁自审

**对应需求：** 治理 | **阶段：** P5 | **模块：** metadata

**What to build：** 认证不再是布尔位（无期限认证 = 永久免检），待办有库层去重与状态不变式，`EntityStatus` 7 值可度量。

**Blocked by：** 124（认证 = 特定 `label_code` + `expires_at`）

**验收清单**
- [ ] 认证 = **特定 `label_code` + `expires_at` 非空**，不是布尔位；过期扫描任务把过期认证显式呈现（详情页提示"认证已过期"）
- [ ] **`expires_at` 绝不进 `content_hash`/`source_hash`**，过期也**不触发** CHANGED（否则"认证过期"每天伪装成"表结构变更"刷屏，plan §3.3/§8 P5）
- [ ] `yak_md_task` DDL **逐字照抄 plan §6.2**：`open_marker` 为**服务层写入的普通列**（生成列方案本仓库不可用，实测 `3109`）+ `CHECK ((open_marker = 0) = (resolved_at IS NULL))`
- [ ] 办结语句必须是 `SET resolved_at=?, open_marker=id`
- [ ] 库层三条用例全绿（已在本机 MySQL 8.0.46 `yak_security` 预演）：同类同目标第二条未办结 → **1062**；只写 `resolved_at` 忘刷 marker → **3819**；按 id 刷 marker 后再建同目标 → **OK**
- [ ] 待办类型：`FILL_COMMENT|CONFIRM_LABEL|FIX_CONFORMANCE|REVIEW_GONE`；`asset_id` 同构引用（同 124，不得有类型分支）
- [ ] `entity_status` 用 OM 的 7 值且**默认 `Unprocessed`**：治理度量必须能分"没人看过"(`Unprocessed`) 与"看过但不合格"(`Rejected`)
- [ ] **提单人不能自审**：状态迁移服务里硬校验 + 单测（403 语义）。**不引 Flowable/BPMN**——3 步流程用状态列 + 一张迁移记录表足够
- [ ] 差异来源：`FIX_CONFORMANCE` 待办由 ticket 120 的差异矩阵生成
