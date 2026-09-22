# 04: 源表结构变更检测（source-diff）界面

**对应需求:** 模型工作台盘点 §6 缺失能力（P1）| 阶段: P1

**What to build:** `GET /api/v1/modeling/models/{id}/source-diff`（`ModelingSourceDiffController` + `SourceChangeDetectionService`，有单测、口径"仅检测不自动同步"）后端完整，但**前端零调用**。逆向导入的模型（V12 来源绑定）随源表演化静默腐化，且指标反推草稿（3.5）的可追溯排查也依赖它。给绑定来源的模型补比对界面：列级 drift（新增/删除/类型变化）清单 + 引导人工修改模型结构。

**模块归属:** modeling（后端已就绪 + 前端新面）；仅只读 datasource introspection

**Blocked by:** 无；与 02 共用"对账/比对"展示面，两单开工前先对齐一次 UI 口径

**Status:** backlog(待排期)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md)；坚持"仅检测不自动同步"，不做一键覆盖模型结构（防止覆盖人工设计真相）；数据源不可达时降级提示，不阻断详情页。

- [ ] 绑定来源的模型详情页出"源表比对"入口（Tab 或按钮），调 `/source-diff`
- [ ] drift 结果按列分组展示：源新增/源删除/类型变更/一致
- [ ] 每条差异给"去表结构编辑器处理"定位跳转
- [ ] 未绑定来源/数据源不可达/表已删除三类空态文案
- [ ] 契约测试：端点响应结构与 `SourceChangeDetectionServiceTest` 口径一致
