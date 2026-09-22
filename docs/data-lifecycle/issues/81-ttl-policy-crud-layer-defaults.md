# Ticket 81：TTL 策略 CRUD 与分层默认策略

**对应需求：** 3.1 / 3.2 | **阶段：** P1 | **模块：** lifecycle

**What to build：** 治理管理员通过 API 管理 TTL 策略：分层默认策略一键初始化（预置模板，层已有 lifecycle_days 时以其为销毁值 D1）；自定义策略增删改查；三段值校验 hot≤cold≤destroy（47005）；被绑定引用的策略不可删（47004）；每层至多一条层默认（47003）。

**Blocked by：** 80

**验收清单**
- [ ] `TtlPolicyService` + `TtlPolicyController`：`POST /api/v1/lifecycle/policies`（page/CRUD）+`PUT`+`DELETE`
- [ ] 编码自动生成（层默认 `ttl_{layer}_default`；自定义 `ttl_custom_{seq}`），项目内唯一（47002）
- [ ] `POST /policies/initialize-layer-defaults`：对每个启用分层按模板表幂等创建，返回 created/skipped 计数
- [ ] `GET /policies/layer-template`：返回 ODS/DIM/DWD/DWS/ADS 预置三段值（前端预填唯一数据源，禁止前端硬编码）
- [ ] 校验：粒度枚举、三段顺序、LAYER_DEFAULT 不可删（47004 语义区分"内置"）
- [ ] 审计：TTL_POLICY_CREATE/UPDATE/DELETE（`BusinessAuditService` + `AuditTransactions.completeOnCommit`）
- [ ] 单测：初始化幂等、三段校验、引用保护、唯一约束
- [ ] `mvnw -o -pl ...-lifecycle test` 绿
