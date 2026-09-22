# 55: 主数据统一记录落地(主数据加工任务,方案 A)

**对应需求:** requirement.md 3.3 主数据采集(D3/D4 核心)/dev-plan D-M11|阶段:P0

**What to build:** 统一主数据表 `mdm_record`(master_id 跨系统唯一、attributes 属性值、source_ids 各系统原始 ID、版本)由 **主数据加工任务**生成:MDM 按实体/属性/来源绑定**生成加工任务**(含 master_id 唯一、source_ids 多源关联的统一逻辑),交**数据开发**执行写入 mdm_record——参照 modeling 44"派生建模 → 生成加工任务 → 任务目录(task-catalog)"的成熟模式,MDM **不建执行引擎、不建采集**。采集执行与状态彻底复用 sync(离线+实时)标签任务,MDM 只查询展示。

**模块归属:** yak-ops-business-mdm(+sync/数据开发/task-catalog)

**Blocked by:** [54 采集(复用 sync)](./54-collect-config.md), [53 识别](./53-identification.md), [22 加工任务接入数据开发](../model/issues/22-processing-task-integration.md)(机制参照)

**Status:** in-review(55a 实现完成,待验收;55b sync 标签为后续增量)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) 与 dev-plan D-M9/D-M10/D-M11:MDM 不建执行引擎/调度/采集;核心口径(D-M7):master_id 跨系统唯一(D3)、source_ids 记录各系统原始 ID(D4)、主数据从业务库取(D1);多源同名数据按 source_ids 关联而非无脑新增;契约先行(涉及 mdm/sync/数据开发/task-catalog 契约同步更新);project_id 服务端可信上下文,不建物理外键;异步任务必须能独立恢复项目上下文(PROJECT_SCOPE)。

- [x] `mdm_record` 表 Flyway 已合入(V6,字段参照 requirement.md 2.4:project_id/entity_id/master_id/attributes(JSON)/source_ids(JSON)/status(ACTIVE/MERGED/DELETED)/version/create_time/update_time);(project_id, entity_id, master_id) 唯一约束
- [x] **生成主数据加工 SQL**(55a):按实体/属性/来源绑定生成(参照 modeling 44 模式);master_id=MD5(实体编码+PK 属性值)确定性统一(D3),source_ids=JSON_OBJECT(datasource_id, PK 原始值)(D4),attributes=JSON_OBJECT(属性编码, 源列,列名=属性编码约定),UPSERT 到 mdm_record(version 递增)
- [x] 记录只读分页查询(实体详情"记录"Tab);"生成加工 SQL"入口:复制或跳数据开发新建任务草稿(复用数据开发已有 API,22 模式,执行引擎归数据开发)
- [ ] **55b(sync 标签,后续增量)**:sync 离线+实时任务增加「主数据」标签(`is_master_data`+实体维度)与执行状态查询 API(离线 `OfflineExecutionEvent`/实时执行记录),MDM 查询展示采集状态并回写 mdm_source/总览"采集状态"卡片(跳数据集成)——依赖 sync 契约改动,待立项
- [ ] 采集执行日志/状态不阻断(随 55b)
- [x] 契约测试通过(REQUIREMENTS.md 已增补 55a/55b 拆分)

**验证记录(2026-09-16):**

- 后端:`./mvnw -pl yak-ops-business/yak-ops-business-mdm -am test` 全绿 —— 新增 `MdmMasterSqlGeneratorTest` 6/6(master_id 口径/attributes JSON/source_ids(D4)/读取源表+UPSERT 递增/缺 PK 拒绝/缺源表拒绝);合计 31 项单测全绿;`./mvnw -pl yak-ops-boot -am validate` 通过。
- 前端:实体详情"记录"Tab(分页/状态筛选/master_id 搜索)+ 生成加工 SQL 弹窗(复制/跳数据开发 `/data-development/task/new`);tsc 中 mdm 相关文件零错误(总数与基线一致)。
- 加工 SQL 仅生成文本不执行;执行引擎归数据开发(22/44 模式)。
- 55b 依赖 sync 模块契约改动(任务标签字段 + 状态查询 API),未随本票实现,已记录为后续增量。
- Flyway 运行时迁移效果(V6 记录表落库)需有数据库的联调环境启动应用确认。
- 遵守硬性约束:契约文件先于代码;未修改 `yak-ops-ui` 下任何 `.md` 文件。
