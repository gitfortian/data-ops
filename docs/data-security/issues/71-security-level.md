# Ticket 71：安全等级字典（分级）

**目标**：维护安全等级字典，可选对齐 semantic `SECURITY` 标准。

**表**：`yak_dsec_security_level`。
**行为**：创建（`level_code` 项目内唯一、`^[A-Za-z0-9_]{1,32}$`、`rank_no`≥1）；编辑（编码不可改）；状态流转 DRAFT/ACTIVE/DISABLED；删除被 classification 引用时阻断；分页；`findAll` 供下拉。
**复用**：`std_security_id` 引用 semantic，不校验存在（本期松引用）。
**审计**：`SECURITY_LEVEL_*` 走 BusinessAuditService（fail-open）。
**错误码**：45010~45019。
**验收**：Service 单测覆盖唯一/状态流转/引用阻断。
