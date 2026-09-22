# 57: 主数据清洗·标准化与补全

**对应需求:** requirement.md 3.4 主数据清洗/design.md 3.4/menu.md 3.4|阶段:P1

**What to build:** 在"主数据清洗"内(与 56 同菜单 `mdm-cleansing`)配置标准化与补全规则:标准化引用数据标准(码值映射 M/F → 1/2、单位、格式),补全规则可配(默认值补全、跨来源补全 —— A 源缺失从 B 源补)。规则执行后可预览变更。

**模块归属:** yak-ops-business-mdm(+semantic)

**Blocked by:** [56 去重与合并](./56-clean-dedup-merge.md), [30 标准表结构](../../semantic/issues/30-standard-tables.md)

**Status:** ready-for-agent

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— 契约先行(更新 mdm + semantic 契约:标准化引用标准,不自由填写,复用 semantic 码值/单位标准);project_id 服务端可信上下文,不建物理外键;规则执行为显式操作,执行前预览影响行数,执行后版本递增可追溯;补全跨来源取值需记录来源,便于追溯。

- [ ] `mdm_clean_rule` 追加 STANDARDIZE/COMPLETE 类型支持(V8:rule_type 扩展 + 规则表达式 JSON 含来源字段)
- [ ] 标准化规则配置:选属性 → 选标准(码值标准:源值→目标值映射;单位标准;格式规则),引用 semantic 标准下拉选择,不手填
- [ ] 补全规则配置:默认值补全(如等级缺失 → 默认"普通")、跨来源补全(从指定 AUXILIARY 来源取值补缺失字段)
- [ ] 规则执行:预览影响行数与变更明细 → 确认执行 → mdm_record 更新(attributes 变更 + version 递增),记录执行日志
- [ ] 规则启停、排序;规则列表按实体查看
- [ ] 契约测试通过

**验证记录(待填):**
