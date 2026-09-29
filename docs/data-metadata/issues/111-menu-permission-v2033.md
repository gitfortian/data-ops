# Ticket 111：元数据菜单权限 V2033 与前端注册

**对应需求：** 元数据中心（工程底座） | **阶段：** P0 | **模块：** data-ops-boot（security 迁移）+ data-ops-ui

**What to build：** 平台出现独立一级菜单"元数据"及 4 个子页（采集任务 / 目录浏览 / 统一搜索 / 概览），路由可达、内容为空态。未授权账号看不到该组。

**Blocked by：** 110

**为什么独立一级菜单**（plan §11.1 第 2 条）：采集任务与运行历史是**运维心智**，与 asset 的资产台账（目录心智）不同源；并入会让 asset 承接采集调度，违背其"管目录不管内容"的既有定位。

**硬性约束**：前端 `data-ops-ui/**.md` 只读（plan §0.12）；权限码字符串三处（SQL / `securityMenuCodes.ts` / `navigation.ts`）逐字节一致。

**验收清单**
- [ ] `V2033__register_data_metadata_menu.sql`：group `data-metadata` + 4 子页 + 4 权限 + root 授权，**幂等**（模板 V2029/V2032；V2030 security、V2031 lifecycle、V2032 asset 已占用）
- [ ] **绝不修改任何已应用迁移文件**（plan §9 T5，`checksum mismatch` 会让整个应用启不来）
- [ ] `src/constants/securityMenuCodes.ts` + `src/constants/navigation.ts`（4 路由）
- [ ] `data-ops-ui/src/utils/security/projectContext.ts` 的 `PROJECT_REQUEST_RULES` 加 `{ prefix: '/api/v1/metadata', mode: 'PROJECT_REQUIRED' }` —— **漏这一条整个模块所有接口 999**（plan §9 T1，asset 侧同类缺陷已实测踩过）
- [ ] `projectContext.test.ts` 补断言：命中 `/api/v1/metadata`、且 `/api/yak-ops/apix` 类反向用例不被误命中
- [ ] `navigationMenuContract.test.ts`：本票**同时把 V2028（mdm 审批）与 V2032（数据资产）补进 `CATALOG_EXTENSION_MIGRATIONS`**（现缺，菜单契约测试对这两个模块不设防，plan §11.3）
- [ ] 4 个前端空态页目录 `./data-metadata/{collect,catalog,search,overview}`
- [ ] `npx tsc --noEmit` 不高于 **199** 基线、被改文件零新增错误；`navigationMenuContract.test.ts` 通过
