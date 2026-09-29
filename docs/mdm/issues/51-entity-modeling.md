# 51: 主数据实体建模

**对应需求:** requirement.md 3.1 主数据建模/design.md 3.1/menu.md 3.2|阶段:P0

**What to build:** 管理员进入"主数据管理 → 主数据建模"(menuCode `mdm-modeling`),维护主数据实体(客户、商品、供应商等):增删改查、编码唯一、状态(草稿/生效/停用)、负责人、描述。实体是属性(52)、识别(53)、采集(55)的归属根。实体列表 → 实体详情页(一站式视图骨架,记录/采集/质量/分发/血缘/标准/变更 Tab 随后续 ticket 填充)。

**模块归属:** data-ops-business-mdm

**Blocked by:** 50

**Status:** in-review(实现完成,待验收)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— 契约先行(开工第一步更新 `data-ops-business-mdm/` 契约文件集:DOMAIN/REQUIREMENTS 增补实体不变量);project_id 只取服务端可信上下文,不建物理外键;entity_code 项目内唯一,DB 层唯一约束兜底;业务表在所属 ticket 追加 V 版本,合入后禁止编辑;本 ticket 注册 `mdm-modeling` 菜单(V2025)。

- [x] `mdm_entity` 表 Flyway 已合入(V2,字段参照 requirement.md 2.1:project_id/entity_code/entity_name/description/status/owner/create_time/update_time)
- [x] 注册 `mdm-modeling` 菜单(V2025)与页面路由,菜单契约测试通过
- [x] 实体列表页:分页、按编码/名称搜索、按状态筛选
- [x] 实体新增/编辑:编码(创建后锁定)、名称、描述、负责人;状态流转(草稿→生效→停用)
- [x] entity_code 项目内唯一,DB 层有唯一约束兜底
- [x] 实体删除校验:已被属性(52)/来源(53)引用时阻断提示(挂点已在 MdmEntityService.isReferenced 注释标记,52/53 落地后并入)
- [x] 实体详情页骨架:概览(名称/状态/负责人/创建时间)+ 空态 Tab(属性/记录/采集/质量/分发/血缘/标准/变更,随后续 ticket 填充)
- [x] 契约测试通过(REQUIREMENTS.md 已增补 Ticket 51 行为要求;错误码 44007-44012 已登记)

**验证记录(2026-09-16):**

- 后端:`./mvnw -pl data-ops-business/data-ops-business-mdm -am compile` 通过(域对象/仓储/适配器/服务/控制器/API);`./mvnw -pl data-ops-boot -am validate` 通过。
- 单测:`MdmEntityServiceTest` 7/7 通过(编码格式/重复编码/名称必填/DRAFT 落库/不存在报错/状态回退阻断/生效停用流转)。
- 前端:菜单契约测试 `navigationMenuContract.test.ts` 5/5 通过(V2025 已登记,mdm-modeling 与 Flyway 目录对齐);tsc 中 mdm 相关文件零错误(总数与基线一致,零新增)。
- Flyway 运行时迁移效果(实体表落库、V2025 菜单注册生效)需有数据库的联调环境启动应用确认。
- 遵守硬性约束:契约文件先于代码;未修改 `data-ops-ui` 下任何 `.md` 文件。
