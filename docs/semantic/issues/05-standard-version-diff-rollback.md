# 05: 标准版本字段级对比与回滚

**对应需求:** 语义中心盘点 §6 缺失能力（P2）| 阶段: P2

**What to build:** 标准每次修改前把完整旧状态写入 `yak_semantic_standard_version`(payload_json,V3),但前端只有查看抽屉(`StandardVersionsDrawer`),V3 注释明示"Rollback is view-only this phase",也无 diff。误改标准只能照抄旧 JSON 手工回填。补齐:任意两版本字段级对比 + 一键回滚。

**模块归属:** semantic

**Blocked by:** 32 标准管理界面(版本抽屉已存在)

**Status:** backlog(待排期)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md);回滚**复用 PUT `/standards/{id}` + 乐观锁 version**,走同一套 `StandardKind.validateRequired` 校验与版本快照写入,不新开旁路端点。

- [ ] 版本抽屉支持选两个版本做字段级 diff(新增/修改/删除,含类别专有列;CODE 类按码集整体对比)
- [ ] "回滚到此版本":服务端按快照 payload 经正常更新链路写回,回滚动作本身产生新版本记录
- [ ] 回滚前展示下游影响提示(引用情况/用度口径与详情页一致)
- [ ] 并发冲突由乐观锁拦截并提示刷新重试
- [ ] 契约测试通过
