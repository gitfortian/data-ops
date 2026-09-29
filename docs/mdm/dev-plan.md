# 主数据管理（MDM）开发计划与跟踪

> 需求:[requirement.md](./requirement.md) v1.0(需求基线)+ [design.md](./design.md) v1.0(设计基线)+ [menu.md](./menu.md) v1.0(最终菜单设计)
> 核心原则(design.md 一、menu.md 一):**能复用就复用,只新建主数据特有的;通用能力复用 + 跳转**
> 拆分方式:tracer-bullet 垂直切片,每个 ticket 贯通 表结构 → API → UI → 可验收
> Tickets 目录:[./issues/](./issues/)(一个 ticket 一个文件,编号即依赖拓扑顺序,也是建议开发顺序)
> 状态:计划基线 v2.0(2026-09-16,吸收 menu.md 菜单收敛)

---

## 0. 硬性开发约束(不可打破)

> 与 [数仓建模开发计划与跟踪](../model/dev-plan.md) 第 0 节一致,本节为主数据管理适用的硬性要求,与任何交付进度冲突时以约束为准。

1. **契约先行(Contract-First)**:每个 `data-ops-business-*` 模块根目录维护契约文件集(README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW);MDM 相关 ticket 开工第一步更新 `data-ops-business-mdm/` 契约文件集(及涉及的其他模块:datasource/sync/quality/data-service/semantic/lineage/dataset/modeling),契约 diff 先于代码 diff 被审阅。新建模块:契约文件集是 ticket 50 的**第一个交付物**。
2. **前端契约文件只读**:`data-ops-ui/` 下所有 `.md` 只阅读遵守、绝不修改;路由 menuCode 过契约测试;与前端契约冲突时上报,由契约维护方更新。
3. **项目全局规范强制适用**:`CODE_STYLE.md`、`data-ops-ui/FRONTEND_CODE_STYLE.md`、`docs/architecture/PROJECT_SCOPE.md`(project_id 只取服务端可信上下文、不建物理外键)、`docs/home-overview-contract.md`(统计服务端聚合,禁止无界 list() 后内存统计)、菜单授权契约。
4. **交互原则**:`docs/INTERACTION_PRINCIPLES.md` —— 能默认就默认,能选择就不填写,能引用就不重复,能自动就手动。
5. **数据库迁移**:mdm 自建 `db/migration/yak-mdm`(V1 起编);业务表在所属 ticket 追加 V 版本,合入后禁止编辑(与 semantic/modeling 约定一致);菜单注册追加 yak-security 时 mdm 取 ≥V2024(当前最大值 V2023 之后)。

## 1. 里程碑

| 里程碑 | 阶段 | Tickets | 出口判据(演示场景) |
| --- | --- | --- | --- |
| M5 主数据管理 | P0 | 50~55 | 模块骨架 + 主数据建模(实体/属性) + 主数据识别(数据源发现/确认) + 主数据采集配置(字段映射,详情页内) + 采集执行(对接 sync,无独立菜单) |
| M5 主数据管理 | P1 | 56~59 | 主数据清洗(去重/合并/标准化/补全) + 主数据分发(配置/执行/监控) + 主数据服务(API/订阅/缓存) |
| M5 主数据管理 | P2 | 60~62 | 主数据审批(申请/审批流/版本) + 主数据治理(质量/血缘/权限) + 主数据总览与分析(总览完整化/分布/变更/使用) |
| M5 主数据管理 | P3(范围外) | — | AI 匹配、主数据市场、实时分发、跨项目共享(见 requirement.md 九) |

## 2. 状态总表(50–62)

> 状态流转:`ready-for-agent`(可开工)→ `in-progress`(进行中)→ `in-review`(待评审)→ `done`(完成)。
> **工作前沿**:从"所有阻塞均已完成"的 ticket 中领取;完成一个 ticket 后勾选其文件中的验收项并同步本表。
> 菜单列:标注该 ticket 注册的菜单(menuCode);未标注的 ticket 无独立菜单,功能经总览/详情页跳转进入(menu.md 四)。

