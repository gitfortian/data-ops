# 53: 主数据识别(数据源发现/候选/确认)

**对应需求:** requirement.md 3.2 主数据识别/design.md 3.2/menu.md 3.3|阶段:P0

**What to build:** 管理员进入"主数据管理 → 主数据识别"(menuCode `mdm-identification`),选择数据源扫描其表结构,系统按识别规则(表名/字段名与业务实体语义匹配)给出候选主数据(如 `CRM.customer` → 可能是"客户"),用户确认为某实体的主数据来源(生成 `mdm_source` MAIN 角色绑定),或排除候选。

**模块归属:** yak-ops-business-mdm(+datasource)

**Blocked by:** 51, [08 逆向导入(元数据读取能力落地)](../model/issues/08-reverse-import.md)

**Status:** in-review(实现完成,待验收)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— 契约先行(更新 mdm + datasource 契约:识别复用 datasource 元数据读取,不重复造轮子);project_id 服务端可信上下文,不建物理外键;识别为"建议"不强制,用户必须显式确认才生成来源绑定;确认的源表需校验数据源连通性;`mdm_source` 表本期建核心列,字段映射/采集方式列由 54 追加;本 ticket 注册 `mdm-identification` 菜单(V2026)。

- [x] `mdm_source` 表 Flyway 已合入(V4,字段参照 requirement.md 2.3 核心列:project_id/entity_id/datasource_id/source_table/source_role(MAIN/AUXILIARY)/status/sort_order;表身份补充 source_database/source_schema 列,保证表唯一定位)
- [x] 注册 `mdm-identification` 菜单(V2026)与页面路由,菜单契约测试通过
- [x] 选择数据源后扫描其表清单(复用 datasource 元数据读取 DataSourceCatalogReader),展示库名/表名/字段摘要
- [x] 识别规则(服务端规则,先规则后 AI):表名分词(非字母数字分隔 + 驼峰 + 复数)与实体名称/编码语义匹配,生成候选列表并标注匹配依据
- [x] 候选可"确认为主数据"(选实体,生成 MAIN 来源绑定)或"排除"(当前扫描会话内不再提示,不落库);已确认的展示在来源列表
- [x] 确认前校验数据源连通性(扫描/表存在性经 datasource 契约,失败给明确提示)
- [x] 同一实体可绑定多个来源(多源,MAIN/AUXILIARY 角色,AUXILIARY 本期仅登记,54 起参与采集)
- [x] 契约测试通过

**验证记录(2026-09-16):**

- 复用:元数据读取/连通性经 datasource 公共契约(`DataSourceCatalogReader`/`DataSourceReader`),MDM 未重复造轮子;来源绑定为松散 ID,数据源/实体展示名经跨模块契约解析(fail-open 降级)。
- 后端:`./mvnw -pl yak-ops-business/yak-ops-business-mdm -am compile` 通过;`./mvnw -pl yak-ops-boot -am validate` 通过。
- 单测:`CandidateMatcherTest` 5/5(前缀/纯表名/复数/驼峰/业务表不匹配/中文名不匹配)、`MdmSourceServiceTest` 5/5(扫描失败/重复绑定/表不存在/解绑不存在/扫描候选标注);既有 15 项(MdmEntityServiceTest 7 + MdmAttributeServiceTest 8)仍全绿,合计 25 项。
- 前端:菜单契约测试 5/5 通过(V2026 对齐);tsc 中 mdm 相关文件零错误(总数与基线一致,曾因 iconKey 'search' 不在 NavigationIconKey 联合类型引入 1 处错误,已改为 'instance' 修复)。
- 排除为前端会话态(不落库),识别规则先规则后 AI(AI 匹配属 P3 范围外)。
- Flyway 运行时迁移效果(来源表落库、V2026 菜单注册生效)需有数据库的联调环境启动应用确认。
- 遵守硬性约束:契约文件先于代码;未修改 `yak-ops-ui` 下任何 `.md` 文件。
