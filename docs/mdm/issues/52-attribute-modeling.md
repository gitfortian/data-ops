# 52: 主数据属性建模(引用数据标准)

**对应需求:** requirement.md 3.1 主数据建模/design.md 3.1|阶段:P0

**What to build:** 管理员在实体内维护主数据属性(客户名称、手机号、地址、等级等):属性角色(PK/ATTR/RELATION)、数据类型、必填、业务描述、排序。属性引用数据标准(类型/单位/码值/安全),不自由填写 —— 复用 semantic。

**模块归属:** data-ops-business-mdm(+semantic)

**Blocked by:** 51, [30 标准表结构](../../semantic/issues/30-standard-tables.md)

**Status:** in-review(实现完成,待验收)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— 契约先行(更新 mdm 契约 + semantic 契约:mdm 经 SPI 引用标准,不直接读表);project_id 服务端可信上下文,不建物理外键;属性的类型/单位/码值/安全必须从标准中选择(引用标准 ID/码集编码),不允许自由填写(同 semantic 决策 A9 松散 ID 引用,展示名经 SPI 解析);attr_code 实体内唯一,DB 兜底;跨模块数据只存松散 ID,无物理外键。

- [x] `mdm_attribute` 表 Flyway 已合入(V3,字段参照 requirement.md 2.2:attr_code/attr_name/attr_type(PK/ATTR/RELATION)/data_type/std_type_id/std_unit_id/std_code_set_code/std_security_id/is_required/business_desc/sort_order/status)
- [x] 属性列表页:按实体查看、按角色筛选、排序
- [x] 属性新增/编辑:编码(创建后锁定)、名称、角色、数据类型
- [x] 类型/单位/码值/安全引用从 semantic 标准中选择(下拉按需加载,复用 semantic 32 options 端点),不手填
- [x] 支持必填标记;同一实体 PK 角色唯一
- [x] attr_code 实体内唯一,DB 层唯一约束兜底
- [x] 属性删除校验:已被采集映射(54)/清洗规则(56)引用时阻断提示(挂点已在 MdmAttributeService.isReferenced 注释标记,54/56 落地后并入)
- [x] 契约测试通过(semantic StandardQueryApi 扩展 labels/existsCodeSet 供 MDM 消费,契约文件同步更新)

**验证记录(2026-09-16):**

- 跨模块:semantic `StandardQueryApi` 扩展 `labels(Collection<Long>)`(批量 `名称（编码）` 标签)与 `existsCodeSet(String)`(启用码集校验),MDM 只经 api 包消费,不直读 semantic 表;semantic ARCHITECTURE/REQUIREMENTS 契约已更新。
- 后端:`./mvnw -pl data-ops-business/data-ops-business-mdm -am compile` 通过;`./mvnw -pl data-ops-boot -am validate` 通过。
- 单测:`MdmAttributeServiceTest` 8/8 通过(重复编码/PK 唯一/类型标准 kind 不符/停用标准/码集不存在/合法引用落库/改 PK 阻断/不存在报错);`MdmEntityServiceTest` 7/7 仍全绿。
- 前端:菜单契约测试 5/5 通过(52 无新菜单,无变更);tsc 中 mdm 相关文件零错误(总数与基线一致)。
- Flyway 运行时迁移效果(属性表落库)需有数据库的联调环境启动应用确认。
- 遵守硬性约束:契约文件先于代码;未修改 `data-ops-ui` 下任何 `.md` 文件。