| 编号 | Ticket | 阶段 | 阻塞于 | 菜单(menuCode) | 状态 | 模块 |
| --- | --- | --- | --- | --- | --- | --- |
| 50 | [MDM 模块骨架与菜单权限接入](./issues/50-module-scaffold-menu.md) | P0 | 无 | 组 `mdm` + `mdm-overview`(总览) | in-review | mdm |
| 51 | [主数据实体建模](./issues/51-entity-modeling.md) | P0 | 50 | `mdm-modeling`(建模) | in-review | mdm |
| 52 | [主数据属性建模(引用数据标准)](./issues/52-attribute-modeling.md) | P0 | 51, 30 | — | in-review | mdm(+semantic) |
| 53 | [主数据识别(数据源发现/候选/确认)](./issues/53-identification.md) | P0 | 51, 08 | `mdm-identification`(识别) | in-review | mdm(+datasource) |
| 54 | [主数据采集(复用 sync:来源绑定+状态展示)](./issues/54-collect-config.md) | P0 | 52, 53 | —(跳数据集成) | in-review | mdm(+sync) |
| 55 | [主数据统一记录落地(主数据加工任务,方案 A)](./issues/55-collect-execution.md) | P0 | 54 | —(跳数据集成) | in-review | mdm(+sync) |
| 56 | [主数据清洗·去重与合并](./issues/56-clean-dedup-merge.md) | P1 | 55 | `mdm-cleansing`(清洗) | in-review | mdm(+quality) |
| 57 | [主数据清洗·标准化与补全](./issues/57-clean-standardize-complete.md) | P1 | 56, 30 | — | ready-for-agent | mdm(+semantic) |
| 58 | [主数据分发(配置/执行/监控)](./issues/58-distribution.md) | P1 | 55 | —(跳数据服务) | ready-for-agent | mdm(+data-service) |
| 59 | [主数据服务(API/订阅/缓存)](./issues/59-service.md) | P1 | 55 | —(跳数据服务) | ready-for-agent | mdm(+data-service) |
| 60 | [主数据审批(申请/审批流/版本)](./issues/60-approval.md) | P2 | 55 | `mdm-approval`(审批) | ready-for-agent | mdm |
| 61 | [主数据治理(质量/血缘/权限)](./issues/61-governance.md) | P2 | 55 | —(跳数据质量/血缘) | ready-for-agent | mdm(+quality/lineage/security) |
| 62 | [主数据总览与分析(分布/变更/使用)](./issues/62-overview-analysis.md) | P2 | 55, 58, 59 | —(总览完整化) | ready-for-agent | mdm(+dataset) |

## 3. 关键决策

| # | 决策 | 来源 |
| --- | --- | --- |
| D-M1 | **复用优先**:能复用就复用,只新建主数据特有的。数据源接入/元数据读取复用 datasource、采集/映射/调度复用 sync、质量规则复用 quality、API/缓存复用 data-service、数据标准引用复用 semantic、血缘复用 lineage、权限复用 security、资产/统计复用 dataset;MDM 只新建:实体、属性、关系、识别规则、采集配置、去重/合并/标准化/补全、审批流/版本、分发配置、订阅、少量治理与分析 | design.md 一/三 |
| D-M2 | 依赖方向单向:`mdm → datasource/sync/quality/data-service/semantic/lineage/security/dataset`,`modeling → mdm`(数仓维表引用主数据)。不允许反向依赖 | design.md 二 |
| D-M3 | **错误码段调整**:MDM 错误码用 **44001+** 段,design.md 规划的 43001-43006 **不采纳**(43001-43018 已被 `ResourceErrorCode` 资源管理占用);语义与 design.md 九一致:实体不存在/记录不存在/属性不存在/来源不存在/审批失败/分发失败 | 2026-09-16 拆票时核对 common 错误码注册表 |
| D-M4 | **Flyway 表归属随 ticket**:V1 为模块基线(只立历史表,与 modeling 01 一致),业务表在其所属 ticket 追加 V 版本(与 semantic/modeling 一致);design.md 的 `V1__init_mdm_tables/V2__init_mdm_indexes/V3__preset_mdm_templates` 三文件规划不采纳(与"表在所属 ticket 建、合入后禁止编辑"的垂直切片约定冲突) | 2026-09-16 拆票 |
| D-M5 | **清洗规则表新增** `mdm_clean_rule`(entity_id + 规则类型 DEDUP/STANDARDIZE/COMPLETE + 规则表达式 JSON + 排序):design.md 六张表(entity/attribute/source/record/change/distribution)未含,去重/标准化/补全规则是主数据特有配置,独立表便于 CRUD 与审计 | 2026-09-16 拆票 |
| D-M6 | 全部业务表项目级(project_id,PROJECT_REQUIRED,与平台一致,同 semantic 决策 A8);project_id 只取服务端可信上下文,不建物理外键 | PROJECT_SCOPE + semantic 先例 |
| D-M7 | 主数据核心口径(requirement.md 七):master_id 跨系统唯一(D3)、source_ids 记录各系统原始 ID(D4)、主数据从业务库取不从数仓取(D1)、变更需审批(D5)、分发回业务系统(D6)、对外用 API(D7) | requirement.md 七 |
| D-M8 | **菜单收敛 9→5**(menu.md):主数据管理只建 5 个特有菜单 —— 总览 `mdm-overview`、建模 `mdm-modeling`、识别 `mdm-identification`、清洗 `mdm-cleansing`、审批 `mdm-approval`;采集→数据集成、质量→数据质量、血缘→数据血缘、服务→数据服务、标准→语义中心、分析→仪表盘,全部跳转;总览为项目内入口页,统计服务端聚合(home-overview-contract)。**menuCode 采用连字符**(mdm-overview/mdm-modeling/...),与仓库既有 menuCode 约定一致,不采纳 menu.md 的下划线建议(mdm_overview) | menu.md 二/八 |
| D-M9 | **采集复用 sync(2026-09-16 修正)**:MDM **不做**采集配置/字段映射/执行/调度——全部复用 sync(数据集成,design.md 3.3 字段映射 ✅ 复用、menu.md 详情页"采集配置"Tab 跳数据集成);MDM 只保留来源→实体绑定(53)与采集状态展示(跳数据集成);执行状态回写 mdm_source/总览随 55 集成点确认 | menu.md 三/五 + design.md 3.3 |
| D-M10 | **采集配置口径修正(D-M9 落实,2026-09-16 用户确认)**:54 原实现(MDM 字段映射编辑器 + V5 配置列 + 配置端点)与 sync 字段映射能力**重复建设**,违反复用契约,整体回退(`git revert a8d972c3c`);采集配置/执行完全走 sync,MDM 只做来源绑定+状态展示+跳转;55 改为"**消费 sync 结果落 mdm_record(不建执行引擎)**",先确认 sync→mdm_record 集成点 | 2026-09-16 用户评审 |
| D-M11 | **采集执行与统一记录归属(方案 A,2026-09-16 用户确认)**:① 采集执行彻底复用 sync——sync 任务(离线+实时)打「主数据」标签(`is_master_data` + 关联实体维度),MDM **只查询展示**标签任务执行状态(离线 `OfflineExecutionEvent`/实时执行记录),不建采集、不建执行引擎、不消费落库;② 统一主数据表 `mdm_record`(master_id/source_ids)由 **主数据加工任务**生成:MDM 按实体/属性/来源绑定生成加工任务(含 master_id 唯一、source_ids 多源关联的统一逻辑),交数据开发执行,参照 modeling 44 派生建模→生成加工任务→任务目录(task-catalog)的成熟模式;55 按此重定义 | 2026-09-16 用户评审 |

