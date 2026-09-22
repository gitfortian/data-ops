# 61: 主数据治理(质量/血缘/权限)

**对应需求:** requirement.md 3.8 主数据治理/design.md 3.8/menu.md 四(跳数据质量/血缘)|阶段:P2

**What to build:** 主数据治理视图:质量检查(完整性/格式/重复,复用 quality 模块,展示检查结果)、血缘(上游:主数据来自哪些源系统/source_ids;下游:被哪些系统分发/数仓维表引用,复用 lineage)、权限(谁能改/看主数据,复用 security RBAC + 实体 owner)。**无独立菜单**:入口在实体详情页 Tab(menu.md 五),质量/血缘跳平台模块,MDM 只做少量特有治理(如实体 owner 权限、来源血缘聚合展示)。

**模块归属:** yak-ops-business-mdm(+quality/lineage/security)

**Blocked by:** [55 采集执行](./55-collect-execution.md)

**Status:** ready-for-agent

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— 契约先行(更新 mdm + quality/lineage 契约:质量复用 quality 规则引擎、血缘复用 lineage,MDM 不重复造轮子,只做聚合展示与主数据特有治理);project_id 服务端可信上下文,不建物理外键;质量/血缘数据服务端聚合,禁止无界 list() 后内存统计;权限基于平台 RBAC(菜单权限),实体级 owner 为 MDM 特有轻量控制。

- [ ] 实体详情页"质量"Tab:展示实体质量检查结果(完整性/格式/重复,复用 quality),可跳数据质量模块配置/执行检查
- [ ] 实体详情页"血缘"Tab:上游来源(各源系统 source_ids 分布,复用 datasource 元数据 + source_ids)、下游使用(分发目标系统数、订阅方、数仓维表引用,复用 lineage/分发记录)
- [ ] 权限:MDM 页面走平台 RBAC(菜单权限 mdm:read/create/update/delete);实体 owner 作为编辑权限的轻量控制(owner 或管理员可改实体配置)
- [ ] 变更审计:实体/记录/配置的关键操作留痕(复用 audit 模块)
- [ ] 契约测试通过

**验证记录(待填):**
