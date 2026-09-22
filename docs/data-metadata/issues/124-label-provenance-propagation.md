# Ticket 124：标签溯源与继承重算

**对应需求：** 治理 | **阶段：** P5 | **模块：** metadata

**What to build：** `yak_md_label` 打标/去标 + 溯源（"凭什么是 PII"可回答）+ 域/分类从表传导到列的重算。

**Blocked by：** 112、119

**验收清单**
- [ ] DDL **逐字照抄 plan §6.1**：`label_type`(MANUAL|AUTOMATED|PROPAGATED|DERIVED) × `state`(SUGGESTED|CONFIRMED) 双维度、`reason`、`derived_from`、`expires_at`
- [ ] **引用目标是 `asset_id`，不是 `target_type` + 目标键**：表/列/模型/标准字段**同构引用**（统一实体表白捡的收益——初稿的 `target_type` 每接一类新目标都要扩枚举 + 加分支）
- [ ] 按类型统计覆盖率时 JOIN `yak_metadata_asset` 取 `type_id`，**本表不冗余类型列**（冗余一份就等于多一个会漂移的事实）
- [ ] 自动/继承标注一律 `SUGGESTED`，人工确认才 `CONFIRMED`；概览分别统计两者
- [ ] `reason` 对 `AUTOMATED`/`PROPAGATED` **必填**（没理由的机器标签不可信）
- [ ] 继承重算必须是**幂等全量重刷**（OM 自陈的坑：传导会产生"内容没变但溯源过期"的状态）。**不引入 `PropagationDescriptor` 式通用框架**
- [ ] **对一条 `tableColumn` 打标与对一张表打标走同一套代码**：断言不存在 `if (targetType)` 分支（plan §8 P5）