## 4. 菜单与权限注册预留

> 菜单注册全部走 Yak Ops 自有 Flyway(追加 yak-security),**V2024 起编,按 ticket 逐条注册**;前端契约测试要求前端 menuCode 与 Flyway 目录精确对齐,每个注册 migration 需同步加入 `navigationMenuContract.test.ts` 的 `CATALOG_EXTENSION_MIGRATIONS`。

| V 版本 | Ticket | 菜单/权限 |
| --- | --- | --- |
| V2024 | 50 | 组 `mdm`(主数据管理)+ `mdm-overview`(主数据总览)页;权限根 `mdm` + `mdm:read/create/update/delete` |
| V2025 | 51 | `mdm-modeling`(主数据建模)页 |
| V2026 | 53 | `mdm-identification`(主数据识别)页 |
| V2027 | 56 | `mdm-cleansing`(主数据清洗)页 |
| V2028 | 60 | `mdm-approval`(主数据审批)页 |

> 采集/服务/治理/分析**不注册菜单**(menu.md 七,通用能力跳转);其路由为隐藏路由(hidden + parentId),无独立 menuCode。

## 5. 跟踪约定

1. 每个 ticket 一个文件,包含:What to build(用户视角端到端行为)、Blocked by、硬性约束、验收清单。
2. 领取 ticket 后**先读第 0 节硬性约束与涉及模块的契约文件**,再开工;完成一项验收就勾选一项。
3. 状态与负责人变更同步更新第 2 节状态总表;ticket 文件的 Status 字段与总表保持一致。
4. 阻塞引用:语义 ticket 相对路径 `../../semantic/issues/<file>.md`,建模 ticket 相对路径 `../model/issues/<file>.md`。
5. 如后续迁移到 GitHub Issues:按本表逐条建 issue,以 issue 链接替换"阻塞于",并在本表回填链接。

## 6. 变更记录

| 日期 | 版本 | 变更 |
| --- | --- | --- |
| 2026-09-16 | v1.0 | 初版:13 个 ticket(P0×6 / P1×4 / P2×3)+ 硬性开发约束 + 关键决策 D-M1~D-M7(含错误码段 44001+、Flyway 表归属随 ticket、清洗规则表新增) |
| 2026-09-16 | v2.0 | 吸收 menu.md 菜单收敛:9→5 个特有菜单,采集/服务/治理/分析改跳转(新增决策 D-M8/D-M9);菜单注册表改为 V2024-V2028(5 条);ticket 62 改为"主数据总览与分析"(总览完整化 + 分布/变更/使用统计) |
