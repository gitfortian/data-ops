# Ticket 75：数据访问策略与裁决

**目标**：定义"谁对什么数据有什么访问权"，并给出数据级裁决（叠加在平台 RBAC 之上）。

**表**：`yak_dsec_access_policy`。
**行为**：
- 策略 CRUD（subject USER/ROLE × scope DATASOURCE/DATABASE/TABLE/COLUMN/LEVEL/ALL × access READ/WRITE/EXPORT × effect ALLOW/DENY，含有效期/优先级）。
- 申请审批：`status` PENDING→APPROVED/REJECTED，`apply(id,approve,operator)`。
- 裁决 `decide(actor, roles, objectKey, action, levelId)`：收集匹配策略，DENY 优先、priority 大者优先；无命中→敏感对象 NEED_APPROVAL、非敏感 ALLOW。产出 `AccessDecision` 并写 `access_log`。
**SPI**：`SecurityAccessDecisionApi.decide(...)`。
**审计**：`ACCESS_POLICY_*`；决策经 audit `authorizationDecision` 留痕。
**错误码**：45050~45059。
**验收**：裁决单测覆盖 DENY 优先、priority、有效期、默认策略。
