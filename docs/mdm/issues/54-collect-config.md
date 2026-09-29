# 54: 主数据采集(复用 sync:来源绑定 + 状态展示,不建映射编辑器/执行引擎)

**对应需求:** requirement.md 3.3 主数据采集/design.md 3.3/menu.md 三.1(跳数据集成)|阶段:P0

**What to build:** 采集执行与状态展示**完全复用「数据集成」(sync,离线+实时)**(design.md 3.3:同步任务/字段映射/调度 = 复用 sync;menu.md:采集不建菜单、详情页"采集配置"Tab 跳数据集成)。MDM 只保留主数据特有的部分:**来源→实体绑定(53 已有)** 与 **采集状态展示**。实体详情页"采集"Tab 展示来源绑定列表(角色/采集状态占位)并提供"前往数据集成"跳转;采集状态(最近采集时间)经 sync「主数据」标签任务执行状态查询回写(离线+实时,dev-plan D-M11,随 55 落地)。

**模块归属:** data-ops-business-mdm(+sync)

**Blocked by:** [52 属性建模](./52-attribute-modeling.md), [53 识别](./53-identification.md)

**Status:** in-review(口径修正完成,待验收)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) 与 dev-plan D-M10(2026-09-16 修正):**MDM 不建字段映射编辑器、不建采集执行引擎、不建调度**——字段映射/调度/执行全部复用 sync(数据集成),MDM 只做来源绑定与采集状态展示/跳转;复用优先(design.md 一);project_id 服务端可信上下文,不建物理外键。

- [x] 详情页"采集"Tab:来源绑定列表(数据源/表/角色)+ 采集状态占位 + "前往数据集成配置采集"跳转
- [x] **不新增** MDM 采集配置表/列:字段映射/方式/频率/配置状态列 V5 已回退(回退提交见下),mdm_source 保持 53 的核心列
- [x] 总览"采集状态"卡片口径已记录(menu.md 三.1:查 sync 标签任务状态,随 55 回写)
- [x] 契约测试通过(REQUIREMENTS.md 已按修正口径更新)

**验证记录(2026-09-16,口径修正):**

- 用户确认(2026-09-16):54 原实现(MDM 字段映射编辑器 + V5 配置列 + 配置端点)与 sync 字段映射能力**重复建设**,违反复用契约;按 `git revert a8d972c3c` 整体回退(MdmCollectConfigApi/MdmConfigStatus/V5 迁移/配置端点/CollectConfigTab 等全部移除),替换为本票的"来源绑定 + 状态展示 + 跳转"形态。
- 回退后验证:`MdmSourceServiceTest` 回到 5/5,合计 25 项单测全绿;`./mvnw -pl data-ops-business/data-ops-business-mdm -am test` 通过。
- 前端:详情页"采集"Tab 改用 `CollectStatusTab`(来源列表 + 跳数据集成),tsc 无新增错误。
- 采集状态回写(最近采集时间/状态)经 sync「主数据」标签任务执行状态查询(离线+实时,dev-plan D-M11),随 55 落地。
- 遵守硬性约束:未修改 `data-ops-ui` 下任何 `.md` 文件。
