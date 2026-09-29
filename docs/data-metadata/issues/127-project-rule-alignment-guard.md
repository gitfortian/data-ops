# Ticket 127：`PROJECT_REQUEST_RULES` ⇄ 后端 `@ProjectScope` 自动对齐守卫

**对应需求：** 工程守卫（根治一类缺陷） | **阶段：** P5 | **模块：** data-ops-ui（测试）

**What to build：** 一个自动测试：扫后端 controller 的 `@ProjectScope` + `@RequestMapping`，与前端 `PROJECT_REQUEST_RULES` 比对，不一致即红。**这是本次 asset 999 事故的根因修复**——"两语言两份清单，靠人记"必然漂移。

**Blocked by：** 无（任意时刻可做；做完即覆盖全平台，不限元数据模块）

**为什么单独立票**：T1/T1b 两次同类缺陷（`/api/v1/metadata`、`/api/v1/assets` 前缀未登记 → 前端不发项目头 → 后端 `PROJECT_REQUIRED` 判 999）都不是逻辑 bug，是**清单不同步**。ticket 111 只补了元数据自己那一条，补不了"下一个模块也忘"。

**验收清单**
- [ ] 仿 `data-ops-ui` 现成的 `navigationMenuContract.test.ts` 路子（读后端源码做契约比对，已有先例）
- [ ] 规则：任何标了 `@ProjectScope(PROJECT_REQUIRED)` 的 controller，其 `@RequestMapping` 前缀必须能在 `PROJECT_REQUEST_RULES` 里被某条规则命中；命中不到即测试失败并打印缺哪条前缀
- [ ] 反向：`LEGACY_GLOBAL` 端点**不得**被新加的 `PROJECT_REQUIRED` 规则意外收敛（避免把全局接口锁死）
- [ ] 全平台跑通：现存违规模块**在本票里如实列出并逐条修**（asset 已修，见 plan §11.1 第 3 条；其余若发现同类缺口需一并补，不得为了让测试变绿而放宽规则）
- [ ] 加入 CI 必跑集，与本模块 `MetadataLayeringConventionTest` 一起构成 plan §10 的两道静态守卫
