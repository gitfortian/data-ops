# Ticket 08：数据源页目录树（P3 可选）

**对应需求：** 数据源缺失能力盘点 §6 第 8 行 | **优先级：** P3（不补也成立） | **模块：** yak-ops-ui

**What to build：** 管理员「打开这扇门看看里面有什么」：数据源卡片加「浏览目录」动作，右侧抽屉内渲染 库→(Schema)→表→字段 懒加载树，表节点提供「预览前 20 行」。

**设计：**
- 全部复用既有端点（`services/data-source/catalog.ts` + `legacy.ts` 已封装 databases/schemas/tables/columns/getTop20Data），零后端改动。
- 新组件 `pages/data-source/components/DataSourceCatalogDrawer/`：antd `Tree` `loadData` 懒加载；无 schema 概念的库类型自动跳过 schema 层（以 `GET /catalog/{id}/schemas` 返回空判定）。
- 预览用 Modal + Table（列来自 columns 接口，行为 map）。
- 权限：只需页面 READ，无新增权限码。

**验收清单**
- [ ] 入口按钮（卡片动作区，`canRead` 即可见）
- [ ] 懒加载树 + 空态/连接失败错误透出（复用全局错误通知链路）
- [ ] 预览弹窗
- [ ] `npx tsc --noEmit` 不新增错误
- [ ] 真机验证：dev server 起来后 browser-use 走一遍 MySQL 源（数据在 project 1「默认空间」，见项目记忆）

**Blocked by：** 无（可与 01-07 并行，但排最后做）。
