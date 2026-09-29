# Modeling Review

本文是建模模块 PR 的评审与拒绝标准。评审顺序:先契约,后实现。

## Gate 1:契约先行(硬性)

- [ ] 本次改动涉及的每个模块,其契约文件(README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW)是否已先行更新?契约 diff 是否先于代码被审阅?
- [ ] 新增 package / 对外门面 / 跨模块依赖是否已登记进 ARCHITECTURE.md / DEPENDENCIES.md?
- [ ] `data-ops-ui` 下任何 `.md` 文件出现在 diff 中 → **直接拒绝**(前端契约文件只读)。

## Gate 2:领域一致性

- [ ] 一个模型 = 一张物理表(口径 A1)是否被保持?
- [ ] 逻辑 → 物理是否保持单库单方言?不可映射类型是否显式报错?
- [ ] 血缘是否做到不自动登记?变更感知是否做到仅通知、不改模型?
- [ ] 已发布版本是否不可变?删除是否软删除且回收站内不可引用?

## Gate 3:平台规范

- [ ] PROJECT_SCOPE:projectId 取服务端可信上下文;异步任务独立恢复项目上下文;无物理外键。
- [ ] home-overview-contract:统计服务端聚合,无无界 list() 后内存统计。
- [ ] 菜单契约:navigationMenuContract 测试通过;新增菜单 migration 同时登记进测试的 CATALOG_EXTENSION_MIGRATIONS。
- [ ] Flyway:只增不改;建模模块 migration 在 `db/migration/yak-modeling`,菜单/权限目录在 yak-security migration。
- [ ] CODE_STYLE.md(Java)与 data-ops-ui/FRONTEND_CODE_STYLE.md 遵守。
- [ ] 审计:创建/删除/发布/结构变更进入操作日志。

## Gate 4:测试

- [ ] 领域规则(校验、diff、类型映射、SQL 生成)有单元测试。
- [ ] controller 层有请求校验与错误语义测试。
- [ ] 涉及跨模块门面的,测试不依赖对方内部实现。
