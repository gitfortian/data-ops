# 58: 主数据分发(配置/执行/监控)

**对应需求:** requirement.md 3.6 主数据分发/design.md 3.6/menu.md 四(跳数据服务)|阶段:P1

**What to build:** 配置主数据分发(目标系统、分发内容、分发方式 API/MESSAGE/FILE、分发频率),执行分发(增量/全量,复用 data-service API 能力),监控分发状态(最近分发时间、成功/失败条数、重试)。**无独立菜单**:配置与监控界面位于实体详情页"分发配置"Tab(menu.md 五),总览"分发状态"卡片展示订阅/分发概览并跳数据服务。

**模块归属:** data-ops-business-mdm(+data-service)

**Blocked by:** [55 采集执行](./55-collect-execution.md)

**Status:** ready-for-agent

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— 契约先行(更新 mdm + data-service 契约:API 方式复用 data-service 能力,不重复造轮子;消息参照 alert、文件参照 storage/resource 能力);project_id 服务端可信上下文,不建物理外键;分发不阻断:单目标失败不影响其他目标,失败可重试;监控数据服务端聚合,禁止无界 list() 后内存统计。

- [ ] `mdm_distribution` 表 Flyway 已合入(V9,字段参照 requirement.md 2.6:project_id/entity_id/target_system/distribute_mode(API/MESSAGE/FILE)/distribute_freq/status/last_distribute_time/create_time/update_time)
- [ ] 实体详情页"分发配置"Tab:新增/编辑分发配置(目标系统、方式、频率、分发内容范围:全量/增量/按字段)
- [ ] 分发执行:手动触发 + 按频率调度;API 方式复用 data-service 能力,消息/文件方式参照平台已有能力
- [ ] 分发结果记录:成功/失败条数、失败明细、耗时;失败支持重试(单目标)
- [ ] 分发监控:状态、最近分发时间、失败率;总览"分发状态"卡片展示(每实体订阅/分发目标数,跳数据服务)
- [ ] 分发配置状态:草稿→生效→停用
- [ ] 契约测试通过

**验证记录(待填):**
