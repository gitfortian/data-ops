# Ticket 88：前端 · 策略管理页

**对应需求：** 3.1 / 3.2 / D8 | **阶段：** P1 | **模块：** data-ops-ui

**What to build：** 管理员打开"数据生命周期→策略管理"：首访空态给一键【从分层模板初始化】主按钮（预填演示数据即点即得）；列表展示 名称/适用范围(Tag: 分层默认·DWD)/热冷销毁摘要(永久显示∞)/粒度/引用数/最近下发/操作；新建编辑用**抽屉**：适用分层▾→选中即从 `GET /policies/layer-template` 自动预填三段与名称（D8，用户只确认不填写）；"永久"开关一键清空销毁段；删除被引用策略→错误提示内联展示前 5 个引用模型名。

**Blocked by：** 81, 82（API 可用）

**交互硬约束：** 遵守 `docs/INTERACTION_PRINCIPLES.md` 全清单——名称自动生成可改、粒度默认 DAY、数字输入用 InputNumber 带单位"天"、hot≤cold≤destroy 前端即时校验（红色内联，不弹 toast 预填违规）、危险操作（删除/初始化）Modal.confirm 说明后果。

**验收清单**
- [ ] `src/services/data-lifecycle/{api.ts,types.ts}`（HttpUtils 模式，同 metric）
- [ ] `src/pages/data-lifecycle/policy/index.tsx` + `components/PolicyEditDrawer.tsx` + `constants.ts`
- [ ] 路由/菜单已由 80 登记，本页对齐 menuCode `data-lifecycle-policy`；权限 create/update/delete 控制按钮显隐
- [ ] 列表过滤（适用范围/关键字）+ 空态引导 + 初始化结果 notifyOnce
- [ ] `npx tsc --noEmit` 不超 199 基线；浏览器手工走查（建/改/删/初始化）
