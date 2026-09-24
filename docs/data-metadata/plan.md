# 元数据中心（Metadata Center）—— 生产级落地方案

> 需求：**一个统一的可扩展实体目录**——物理元数据与内部元数据（模型 / 数据标准 / 业务域 /
> 表→字段层级）全部归类为可扩展实体，统一采集/注册、统一存储、统一检索，并向上游建模供给事实。
> 参考实现：[OpenMetadata 蒸馏笔记](./openmetadata-distill.md)（下称"蒸馏 §x"）。
>
> 现状定位：平台已有**逻辑层**元数据（modeling 模型列、semantic 标准字段、metric 指标），
> 但它们分散在各模块自己的列表页里，**没有任何一处能"一次查到全平台有什么"**；
> 物理层更是完全缺失——"数据源里真实存在哪些表、哪些列、什么型、多少量、什么时候变的"。
> 目前这个空洞靠 `DataSourceCatalog` 的**实时只读 + 进程内缓存**顶着
> （`DataSourceCatalogMetadataCache`，TTL 过期即蒸发），所以**能读到 ≠ 采集到**：
> 没有历史、没有对比、没有搜索、没有血缘挂载点。
>
> 所以本方案两件一起做：**补上物理侧这块缺失的事实**，
> 并把它与已有的逻辑侧实体收进同一个目录（范围修正的来龙去脉见 §1.1）。

---

## 0. 硬性开发约束（不可打破）

> 与 [asset dev-plan.md §0](../data-asset/dev-plan.md) 同等效力；与交付进度冲突时以约束为准。

1. **契约先行**：`yak-ops-business-metadata` 根目录维护契约文件集
   （README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW，参照 asset 模块同名 6 文件）；
   每个 ticket 开工第一步更新契约文件集，**契约 diff 先于代码 diff**。
2. **不建第二套真相（最高优先）——判别式见 §1.3**：
   元数据模块对某类实体**只有在"没有其他模块拥有它"时才是 owner**，否则只存**目录投影**
   （检索/展示/治理所需的少量字段 + 指向源域的指针 + 源侧指纹），详情实时读源域 SPI。
   - 物理 `table` / `tableColumn`：元数据模块 own，全属性可存。
   - `dataModel` / `standardField` / `domain` / `metric`：源域 own，
     **目录里绝不出现**模型的列定义、指标公式、标准的字典项。
   - 图节点/边**只在** `yak_metadata_asset` / `yak_metadata_relation`（lineage 基线）。
     元数据模块**不得**新增资产表或血缘边表；登记后只存 lineage 返回的 id 作为外键列
     （无物理 FK，同 lineage 惯例）。
   - 存储字节量**只在** `yak_lc_storage_snapshot`（lifecycle）。元数据模块**只读复用**，
     禁止自己 `SHOW DATA`。依据 asset《信息地图》原句：
     *"源域已有聚合/快照的 → 复用其成果加读接口，禁止自建第二份采集（如存储量）"*。
   - 标准字段**只在** semantic（`semantic/field/StandardField.java`）。元数据只出物理列，
     不参与"是否标准"的判定。
3. **跨模块依赖只走 `api` 包**：消费者不得 import 兄弟模块内部包。
   现状违规先例：`modeling/governance/StandardFieldMatcher.java:3` 直接
   `import io.yak.ops.business.semantic.field.StandardField`。
   **本模块不得新增此类依赖**，且见 §9 陷阱 T6。
4. **写别人的表 = 死罪**。必须走对方 API/SPI。
   本条不是抽象规约，是本仓库本周踩过的坑：灌数脚本 `docs/semantic/harness_semantics.py`
   曾直插 `yak_ops_data_source` 并写错枚举字面量（`'SUCCESS'`/`'DEV'`——
   `DataSourceConnStatus` 只有 `UNKNOWN/CONNECTED/DISCONNECTED`），
   一行脏数据毒死了整个 `*/page` 接口（兜底 999）。见 `docs/semantic/ux-review-2026-09-18.md` A2。
   **采集器同理**：哪怕只是"顺手补一行数据源名"，也只能通过 provider 读。
5. **数据库迁移**：自持 `db/migration/yak-metadata`，V1 起编，历史表
   `flyway_schema_history_metadata`，Bean 名 `yakMetadataFlyway`（模板：
   `asset/config/AssetPersistenceConfiguration.java:25-35`）。
   菜单注册进 yak-security 链，取 **V2033__register_data_metadata_menu.sql**
   （V2030 security / V2031 lifecycle / V2032 asset 已占用）。
6. **错误码段 49001~49099**（44xxx mdm/metric、45xxx security、46xxx alert、47xxx lifecycle、
   48xxx asset 已占）；载体 `yak-ops-common/…/enums/metadata/MetadataErrorCode.java`。
   权限码 `data-metadata:read/create/update/delete`。
7. **调度 namespace** `YakScheduleNamespaces.DATA_METADATA = "yak-ops-metadata"`
   （现有 5 个常量中无此项）。
8. **表前缀 `yak_md_`**；PO 落 `yak-ops-common/…/bean/po/metadata/`；
   枚举/常量落 `…/constant/metadata/`。
9. **项目隔离**：所有列表/详情接口 `@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)`；
   `project_id` 只取服务端可信上下文（`CurrentProject.requireProjectId()`，
   `yak-ops-core/…/project/CurrentProject.java:19`），**永不接受前端传入的 projectId**；
   不建物理外键（`docs/architecture/PROJECT_SCOPE.md`）。
10. **分页统一**：Controller 返回 `Result<PagingData<VIEW>>`，值由
    `PagingData.from(pageData)` 产生。**直接返回裸 `PageData` 会被 Jackson 判
    "No serializer … FAIL_ON_EMPTY_BEANS"，再被模块 advice 兜成 999**——
    这是本仓库已发生的真实事故形态，`MetadataLayeringConventionTest` 需守。
11. **无界禁止**：采集游标分批 ≤500；概览查询 ≤8 次；列表一律分页；
    单库表数超阈值必须分页拉取而非一次 `listTables` 全载入内存。
12. **前端契约文件只读**：`yak-ops-ui/**.md` 绝不修改；新菜单 menuCode 同步登记
    `src/constants/securityMenuCodes.ts` 并过 `navigationMenuContract.test.ts`。
13. **交互原则**（`docs/INTERACTION_PRINCIPLES.md`）：FQN/采集任务编码自动生成；
    采集范围默认从语义分层配置带出（能默认就不留空）；删除/忽略类批量操作必经预览确认；
    规则必须试跑（dry-run）后才可启用。

---

## 1. 边界：一个统一的可扩展实体目录

### 1.1 需求修正（2026-09-19 评审）

初稿把范围限定在"物理元数据"，这是错的：**采集对象不止外部数据源，
还包括内部模型、数据标准、业务域、表→字段层级等，且要求全部归类为可扩展实体、统一检索。**

这个要求直接否掉了"每种元数据一张归一化表"的做法：
若 `yak_md_physical_table` / `yak_md_physical_column` / `yak_md_model` / `yak_md_standard_field` 各自成表，
"统一检索"就退化成 N 路联合查询 + 人工合并排序，且**每加一类元数据就要新表 + 新代码 + 新检索分支**。

所以本方案的地基改为 **OM 的实体模型**（蒸馏 §1.3、§2.2）：
**一张统一实体表 + 类型判别列 + 属性 json + 生成列 + 扩展字段定义表。**
加一类元数据 = 插一行类型定义 + 写一个 provider；加一个字段 = 插一行字段定义，**不改表结构**。

### 1.2 做 / 不做

| 做 | 不做（明确排除） |
|---|---|
| **统一实体目录**：外部物理表/列 + 内部模型/标准字段/业务域/指标/数据集/看板，全部同类登记 | 把源域业务内容复制进目录（列清单、模型 SQL）→ 只存**目录投影**，见 §1.3 |
| **元模型驱动的可扩展性**：类型与扩展字段作为数据新增 | JSON Schema → 代码生成（蒸馏 §1.2 判 ❌，与元模型无关） |
| 跨类型**统一检索**（一次查询命中所有类型，类型是 facet） | Elasticsearch → MySQL FULLTEXT 起步，阈值见 §4.4 |
| 物理侧**定时采集**（SPI）+ 内部侧**写时登记**（push，定时仅作对账兜底，§11.1.6） | 连接器进程外批量 HTTP（蒸馏 §0） |
| 实体层级（表→列 = `parent_asset_id`）与 FQN 规范 | 每类元数据一张表（表爆炸，蒸馏 §2.2） |
| 采集增量指纹、变更历史、字段增删检测 | 采集**存储字节量** → 复用 lifecycle 快照（§0.2） |
| 标签溯源、带过期认证、治理待办 | Flowable/BPMN、OWL/RDF、DataProduct Port |
| 为 modeling 提供**符合性对账**底座 | 自动改写模型（只报差异） |
| 为数据内容采样**预留字段定义**（§2.2 的 `field_def`） | 数据内容采样本身（profile / 直方图）→ 一期不做 |

### 1.3 目录不是第二真相：投影 vs 归属（本方案最重要的一条线）

统一实体表会被质疑"又把模型抄了一份"。切分线必须写成可执行的判别式：

> **元数据模块对某类实体，只有当"没有其他模块拥有它"时才是 owner；
> 否则只存"目录投影"——检索/展示/治理所需的少量字段 + 指向源域的指针 + 源侧指纹。**

| 实体类型 | 归属 | 目录里存什么 | 详情列清单从哪来 |
|---|---|---|---|
| `table`（物理） | **元数据模块 own** | 全属性（type/comment/datasource/db/…） | 目录本身 |
| `tableColumn`（物理） | **元数据模块 own** | 全属性（型/长/可空/序/PK/注） | 目录本身 |
| `dataModel`（逻辑模型） | modeling own | displayName/summary/owner/domain/status/**源指纹** | **实时** SPI 调 modeling |
| `standardField`（数据标准） | semantic own | 同上 | 实时 SPI |
| `domain`（业务域） | semantic own | 同上 | 实时 SPI |
| `metric` | metric own | 同上 | 实时 SPI |

**目录里绝不出现**：模型的列定义、指标的公式、标准的字典项。
这些一改就漂移、且已有 owner，抄一份就是 asset dev-plan D1 违反项（"禁止把源域业务事实复制为第二真相"）。
`content_hash` 的职责因此有两种，且必须分开命名：
- 物理实体：`content_hash` = 结构指纹（§3.3），由元数据模块自己算、自己判增量。
- 投影实体：`source_hash` = 源域摘要，由**源域产出**（写时登记随请求交出，对账时由 provider 批量产出），
  元数据模块只比对是否变化，不参与语义。

**投影实体"什么时候进目录"不是实现细节，是语义**（2026-09-19 定，§11.1 第 6 条）：
**源域写成功后登记（push）**，定时拉取降级为**对账**（补漏、刷 `source_hash`、判 GONE）。
判据来自本仓库既有约定而非新发明：内部实体进 lineage 走的就是写路径
（modeling `ModelingLineageController.java:56/80` 的 `registerModel(...)`、
dataset/analysis/dashboard 三个 `*LineageSynchronizer.syncCurrent(detail)`），
**没有任何一个内部实体是靠定时任务捞进图的**。
反面理由很直白：改完模型名当下就该搜得到，定时采集会把"搜不到刚改的东西"变成日常。

**本表的"own"指语义所有权（谁定义并写入这个实体的业务字段），不是物理表所有权。**
B 案之后这两个词必须分开说：`yak_metadata_asset` 这张 MySQL 表的基线与 Flyway 版本归 lineage
（`db/migration/yak-lineage`），但表里 `dataModel` 行的 `display_name/summary/owner_user` 归 modeling 所有，
`table` 行的结构属性归元数据模块所有。混用会导致两种对称的错误：
① 元数据模块以为"表里有这列"就能给任意类型写业务字段；② lineage 以为目录列的语义也归它解释。
物理所有权只决定**谁能改这张表的结构**，语义所有权决定**谁能改某一行的某一列**——
后者的落地形式是 §2.3 后果 2 与后果 5 的列归属清单。

这也回答了"内部实体怎么进目录"：**不是元数据模块去读别人的库，
而是各源域实现一个 provider 把自己的目录投影交上来**——与 asset 的 `AssetProvider` 完全同构，
且采集/注册两条路径共用同一套指纹与 GONE 机制（§3）。

### 1.4 一期验收的现实锚点（2026-09-19 本机 `yak_security` + 各业务库实测）

| 事实 | 实测值 | 对方案的影响 |
|---|---|---|
| 数仓五库 `ods/dwd/dim/dws/ads` | **0 张表** | 物理侧端到端验证只能走业务库 |
| 非系统库 16 个（`data_ops` 132 表 / `udata_ops` 108 / `test` 55 / `bemodel_platform` 33 / …） | **373 表 / 4684 列** | 列/表 ≈ **12.6:1** → §4.6 的排序问题一期就要面对 |
| 内部实体存量：`yak_modeling_model` 40、`yak_semantic_field` 148、`yak_semantic_domain` 28、`yak_metric` 4 | 合计 **220** | provider 注册路径有真实数据可验，不必造样例 |
| `yak_metadata_asset` 已有 **234** 个图节点；`yak_asset_item` **0** 行 | — | §2.5 B 案是"给一张已有内容的表加列"，不是"给空表立规矩"；asset 台账还是空的，说明**发现层缺位正是现在的瓶颈** |
| 上面 234 行的构成：**`COLUMN` 219**（MODELING 211 + SEMANTIC 8）/ **`TABLE` 11** / **`METRIC` 4**；键前缀 `modeling:` 222 / `semantic:` 8 / `metric:` 4；project 分布 225 行 + 9 行 | 全是**逻辑**实体，**零物理表、零物理列、零 SQL 任务** | 三件要紧事：① 目录一期接的实体，现网**已经在这张表里**，认领而非新写（§2.3 后果 6 的验收据此可跑）；② `asset_type='TABLE'` 现在指的是"逻辑模型"，物理表采集进来后会与它**同值不同义**，所以判别必须走 `type_id`（后果 1）；③ 没有任何一行能用来预演物理侧，采集链路只能靠新建 |

凡依赖 Doris 特性的路径标注"环境受限、不可本地验收"，不许写成已验证。
上面这些数字随环境变化，实施前重新取一次，别拿本文的值当常量写进代码。

---

## 2. 存储设计：统一实体目录

### 2.1 表清单（10 张）

| 表 | 层 | 职责 | 量级 |
|---|---|---|---|
| `yak_md_type_def` | **元模型** | 实体类型与字段类型的定义（可扩展的根） | 百级 |
| `yak_md_field_def` | **元模型** | 某实体类型的扩展字段定义（含是否可搜） | 千级 |
| `yak_metadata_asset` | 目录 | **统一实体表**（B 案：复用 lineage 既有表 + 追加目录列，§2.3/§2.5） | 十万级 |
| `yak_md_asset_extension` | 目录 | 稀有大字段侧表（PK `(asset_id, extension)`，指向别人的主键） | 万级 |
| `yak_md_collect_job` | 采集/对账 | 物理采集任务 + 投影**对账**任务（作用域 + cron + 开关；push 登记不经它，§3.2b） | 十级 |
| `yak_md_collect_run` | 采集 | 每次运行的结果与统计 | 万级/年 |
| `yak_md_register_retry` | 注册 | 写时登记的失败重试队列（outbox，§3.2c） | 百级/天，可清理 |
| `yak_md_change` | 历史 | 实体级变更流水（append-only） | 十万级/年 |
| `yak_md_label` | 治理 | 标签溯源（含继承与认证） | 万级 |
| `yak_md_task` | 治理 | 治理待办 | 千级 |

**B 案下的归属划分**：本模块**自持 9 张**
（`type_def` / `field_def` / `asset_extension` / `collect_job` / `collect_run` /
`register_retry` / `change` / `label` / `task`，
迁移落 `db/migration/yak-metadata`），
**与 lineage 共管 1 张**（`yak_metadata_asset`，目录列的 ALTER 落 `db/migration/yak-lineage`，
steward 约定见 §2.3 后果清单）。合计 10 张，与上表一致。
关系（边）仍只在 `yak_metadata_relation`，**不在本方案里新建任何边表**。见 §2.5。

### 2.2 元模型两张表：可扩展性的落点（✅ 蒸馏 §1.3 第二层）

OM 用 `Type` 实体承载"类型本身是数据"。我们不做它的运行时 schema 校验（不引 JSON Schema 校验器），
但**保留"类型与字段是行、不是代码"这一条**——这是"加字段免改表"的全部所需。

```sql
CREATE TABLE IF NOT EXISTS yak_md_type_def (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    type_name     VARCHAR(64)  NOT NULL COMMENT '实体类型名或字段类型名，全局唯一，如 table/tableColumn/dataModel',
    category      VARCHAR(16)  NOT NULL COMMENT 'ENTITY|FIELD（对齐 OM type.json category）',
    name_space    VARCHAR(64)  NOT NULL DEFAULT 'custom' COMMENT 'OM: nameSpace；区分 platform/custom',
    display_name  VARCHAR(128) NOT NULL,
    parent_types  VARCHAR(256) NULL COMMENT '多父以逗号分隔；物理层级用 refersTo 类型对（§2.2 末）',
    fqn_pattern   VARCHAR(256) NULL COMMENT 'ENTITY 必填：键/FQN 生成式。**必须复刻各源域既有格式**（§2.3 后果 6 表），不得另起一套',
    key_separator VARCHAR(8)   NOT NULL DEFAULT '.' COMMENT '**只用于展示用 FQN 的拼接**；asset_key 的分隔符沿用既有 `:`，不由本列决定（§2.3 后果 6）',
    schema_def    JSON NULL COMMENT '属性 schema（字段清单/型/必填/校验）。OM 把它存成字符串，我们存 JSON 以可查',
    provider_bean VARCHAR(128) NULL COMMENT '对账副通道用的 EntityProvider bean 名（§3.2b）；采集型为 NULL',
    lineage_asset_type VARCHAR(32) NULL COMMENT 'ENTITY 必填：映射到 LineageAssetType 枚举常量名，落库写入 asset_type；保存时用 values() 校验（§2.3 后果 1）',
    key_prefix  VARCHAR(64) NULL COMMENT 'ENTITY 必填：asset_key 的固定前缀（如 `modeling:model:`、`table:`），§3.2b 硬约束 4 用它校验 provider 交出的键',
    collectible   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=由元数据模块主动采集(物理)；0=由源域写时登记 + 定时对账(投影，§3.2b)',
    search_default_weight   FLOAT NOT NULL DEFAULT 1.0 COMMENT '统一检索的乘性权重（§4.6，表>列）',
    search_include_by_default TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=不进默认检索面（列实体设 0，§4.6）',
    icon_url      VARCHAR(256) NULL,
    color         VARCHAR(24)  NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|DEPRECATED：永不物理删，历史实体还要能解析',
    version       INT NOT NULL DEFAULT 1,
    description   VARCHAR(512) NULL,
    create_time   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_md_type_name (type_name),
    KEY idx_yak_md_type_category (category, status),
    KEY idx_yak_md_type_collect (collectible, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元模型：类型定义（实体类型 + 字段类型）';

CREATE TABLE IF NOT EXISTS yak_md_field_def (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    type_id       BIGINT NOT NULL COMMENT '所属实体类型 yak_md_type_def.id（category=ENTITY）',
    field_name    VARCHAR(64)  NOT NULL COMMENT '属性键，进 attributes JSON 的 key；camelCase（OM entityName pattern 约定）',
    field_type    VARCHAR(64)  NOT NULL COMMENT '字段类型名，须解析到 category=FIELD 的 type_def',
    display_name  VARCHAR(128) NOT NULL,
    description   VARCHAR(512) NULL,
    required      TINYINT(1)   NOT NULL DEFAULT 0,
    is_null       TINYINT(1)   NOT NULL DEFAULT 1,
    base_type     VARCHAR(24)  NOT NULL COMMENT 'STRING|INTEGER|NUMBER|BOOLEAN|DATE|DATETIME|ENTITY_REFERENCE|JSON|ARRAY',
    entity_type_ref VARCHAR(64) NULL COMMENT 'base_type=ENTITY_REFERENCE 时允许的类型名，逗号分隔（OM customPropertyConfig.entityTypes）',
    constraint_def JSON NULL COMMENT '枚举集/正则/min-max/format（OM enumConfig/format/tableConfig）',
    default_value VARCHAR(256) NULL,
    -- ↓↓↓ 检索配置：OM 的教训是"扩展字段存下来 ≠ 搜得到"，必须显式配（蒸馏 §1.3 第三层）
    searchable    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=参与 q 全文检索；默认 0 防目录被低价值字段污染',
    match_type    VARCHAR(16)  NOT NULL DEFAULT 'text' COMMENT 'text|exact|like|range',
    boost         FLOAT        NOT NULL DEFAULT 1.0 COMMENT '搜索权重（OM FieldBoost）',
    facetable     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=进筛选聚合',
    storage_slot  VARCHAR(32)  NULL COMMENT '提槽到哪个生成列；NULL=只在 attributes JSON 里不可查',
    -- ↑↑↑
    ordinal       INT NOT NULL DEFAULT 0,
    show_in_list  TINYINT(1) NOT NULL DEFAULT 0,
    deprecated    TINYINT(1) NOT NULL DEFAULT 0,
    create_time   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_md_field (type_id, field_name),
    KEY idx_yak_md_field_search (searchable, type_id),
    KEY idx_yak_md_field_slot (storage_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元模型：扩展字段定义';
```

**两个必须解释的点：**

1. **`base_type` 与 `field_type` 并存看似冗余，是刻意的。**
   `field_type` 走 OM 的"字段类型也是一个 Type"（可自定义 `mysqlColumnType` 这类复合类型）；
   `base_type` 是我们**唯一真正用来决定怎么存、怎么筛**的判别。
   一期 `category=FIELD` 的自定义类型只在需要复合结构时用，其余一律 `base_type` 直判——
   照抄 OM 全套 propertyType 会引入一层没有收益的间接。这是"借思想不借实现"的具体落点。
2. **层级不靠 `parent_types`，靠"引用对"。** OM 的 `Table` 里 `columns` 是嵌套数组
   （`entity/data/table.json:211`，已核对：Column **不是** `EntityInterface`），
   而它又用 `table.columns.column` 这类 FQN 型在 `field_relationship` 里把列当可寻址对象
   （`v001:24` 注释）。我们既然要在 MySQL 里按列名跨表查询（符合性对账必需），
   就**必须把列提成独立实体行**，用 `parent_asset_id` 挂父，
   并在 `yak_md_type_def` 里声明 `(table, tableColumn, refersTo)` 这一对父子型。
   → **这是对 OM 的一处主动偏离**，理由：它用 ES 嵌套文档解决列检索，我们没有 ES。
   代价（实体的类型对）由目录页与血缘 UI 统一渲染兜住。

### 2.3 统一实体表：`yak_metadata_asset`（扩展后）

> **本节是 B 案的落地形状（2026-09-19 确认，§11.1 第 5 条）。**
> 表本身由 lineage 建有，本方案在 `db/migration/yak-lineage` **追加一个新版本**给它加目录列
> （**不改任何已应用文件**，见 §9 T5）。
> 为了读起来完整，下面仍按"一张表的全部列"书写；**净增列与复用列的切分见表后映射表**。

```sql
-- V2__add_metadata_catalog_columns.sql   （落在 db/migration/yak-lineage，版本号续 lineage 现状）
-- 基线（V1__baseline_lineage.sql:3-31，已逐列核对）：
--   asset_key VARCHAR(512) NOT NULL、asset_type VARCHAR(32) NOT NULL、name VARCHAR(200) NOT NULL、
--   parent_asset_id、source_type/source_id、data_source_id/database_name/schema_name/table_name/column_name、
--   properties JSON、create_time/update_time DATETIME(6) NOT NULL（无默认值）、
--   uk_yak_metadata_asset_project_key (project_scope_id, asset_key)、project_id 可空 + project_scope_id 生成列
ALTER TABLE yak_metadata_asset
    -- ① 元模型挂钩
    ADD COLUMN type_id        BIGINT NULL COMMENT '→ yak_md_type_def.id；NULL=本方案之前的遗留图节点',
    -- ② 可展示 / 可检索面
    ADD COLUMN display_name   VARCHAR(256) NULL,
    ADD COLUMN fully_qualified_name VARCHAR(768) NULL
        COMMENT '按 type_def 的键生成器产出；遗留行为 NULL（= 未纳入目录语义）',
    ADD COLUMN fqn_hash       CHAR(32)     NULL COMMENT 'md5(lower(asset_key))；NULL=遗留行。只建普通索引，不建唯一键（后果 4）',
    ADD COLUMN summary        VARCHAR(2048) NULL COMMENT '描述/注释，中文检索主力（ngram）',
    -- ③ 治理与归属
    ADD COLUMN owner_user     VARCHAR(64)  NULL,
    ADD COLUMN domain_ids     VARCHAR(512) NULL COMMENT '业务域 id 逗号串，让 facet 查询免扫侧表',
    ADD COLUMN tier_label     VARCHAR(32)  NULL,
    ADD COLUMN layer_code     VARCHAR(32)  NULL COMMENT '命中 semantic 分层库时回填 ODS/DWD/…',
    ADD COLUMN entity_status  VARCHAR(24)  NULL COMMENT '7 值，蒸馏 §1.1；NULL=遗留行（不用假值污染，后果 3）',
    -- ④ 两条入口与指纹（§1.3）
    ADD COLUMN provider_type  VARCHAR(16)  NULL COMMENT 'HARVESTED|REGISTERED；NULL=遗留行',
    ADD COLUMN collect_job_id BIGINT       NULL COMMENT 'yak_md_collect_job.id（原名 provider_id，避免与 provider bean 混）',
    ADD COLUMN content_hash   CHAR(32)     NULL COMMENT '物理结构指纹，§3.3',
    ADD COLUMN source_hash    CHAR(32)     NULL COMMENT '投影指纹，由 provider 产出',
    ADD COLUMN source_updated_at DATETIME(6) NULL COMMENT '源侧最后变更时间，不是本行 update_time',
    -- ⑤ 在场性与版本
    ADD COLUMN first_seen_at  DATETIME(6)  NULL COMMENT '遗留行为 NULL（含义="早于目录机制"），不填假值',
    ADD COLUMN last_collect_at DATETIME(6) NULL,
    ADD COLUMN last_change_at DATETIME(6)  NULL,
    ADD COLUMN gone_at        DATETIME(6)  NULL COMMENT '软删；连续两轮缺失才置值（§3.4）',
    ADD COLUMN catalog_version INT NOT NULL DEFAULT 1 COMMENT '目录侧版本；不复用别的语义的 version 列',
    ADD COLUMN updated_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    -- ⑥ 属性袋：**新开一列，不复用 properties**（理由见下方后果 2）
    ADD COLUMN md_attributes  JSON NULL COMMENT '按 type_def/field_def 校验的属性袋；不查，只展示',
    -- ⑦ 属性提槽：被 field_def.storage_slot 指定的热字段固化为生成列（列数封顶，§2.4.1）
    ADD COLUMN s_str_1  VARCHAR(256) GENERATED ALWAYS AS (json_unquote(json_extract(md_attributes,'$."s_str_1"')))  STORED,
    ADD COLUMN s_str_2  VARCHAR(256) GENERATED ALWAYS AS (json_unquote(json_extract(md_attributes,'$."s_str_2"')))  STORED,
    ADD COLUMN s_str_3  VARCHAR(256) GENERATED ALWAYS AS (json_unquote(json_extract(md_attributes,'$."s_str_3"')))  STORED,
    ADD COLUMN s_num_1  BIGINT       GENERATED ALWAYS AS (json_extract(md_attributes,'$."s_num_1"'))                 STORED,
    ADD COLUMN s_num_2  BIGINT       GENERATED ALWAYS AS (json_extract(md_attributes,'$."s_num_2"'))                 STORED,
    ADD COLUMN s_bool_1 TINYINT      GENERATED ALWAYS AS (json_extract(md_attributes,'$."s_bool_1"'))                STORED,
    ADD COLUMN s_date_1 DATETIME(6)  GENERATED ALWAYS AS (json_extract(md_attributes,'$."s_date_1"'))                STORED,
    -- ⑧ 键与索引（**不新增唯一键**，理由见下方后果 4）
    ADD KEY idx_yak_md_asset_fqn (fqn_hash),          -- 按 FQN 反查用；唯一性由 asset_key 那把既有键保证
    ADD KEY idx_yak_md_asset_type (project_id, type_id, gone_at),
    ADD KEY idx_yak_md_asset_collect (project_id, provider_type, last_collect_at),
    ADD KEY idx_yak_md_asset_status (project_id, entity_status),
    ADD KEY idx_yak_md_asset_domain (project_id, domain_ids(64)),
    ADD KEY idx_yak_md_asset_slot_str (s_str_1),
    ADD KEY idx_yak_md_asset_slot_num (s_num_1),
    ADD FULLTEXT KEY ft_yak_md_asset (name, display_name, summary) WITH PARSER ngram;

-- 稀有大字段侧表（本方案自有，前缀仍按 §0.8）
CREATE TABLE IF NOT EXISTS yak_md_asset_extension (
    asset_id   BIGINT NOT NULL COMMENT 'yak_metadata_asset.id；无物理外键（§0.9）',
    extension  VARCHAR(128) NOT NULL COMMENT '点分名 <typeName>.<fieldName>（OM 同形）',
    json_schema VARCHAR(256) NULL COMMENT '产该值的类型版本，便于排查漂移',
    json       JSON NOT NULL,
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (asset_id, extension),
    KEY idx_yak_md_ext_name (extension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='稀有大字段侧表（主表保持窄）';
```

> **这段 ALTER 已在真表副本上预演过**（2026-09-19，`yak_security`，
> `CREATE TABLE … LIKE yak_metadata_asset` + 显式列出非生成列灌入 **234** 行后执行；副本用完即 `DROP`）。
> 预演**否掉了本方案早先写的一版形状**：那时 `fqn_hash` 定成 `NOT NULL DEFAULT ''` 并加
> `UNIQUE KEY (project_id, fqn_hash)`，在 234 行副本上直接
> `(1062, "Duplicate entry '1-' for key 'uk_sim_fqn'")` —— 全部历史行被回填成同一个 `''`，
> **这个迁移在现网第一天就跑不过去**。改法见下方后果 4（不建这把唯一键）。
> 与预演同时定下的另一条：`fully_qualified_name`/`fqn_hash`/`entity_status`/`content_hash`
> 四个"只有目录才知道"的列**一律可空**，与后果 3 的"用 NULL 承载未知、不填假值"是同一条决定，不是巧合。
> 用可空版本重跑：**通过**，行数仍 234，不需要任何回填语句。

**复用 lineage 既有列，不重复建**（这是 B 案的全部省力处，也是它的约束来源）：

| 初稿（A 案）列 | B 案落点 | 说明 |
|---|---|---|
| `type_name VARCHAR(64)` | **不复用 `asset_type`；改用新列 `type_id`** | `asset_type` 是 lineage 的**闭集枚举**且存量已写歪，见下方后果 1 与后果 7 |
| `name` | **复用 `name VARCHAR(200)`** | 原始名不含分隔符，200 够用，**不 ALTER 活列** |
| `parent_entity_id` | **复用 `parent_asset_id`** | 层级语义一致 |
| `asset_key` | **复用 `asset_key VARCHAR(512) NOT NULL`** | 已是 `uk (project_scope_id, asset_key)` |
| `lineage_asset_id` | **删除** | 本行**就是**图节点，没有"指向别人"的必要 |
| `attributes` | **改为新列 `md_attributes`** | 见下方后果 2，**绝不复用 `properties`** |
| `version` | **改为 `catalog_version`** | `yak_metadata_relation` 已有一个语义不同的 `version`，不共名 |

**B 案的七条专属后果**（A 案下不存在，必须写进 steward 约定）：

1. **`asset_type` 不能当目录判别列，必须另立 `type_id`。** 三条实测理由，任一条单独成立即否决：
   - 它是**闭集 Java 枚举**：`LineageAssetType`（`domain/LineageAssetType.java:4-13`，
     只有 `TABLE/COLUMN/SQL_TASK/DATASET/DATASET_FIELD/CHART/DASHBOARD/METRIC` 八个值），
     而 `LineageRepositoryAdapter.toAsset()` 每次读行都做 `LineageAssetType.valueOf(row.getAssetType())`——
     **写进一个枚举里没有的名字，lineage 的血缘查询会当场抛 `IllegalArgumentException`**，
     不是"搜索少个类型"而是"别人的功能 500"。这个枚举还被 analysis / dashboard / dataset /
     data-development / home 五个模块 import，改名或扩值都是跨模块动作。
   - 存量 234 行已经把它用成了**"图节点形状"的粗分类**而非实体类型：
     11 个模型写成 `TABLE`、211 个模型字段与 8 个标准字段写成 `COLUMN`（§1.4 实测）。
     所以按 `asset_type='TABLE'` 过滤"物理表"会捞出一堆逻辑模型。
   - 它只有 32 字符（`V1__baseline_lineage.sql`），容不下开放生长的类型名。
   **落地形状**：目录判别列 = 新列 `type_id → yak_md_type_def.id`；
   `asset_type` 仍要写（NOT NULL），值来自 `type_def.lineage_asset_type` 这一**显式映射列**，
   保存时用 `LineageAssetType.values()` 校验（不是枚举名即 49xxx 拒），
   并由 §10 测试 13 锁住"每个已登记类型都映射到存活枚举常量"——
   这样 lineage 将来重命名常量时，CI 会红，而不是线上读行才炸。
   两者不一致时**以 `type_id` 为准**；`asset_type` 只回答"这个节点在图里长什么形状"。
2. **绝不能与 lineage 共用 `properties`。** 该列由 lineage 的 upsert **整体覆写**——
   已核对 `mapper/lineage/LineageWriteMapper.xml:31`（单条）与 `:64`（批量）都是
   `properties = VALUES(properties)`，即"算好一整个 json 再写"。
   元数据往里塞的键会在下一次 lineage 写入时**静默消失**，提槽生成列跟着变 NULL → 搜索条件凭空失效，
   且没有任何报错。这就是 §2.5 说的"共管的是列，不是语义"的具体形态，
   也是 `md_attributes` 单独成列的全部理由。
   > 反向的好消息：**同一份列清单里没有新列**，
   > 所以 lineage 的 upsert 不会碰目录列——加列对既有写入路径确实是加法，不是改写。
3. **遗留 234 行的语义要显式承认**：它们 `type_id/provider_type/first_seen_at` 为 NULL、
   `fqn_hash/content_hash` 也为 NULL，含义是"**早于目录机制存在的图节点**"，
   不是"未采集"也不是"无变更"。按 §1.4 实测它们的构成是：11 个模型 + 211 个模型字段
   + 8 个标准字段 + 4 个指标——**没有一个是物理表**。所以：
   - `provider_type`/`type_id`/`first_seen_at` 保持**可空**（用 NULL 承载"未知"，不用假值污染）；
   - 覆盖率/新鲜度类概览指标（ticket 126）**必须排除 `provider_type IS NULL`**，
     否则 234 个遗留节点会把治理分数从起始就压死，看起来像"采集质量极差"。
4. **目录不新增唯一键：身份只有一个来源，就是 `asset_key`。**
   本表已有 lineage 的 `uk (project_scope_id, asset_key)`，而 `fqn_hash` 定为
   `md5(lower(asset_key))` 的**派生值**——给派生值再建一把唯一键，
   买到的只有写放大和"以为有两把锁"的虚假安全感。
   **实测的教训比结论更重要**：早先那版
   `fqn_hash NOT NULL DEFAULT '' + UNIQUE KEY (project_id, fqn_hash)` 在 234 行副本上直接 1062
   （本节末预演记录）——**给一张活表加"依赖回填值"的唯一键，本身就是危险动作**，
   它假设"历史行回填出来的值互不相同"，而回填默认值天然相同。
   所以：`fqn_hash` 只建**普通索引**（按 FQN 反查用，§4.1），
   遗留行 `fqn_hash IS NULL` 的含义回到后果 3（"未纳入目录"），不再牵涉键空间。
   → **不变式的锁法换成代码层**：`fqn_hash` 与 `fully_qualified_name` **只允许在 `MetadataKeyCodec`
   这一个类里生成**——前者是 `md5(lower(asset_key))` 的摘要，后者按 `type_def.fqn_pattern`
   从同一个 `asset_key` 渲染（两者规则不同但**同处一类**，§3.2b 硬约束 4）；
   §10 测试 13 grep 守护"别处不得出现 md5(…assetKey…)"。
   与其相信第二把键能拦住漂移，不如让漂移在物理上无从下手。
5. **⚠️ `project_id` 也会被 lineage 的 upsert 覆写，这才是共表真正咬人的地方。**
   `LineageWriteMapper.xml:26`/`:59` 都有 `project_id = VALUES(project_id)`，
   而"project 为 null"在 lineage 侧是**合法路径**（`selectAssetForUpdate:89` 的
   `<if test="projectId != null">` 就是在允许它）。后果链条：
   某张物理表的目录行被 lineage 的一次全局登记刷成 `project_id = NULL`
   → 生成列 `project_scope_id` 跟着变成 0，**该行从本项目的键空间搬进全局桶**
   → 下一轮采集以真实 project 登记同一个 `asset_key` 时**不再与它冲突**
   → **插出重复行**，而且是静默的（唯一键没坏，坏的是行的归属）。
   这条没有任何唯一键能拦，只能靠 steward 约定与看门狗查询：
   - 元数据登记出来的行**不得被全局路径重新登记**——provider/collector 一律带 project 上下文；
   - lineage 侧若确需全局资产，**用不同 `asset_key`**，不与目录行复用同一行。
   → 守卫：ticket 119 加一条断言"同一 `asset_key` 的在场（`gone_at IS NULL`）行数 ≤ 1"，
   以及 §10 测试 5 扩一条"重复采集不产生第二行"。
   **这是 B 案的真实代价，写在明处**：共表之后，一个模块的 project 语义变更能破坏另一个模块的去重键。
6. **⚠️ `asset_key` 必须复用各源域**已有**的生成器，不能由元数据另起一套命名。**
   实测现网 234 行的键前缀只有三种，且格式是既成事实：

   | 已有实体 | 现网 `asset_key` 格式 | 出处 |
   |---|---|---|
   | 模型 | `modeling:model:{modelId}` | 存量 11 行（§1.4） |
   | 模型字段 | `modeling:model:{modelId}:column:{col}` | 存量 211 行 |
   | 标准字段 | `semantic:field:{fieldId}` | 存量 8 行 |
   | 指标 | `metric:{metricId}` | 存量 4 行 |
   | 物理表 | `table:[unresolved:]{dataSourceId}:{db}.{schema}.{tbl}` | `TableIdentityResolver.java:93-102`（**尚未有数据**，SQL 血缘未跑过） |
   | 物理列 | 上式把前缀 `table:` 换成 `column:`，末尾 `.{"列名小写"}` | `DevelopmentSqlLineageService.java:469-474` |
   | SQL 任务 | `sql-task:data-development:{nodeId}` | 同文件 `:36-38` |

   为什么这条比后果 5 更阴：**新造一套键不会报错，只会分裂节点。**
   元数据若按初稿的 `model:{id}` 注册模型，与现网的 `modeling:model:{id}` 是**两个不同的 `asset_key`**，
   而全表唯一的 `uk (project_scope_id, asset_key)` 对两行都满意 → **数据库根本不拦**，
   于是同一个模型在图里有两个节点，血缘边各挂一半——
   而这正是 B 案当初要消灭的东西（§0.2 第二套真相）。
   所以：**`type_def` 的键生成规则以"复刻上表"为准**，一期八个类型里
   `dataModel` / `standardField` / `metric` **必须落到已有键格式上**（顺带把 234 遗留行"认领"进目录，
   `type_id` 由 NULL 变非空），`table` / `tableColumn` 必须落到 `TableIdentityResolver` 的格式上。
   → 可执行验收（有真实数据可比，不必等新采集）：
   **注册通道跑完后**，`asset_type='TABLE' AND source_type='MODELING'` 的行数**仍是 11**、
   `semantic:field:%` 仍是 8 行、`metric:%` 仍是 4 行，只是 `type_id` 不再是 NULL；
   任何一个数字翻倍就是本条失败（§8 P1 注册、§10 测试 5）。
7. **两处跨模块 Java 改动躲不掉，必须与 lineage/development 一起排期（ticket 134）。**
   - 物理键生成器 `TableIdentityResolver.PhysicalTableIdentity#assetKey()` 在
     `yak-ops-business-data-development` 的 `…development.service` **内部包**里，
     §0.3 禁止元数据 import 别模块内部包 → 必须把这段纯字符串逻辑**下沉到 `yak-ops-common`**
     （或 lineage 的 api 包），data-development 改为引用同一份，**行为逐字不变**并由其现有测试守护。
     这与 ticket 113 抽 `LineageRegistrationApi` 是同一类改造，但不是一件事：113 抽的是接口，这条抽的是键。
   - 元数据独有的三个类型（`databaseService` / `database` / `domain`）在 `LineageAssetType` 里**没有对应常量**，
     而 `asset_type` 是 NOT NULL 且被 `valueOf` 解析（后果 1）→ 需要**给该枚举加 `DATABASE_SERVICE` /
     `DATABASE` / `DOMAIN` 三个值**。加常量对 `valueOf` 与六个消费方都是向后兼容的加法，
     但它是别人模块的源码改动，**不能由元数据单方面提交**，须在 134 里与 lineage 约定。

> 命名债（诚实记录）：表是 `yak_metadata_asset`（lineage 前缀），侧表却叫 `yak_md_asset_extension`
> （本方案前缀）且指向别人的主键。读代码的人会先愣一下。
> 不打算为此改名规避——**前缀一致 vs 归属清晰，这里选归属清晰**，并写进 `ARCHITECTURE.md`。

**一期注册的实体类型（V1 基线 INSERT，不写代码）：**
`asset_key 格式`列**不是设计选择而是既成事实**——有存量的行必须逐字复用现网键（§2.3 后果 6）。

| `type_name` | `lineage_asset_type`（→`asset_type`） | 入口 | `key_prefix` | asset_key 格式 | 键由谁生成 | 现网存量 / 预估 | 默认进检索面 |
|---|---|---|---|---|---|---|---|
| `databaseService` | `DATABASE_SERVICE`(新) | HARVESTED | `datasource:` | `datasource:{dataSourceId}` | 元数据（下沉后的共享生成器） | 16 个非系统库所属源，十级 | 是 |
| `database` | `DATABASE`(新) | HARVESTED | `database:` | `database:{dataSourceId}:{db}` | 元数据 | 十级 | 是 |
| `table` | `TABLE` | HARVESTED | `table:` | `table:[unresolved:]{dsId}:{db}.{schema}.{tbl}` | 元数据（**与 `TableIdentityResolver:93-102` 逐字同源**，ticket 134） | **373**（§1.4 实测；现网 0 行） | 是（权重最高） |
| `tableColumn` | `COLUMN` | HARVESTED | `column:` | 上行 `table:`→`column:` + `.{列名小写}` | 元数据（同 `DevelopmentSqlLineageService:469-474`） | **4684** | **否**（§4.6，以"命中 N 列"聚合露出） |
| `dataModel` | `TABLE` | REGISTERED | `modeling:model:` | `modeling:model:{modelId}` | **provider**（`ModelingLineageRegistrationService:275`） | **11 行已在图里**，注册后认领 | 是 |
| `standardField` | `COLUMN` | REGISTERED | `semantic:field:` | `semantic:field:{fieldId}` | **provider**（同文件 `:184`） | **8 行已在图里**（另有 148 条标准字段未进过图） | 是 |
| `domain` | `DOMAIN`(新) | REGISTERED | `semantic:domain:` | `semantic:domain:{domainId}` | provider（semantic 侧新写，照 `semantic:field:` 构法） | 28（均未进过图） | 是 |
| `metric` | `METRIC` | REGISTERED | `metric:` | `metric:{metricId}` | **provider**（现网 4 行同键） | **4 行已在图里** | 是 |

> 表里三个 `(新)` 就是 §2.3 后果 7 说要与 lineage 协商加的那三个 `LineageAssetType` 枚举常量；
> `TABLE`/`COLUMN`/`METRIC` 复用现值，因此**逻辑模型与物理表会在 `asset_type` 上同值**——
> 这正是"以 `type_id` 判别、`asset_type` 只图表形状"（后果 1）的必然结果，不是漏洞。
> 现网另有 4 行 `MODELING` 的列挂在 project 2、1 行 TABLE 挂 project 2，认领时 `project_id` 不动。

一期落库量级约 **5.3 千行**（距 §4.4 的 10 万 ES 阈值还有约 19 倍余量），
**但列实体占其中 88%**——这就是 §4.6 必须一期解决、不能推给二期的原因。

**加一类元数据的真实成本**（写在这里，防止"灵活"变成口头承诺）：
插一行 `yak_md_type_def`（+ 若干 `field_def`）**免代码**——**前提**是它的
`lineage_asset_type` 映射到一个**已存在**的 `LineageAssetType` 常量，且键由 provider 交出（§3.2b 硬约束 4）；
投影型再加一个 provider 类（约 60~120 行，只读，照 `AssetProvider` 形状）；
采集型再加一个 collector（约 100 行）；
需要新属性时优先用现成槽位。
**三种情况要改代码/改表，别骗自己**：
新类型在 `LineageAssetType` 里没有对应枚举常量 → **一次跨模块 Java 改动**（§2.3 后果 7）；
要**新槽位**或新索引 → 一次 `ALGORITHM=COPY` 的运维窗口（§2.4.1）；
键格式与源域既有生成器不一致 → 必须先谈拢键，不是元数据单方面能定的（§2.3 后果 6）。

### 2.4 五个必须解释的设计选择

1. **提槽列（`s_str_*`/`s_num_*`/…）而不是 per-field 生成列。**
   OM 每个热点字段一个生成列——因为它每种实体一张表，列数不失控。
   我们是单表混装所有类型，若每个 `field_def` 都开一列，
   扩展字段累积会让表宽无界增长，且**每次加字段都要 ALTER 全表**。
   共享槽位把这个代价变成一次性：列数封顶在 7，之后加字段只是 `field_def.storage_slot` 指个名。
   代价是**两个类型不能同时用同一个槽做不同语义的索引**——
   所以 `storage_slot` 分配必须走一个集中登记（`MetadataSlotRegistry`），
   冲突时报错而不是静默复用；槽位表本身也要有"谁占了哪个"的可读视图。
   **这是本方案里最不优雅但最划算的一处**，明确写进契约文件集。
   **并且槽位必须一次性建全。** 实测（2026-09-19，本机 MySQL 8.0.46）给一张带数据的表
   `ADD COLUMN ... GENERATED ALWAYS AS (...) STORED`：
   `ALGORITHM=INSTANT` → 错误 1845，`ALGORITHM=INPLACE` → 同样 1845，只有 `ALGORITHM=COPY` 通过。
   也就是**加一个 STORED 生成列 = 整表复制，期间不能并发 DML**。
   所以 §2.3 那次 ALTER 必须把 7 个槽位一次建齐（234 行时是毫秒级），
   而**日后**在已填满的表上再加第 8 个槽位是一次运维窗口，不是免费迁移——
   这条要写进 ticket 128 的契约，让"槽位够不够"在第一天就被评审，而不是等到第 20 个扩展字段。
2. **FQN 反查走 `fqn_hash CHAR(32)`，不给 `VARCHAR(768)` 直接建索引。**
   表 FQN 已近 200 字节，中文库名/表名按 utf8mb4 最坏 4 字节/字，直接建索引会撞 InnoDB
   的键长上限。（OM 用 `VARCHAR(768) ascii_bin`，同一目的、不同手法；我们选 md5
   因为它连 `asset_key VARCHAR(512)` 那类拼接也不受字符集牵连。）
   至于要不要在它上面建唯一键——不建，理由见下一条。
3. **一张表只留一把唯一键，而且不是我们建的。**
   `yak_metadata_asset` 已有 `uk_yak_metadata_asset_project_key (project_scope_id, asset_key)`
   （基线 `V1__baseline_lineage.sql`，建在 `project_scope_id GENERATED ALWAYS AS (COALESCE(project_id,0)) STORED` 上）。
   本方案**不新增唯一键**，只给派生列 `fqn_hash` 加普通索引。三点理由：
   - `asset_key` NOT NULL，所以每个目录行都必须带一个与 lineage 同源的 key——
     这正是 B 案的本意：同一张表的同一个 node，既被图查询走 `asset_key`、又被目录搜索走派生的 `fqn_hash`，
     **不存在"只有目录身份、没有图身份"的行**。`domain` 这类暂无血缘的实体也要有稳定 key（§2.3 后果 6 表）。
   - 给派生值再建一把唯一键是冗余（后果 4），而且在活表上还危险：实测那种形状在 234 行副本上直接 1062。
   - 初稿"A 案双唯一键 ⇒ FQN 规则漂移当场失败"这条论证**随之作废**：
     真正拦得住漂移的不是第二把键，而是**键由源域自己交出 + 派生只有一处函数**（§3.2b 硬约束 4、§10 测试 13）。
   **残余风险要说清楚**：唯一键管不住"行被换了归属"——`project_id` 被 lineage 覆写成 NULL
   会把行搬进 `project_scope_id=0` 的全局桶，同 `asset_key` 的真实项目行于是能与它并存（后果 5）。
   这一条没有任何键能拦，只能靠 steward 约定 + 看门狗查询。
   （对比 A 案：新建表时两把键都在自己手里、可设计成对称的，代价是 §0.2 不自洽。B 案换来了同源，换走了对称。）
4. **FULLTEXT `WITH PARSER ngram` 建在 `(name, display_name, summary)` 三列上。**
   中文注释/中文名是本平台元数据的主要检索面，默认 fulltext 按空格切、中文整句成一个词等于搜不到。
   ngram 是 MySQL 5.7.6+ 内置、无外部依赖。
   **必须在 V1 就建**——事后 `ALTER TABLE ADD FULLTEXT` 在十万行上是分钟级锁。
   这是"不上 ES 也能统一搜中文"的关键前提。
5. **`gone_at` 软删，不物理删。** 一张表从库里消失可能只是运维临时下线；
   一个模型下架不代表它从未存在。物理删会连带丢掉历史与人工标注，
   并让"GONE"退化成"查不到"这种不可查询状态。

### 2.5 与 lineage 的分工（以及一个必须现在做的决定）

**现状**：`yak_metadata_asset` **已经是一张统一实体表**（已核对基线）——
`asset_type VARCHAR(32)` 图形状粗分类（**不能当目录判别列**，理由见 §2.3 后果 1）、`name`、
`parent_asset_id` 层级、
`properties JSON` 属性袋、`project_scope_id GENERATED ALWAYS AS (COALESCE(project_id,0)) STORED`
（已经在用生成列技巧）、`uk (project_scope_id, asset_key)`。
它缺的只是：`type_id`（真正的类型判别）/ `display_name` / 可搜的描述 / `fqn_hash` /
`content_hash` / `entity_status` / `catalog_version` /
域与负责人 / 全文索引。
**且它不是空表**：§1.4 实测已有 **234** 个图节点（模型 11 + 模型字段 211 + 标准字段 8 + 指标 4，
**零物理表**），而同为目录形状的 `yak_asset_item` 是 **0** 行。
两个数字合起来说明：图节点这条链**已经在真实写入**，B 案是"给一张活表加列"（要评估既有写入路径），
不是"给空表立规矩"；而平台缺的确实不是又一张登记表，是**能搜能逛的发现层**。

**所以"再建一张 `yak_md_entity`"是有真实代价的**：平台将出现三张目录形状的表
（`yak_asset_item`、`yak_metadata_asset`、`yak_md_entity`），
每两张之间都要一个同步器，而**同步器就是漂移的发生地**。
本仓库已经在为 `*LineageSynchronizer` 付这笔钱（analysis / dashboard / dataset 三处各一个）。

两个可选路径：

| | A. 新建 `yak_md_entity`（❌ 作废） | B. 原地扩展 `yak_metadata_asset`（**✅ 已采纳**） |
|---|---|---|
| 迁移 | 新模块自己的 V1，不碰 lineage | 在 `db/migration/yak-lineage` 追加新版本（**加版本号，不是改已应用文件**） |
| 表数量 | 三张目录形状 → **需 2 个同步器** | 一张 → **零同步器** |
| 归属 | 目录归 metadata，图节点归 lineage | 同一张表由 lineage/metadata 共管，**ownership 必须先定** |
| 风险 | 同步器漂移、双写不一致 | 与 lineage 读写链共表：3 个 `*LineageSynchronizer` 需回归（**加可空列不动它们的列清单**，风险在索引与写入时序，不在 SQL 形状）。现存 234 行加 FULLTEXT 成本可忽略 |
| 工作量 | 高（多两套同步 + 对账） | 低（一次 ALTER + 补齐列），但涉及跨模块协商 |

**建议 B，并已按 B 定案**，理由就一条但足够硬：**§0.2"不建第二套真相"在 A 方案下无法自洽**——
A 需要靠同步器维持两份实体记录一致，而同步器正是真相漂移的来源；
B 让"图节点表"与"目录表"是同一张表，血缘、层级、检索、治理天然共享一份事实。
`project_scope_id` 那套生成列用法也已验证可行。

**B 的具体形状**（已采纳，故不再是"若"）：**以 §2.3 的 `ALTER` 为唯一权威清单**
（那里逐字列出新增列、复用的 lineage 既有列、7 个提槽生成列、`KEY`/`FULLTEXT` 与七条专属后果）。
本节只保留判据与决策，不再抄一份列清单——两份必然漂移，而实现时看的是列清单。
分工：元数据模块是**目录列的 steward**，lineage 继续拥有 `asset_key/asset_type/parent_asset_id/properties` 的既有语义。

**B 案有一条必须先谈平的语义冲突**（不是可选优化，是同一张表上的两种隔离口径）：
lineage 现用 `project_scope_id GENERATED ALWAYS AS (COALESCE(project_id,0)) STORED`，
即**允许全局节点**（`project_id` 为 NULL 落到 0）；而 §0.9 要求目录里的每一行都可追溯到一个真实项目。
两者共存的正确处置：
- 元数据登记的行 **`project_id` 必须非空**，由 provider/采集器上下文保证，入库前断言（49xxx）；
- 目录侧**不另立唯一键**（后果 4），身份就是 lineage 那把 `uk (project_scope_id, asset_key)`。
  因此"全局节点与项目节点占同一个 FQN"这类串号**没有键能拦**——它由三条约定管理：
  ① 元数据写行时 `project_id` 非空；② provider/collector 永远带项目上下文（§3.7）；
  ③ ticket 119 的"同一 `asset_key` 在场行 ≤ 1"看门狗。见后果 5。
- lineage 既有 `uk (project_scope_id, asset_key)` 不动（改它才是真的高风险迁移）。
> 这段就是"两模块共管一张表"的具体样子：**共管的是列，不是语义**。
> steward 约定必须进 `ARCHITECTURE.md`，不能只写在本文里。

**✅ 已定：采纳 B 案（2026-09-19 确认，见 §11.1 第 5 条）。A 案作废，其上两行保留作对照。**

落地时的三条硬要求：
1. 目录列的 ALTER **必须**是 `db/migration/yak-lineage` 的**新版本文件**
   （不是改 V1；改已应用文件 = `checksum mismatch`，启不来，见 §9 T5）。
2. steward 约定（谁读谁写哪几列、`project_id`/`properties` 的两条禁令）
   **同时写进 lineage 与本模块的 `ARCHITECTURE.md`**——只写在方案里等于没写。
3. 本文其余部分（提槽、双指纹、GONE/熔断、ngram 全文、软删、治理同构引用）
   **逐字照抄到 B 案形状**，不需要再打折；A/B 分支断言一律取 B 支。

### 2.6 变更事件（🟡 蒸馏 §3.5）

不建独立 `change_event` 表：`yak_md_change` + `yak_md_collect_run` 已承载"谁在何时改了什么"。
但 outbox 那条经验要固化——**事件是"出箱"不是"通知"：先落库再派发，消费者挂了数据还在**
（蒸馏 §3.5）。因此 `yak_md_change` 必须 append-only 且**永不 UPDATE/DELETE**
（清理走 `changed_at` 冷数据归档，不走 DELETE）。
> 这条经验在定下 push 登记（§3.2b）之后**有了实体**：`yak_md_register_retry`（§3.2c）
> 就是那个"先落库再派发"的箱。在此之前它只是一句提醒。

---

## 3. 采集设计（两条入口：采集 + 注册）

### 3.1 进程内，不引新组件（与 OM 的根本分歧）

OM 的连接器在 JVM 外（Python 进程 + HTTP 回写，蒸馏 §0）。
**我们不做这个选择，也没有理由做**：`DataSourceCatalog` SPI 已在进程内，
调用即得（`yak-ops-plugins/yak-ops-plugin-datasource/*/…/DataSourceCatalog.java`）。
因此以下三类 OM 机制**整体不采纳**：跨进程批量 HTTP 提交、HTTP 重试、连接器状态文件/水位续传。

两条入口共用同一套落库、指纹、GONE 与熔断机制（§3.3~§3.4），区别只在 `provider_type`：

| 入口 | 适用 | 方向 | `provider_type` |
|---|---|---|---|
| **采集 HARVESTED** | 外部物理元数据 | 元数据模块主动调 `DataSourceCatalog` | `HARVESTED` |
| **注册 REGISTERED** | 内部逻辑元数据 | **主：源域写成功后调 `MetadataRegistrationApi`（push）**；副：定时对账调源域 provider 批量补漏（§3.2b） | `REGISTERED` |

### 3.2 物理采集 = 一次 SPI 调用（不需要方言层）

`listDatabases() → listSchemas(db) → listTables(query) → listColumns(tablePath)`
即得全部结构。`DataSourceTable` 5 字段 + `DataSourceColumn` 9 字段
落成 `table` / `tableColumn` 两类实体的 `attributes`（§2.3）。

OM 为此写了 90+ 个方言目录（Doris 那个连 `MySQLTableDefinitionParser` 都要自己借，蒸馏 §3.2）。
**我们跳过整层。** 这是本方案省下的最大一块工程量，也是"为什么这个模块能在十余个 ticket 内落地、
而不是三十个"的原因。

> 边界说明：**MySQL 协议族**（MySQL / Doris / MariaDB / OceanBase-MySQL 模式）走 JDBC
> `DatabaseMetaData` 稳定可靠。**非 JDBC 插件**（如 DuckDB、某些国产库）若 `listColumns`
> 返回空或抛异常，采集器必须把它记成该表 `PARTIAL` 失败而非静默空表——
> 静默空表会让下一轮误判为"全部列被删除"（这正是 §3.4 熔断要拦的事）。

### 3.2b 内部实体进目录：push 登记为主，`EntityProvider` 只做对账

内部实体**不由元数据模块去读别人的库**（§0.4 红线），也**不靠定时任务捞**（§1.3 末的决定）。
两条通道，主次分明：

**主通道 · 写时登记（push）**——源域在自己的写操作成功后，把目录投影交给元数据的 api：

```java
package io.yak.ops.business.metadata.api;   // 接口在 metadata 的 api 包，源域只 import 这里

public interface MetadataRegistrationApi {
  /** 幂等 upsert：同一 (project, assetKey) 重复登记不产生第二行。 */
  void register(RegisterCommand command);
  /** 源域删除实体时同步撤销目录行（走软删，不物理删，§2.4.5）。 */
  void unregister(String typeName, String sourceId);
}
```

`RegisterCommand` = `typeName / sourceId / assetKey / 投影字段（§1.3 那一份） / sourceHash / sourceUpdatedAt`。
**目录列由元数据自己写**，源域永不直写 `yak_metadata_asset`——这条不因 push 而松动。

`EntityProjection` 只含**目录投影**字段（§1.3）：
`sourceId / assetKey / name / displayName / summary / owner / domainIds / layerCode /
sourceUpdatedAt / sourceHash / parentSourceId / extra`。
push 的 `RegisterCommand` 与它字段一一对应，**两者共用同一个 DTO**，免得长出两套投影定义。

**副通道 · 定时对账（pull）**——`EntityProvider` 保留原形状，但职责收窄为对账：

```java
package io.yak.ops.business.metadata.api;   // 接口在 metadata 的 api 包，实现在各源域

public interface EntityProvider {
  String typeName();                                    // 对应 yak_md_type_def.type_name
  /** 游标批量 ≤500，按主键升序；updatedAfter 非空时仅返回该时间后有变更的投影。 */
  EntityPage cursorList(EntityCursorQuery query);
  /** 单实体刷新（详情聚合/复核/重试重放用）；源已删除返回 empty。 */
  Optional<EntityProjection> refresh(String sourceId);
}
```

它只做三件事：**补漏**（push 之前就已存在的历史实体、以及重试表都失败的行）、
**刷 `source_hash`**、**判 GONE**（§3.4）。一期四个 provider：

| provider | 所在模块 | 读什么 | `parentSourceId` |
|---|---|---|---|
| `ModelEntityProvider` | modeling | 既有模型查询服务 | 所属目录 |
| `StandardFieldEntityProvider` | semantic | `semantic/field/` | 所属业务域 |
| `DomainEntityProvider` | semantic | 业务域 | —（根） |
| `MetricEntityProvider` | metric | 指标目录 | 所属业务域 |

**依赖方向要说清**：`MetadataRegistrationApi`（源域 → metadata.api）与 `EntityProvider`
（接口在 metadata.api、实现在源域，运行期由 metadata 调）**是同一个方向**，
都不需要元数据 import 源域内部包。所以"改走 push"不动 §0.3/§0.4 任何一条红线，
变的只是**谁触发、什么时候、失败怎么办**。

四条硬约束：
1. **源域只读本域 service，绝不写别人的表**（§0.4）。登记动作由元数据统一执行，源域只出数据。
   反向依赖同样禁止：元数据不得 import 任何源域内部包（`MetadataLayeringConventionTest` 守，T6）。
2. **`sourceHash` 由源域产出，且 push 与对账用同一个算法**——否则两条通道会互相把对方的行判成 CHANGED。
   落点建议：算法写在源域（`ModelSourceHash.of(po)` 之类），push 与 `cursorList` 都调它。
   元数据模块只比对不参与语义（与物理侧 `content_hash` 分开命名，就是为了防两件事被混用）。
   建议口径：`md5(name|summary|列结构摘要|domainIds)`——即"影响目录展示的字段"，不含内部实现细节。
3. **登记与采集走同一个 upsert 与 GONE 判定**。
   一轮 provider 返回空 → 零删除（§3.4 熔断对两条入口同等生效），
   否则"某模块 provider 抛异常被 catch 成空页"就会清空该类实体。
4. **`assetKey` 由源域交出，元数据不得替它拼键**（这是 §2.3 后果 6 的落地处，push 与对账同等适用）。
   现网的键就是各源域自己生成的：`modeling:model:{id}` 出自
   `ModelingLineageRegistrationService.java:275`，`semantic:field:{id}` 出自同文件 `:184`。
   元数据若按"更整齐"的格式另起一套（如 `model:{id}`），
   **唯一键不会拦**——两行各自满足 `uk (project_scope_id, asset_key)`，谁也不冲突，
   结果是同一实体在图里裂成两个节点（§2.3 后果 6）。
   元数据侧只做三件事：校验非空 / `length ≤ 512` / 前缀等于该类型登记的 `key_prefix`（49xxx 拒），
   然后**由 `MetadataKeyCodec` 这一处函数**算出 `fqn_hash = md5(lower(asset_key))`
   （全库只允许这一处，§10 测试 13 用 grep 守护），
   `fully_qualified_name` 则按 `type_def.fqn_pattern` 渲染，仅用于展示与反查。

> **与 asset 的 `AssetProvider` 关系**：形状刻意保持一致，但职责不同——
> asset 管"上架与目录归属"（治理动作），metadata 管"存在与可发现"（事实登记）。
> 两者输入同源（都用 lineage `asset_key`），所以不会产生两套键。
> **注意 push 定为主通道之后**，`EntityProvider` 收窄成"对账批量读"，
> 与 `AssetProvider`（同样是游标批量读）的职责差进一步缩小——但合并能省下的东西也随之变小
> （真正干活的入口换成了 `MetadataRegistrationApi`，那是 `AssetProvider` 没有的形状）。
> 是否合并为同一个 SPI 见 §11.2 待议 6，**结论等 130/131/135 落地再拍**。

### 3.2c push 的三个必须：post-commit、可重放、保序（✅ 蒸馏 §3.5 翻案 + 本仓库已有同形实现）

把"进目录"挂到源域的写路径上，就欠下三条必须兑现的约束。三条都不是我们的发明：

1. **必须在事务提交之后登记，不能在事务内。**
   OM 的形状：`jdbi3/EntityRepository.java:3998` 的 `postCreate()` 派发生命周期事件 →
   `events/lifecycle/EntityLifecycleEventDispatcher.java:374` 用
   `PostCommitActionQueue.runOrDefer(...)` 包住每一个 handler；
   该队列是 `ThreadLocal<List<Runnable>>` + `rollbackToCheckpoint(int)`
   （`util/PostCommitActionQueue.java:39-54`）——**业务事务回滚时待办动作直接丢弃**。
   两个方向的反例都要拦：事务内登记 → 业务回滚但目录留脏行；
   登记失败向上抛 → 目录故障拖垮业务保存（这是新增的跨模块耦合面，比脏行更糟）。
2. **失败必须落库可重放，不能 catch 一句 log 了事。**
   OM 五处 `SearchIndexHandler.java:58/77/95/117/137`（create/update/delete/softDelete/restore）
   全是 `catch → SearchIndexRetryQueue.enqueue(entity, op, e)`，由
   `SearchIndexRetryWorker` 每 5 秒轮询（`:63`）、指数退避（`:737`）、
   每分钟回收卡在 IN_PROGRESS 的行（`:794`），并开 API 给人看队列（`resources/apps/AppResource.java:462`）。
   **这条是本次决定里唯一推翻旧结论的地方**：蒸馏 §3.5 记下过这张表，当时以
   "我们暂不建搜索索引，也就没有索引重试队列"为由不建——那是"注册=定时拉"前提下的正确判断。
   push 成主通道后前提变了，**重试表必须建**（`yak_md_register_retry`，DDL 见下）。
3. **同一实体的两次登记必须保序，否则旧覆盖新。**
   OM 用 `OrderedLaneExecutor`：类注释写明 lane = `floorMod(hash(entityId), laneCount)`，
   同一实体的副作用永远串行到同一条单线程 lane，**且请求线程绝不因 lane 满而阻塞**。
   我们不必抄这个执行器——**本仓库已经有一个更省的同形实现**：
   `data-development` 的血缘登记走持久化 outbox
   （`V1__baseline_data_development.sql:134-149` 的 `yak_dev_lineage_outbox`，
   `uk (node_id, revision_id)` + `KEY (status, next_attempt_time)`），
   worker 是 `DevelopmentLineageWorker`：`@Scheduled(fixedDelayString =
   "${yak.development.lineage-outbox.poll-delay-ms:1000}")` → `outbox.due(20)` → `claim(task)` →
   干活 → `complete/fail`，`fail()` 的退避是 `Math.min(3600, 1L << Math.min(12, attempts))`
   （`DevelopmentLineageOutbox.java:36-41`），且 `projectScope.run(new ProjectContext(projectId, null), …)`
   先恢复项目上下文再动手（正是 §3.7 / T10 那一条）。
   **保序**由 `writer.writeIfLatest(node, revision, …)` 完成——只允许最新 revision 落库，
   旧任务的迟到重放被就地拒绝。元数据侧同构：登记命令带 `sourceUpdatedAt`（或源域版本号），
   比目录行现值旧则**只更 `last_collect_at`、不改内容**，等价于 `writeIfLatest`。
   > 选 outbox 而不是 OM 的内存 lane，理由很实在：**重启不丢**（本仓库的调度器是内存存储，
   > 重启后不补跑，见 §3.7），而目录登记恰恰是最不该在重启时丢的那类副作用。

```sql
-- 本模块自持（落 db/migration/yak-metadata），形状照抄 yak_dev_lineage_outbox
CREATE TABLE IF NOT EXISTS yak_md_register_retry (
    task_id     CHAR(36)     NOT NULL COMMENT 'UUID；主键即幂等令牌',
    project_id  BIGINT       NOT NULL COMMENT '源域上下文；worker 执行前用它恢复 ProjectContext',
    type_name   VARCHAR(64)  NOT NULL COMMENT '= yak_md_type_def.type_name',
    asset_key   VARCHAR(512) NOT NULL COMMENT '源域交出的键（§3.2b 硬约束 4）',
    source_id   VARCHAR(200) NOT NULL COMMENT '源域内主键，重放时回查用',
    operation   VARCHAR(16)  NOT NULL COMMENT 'REGISTER|UNREGISTER',
    status      VARCHAR(16)  NOT NULL COMMENT 'PENDING|IN_PROGRESS|DONE|DEAD',
    attempts    INT          NOT NULL DEFAULT 0,
    next_attempt_time DATETIME(6) NOT NULL COMMENT '退避后的下次执行时间',
    last_error  VARCHAR(2000) NULL,
    payload     JSON         NULL COMMENT '整份 RegisterCommand；重放时不再回查源域，避免时序依赖',
    source_updated_at DATETIME(6) NOT NULL COMMENT '本次变更的源侧时间，兼作去重令牌',
    create_time DATETIME(6)  NOT NULL,
    update_time DATETIME(6)  NOT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_yak_md_retry_change (project_id, type_name, asset_key, source_updated_at)
        COMMENT '同一次变更只排一条：并发重复登记天然合并（对齐 uk_yak_dev_lineage_outbox_revision）',
    KEY idx_yak_md_retry_due (status, next_attempt_time),
    KEY idx_yak_md_retry_project_due (project_id, status, next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='写时登记的失败重试队列（outbox；蒸馏 §3.5 的 search_index_retry_queue 形状，本地化）';
```

> 键**建在"变更"上而不是"状态"上**，这一步是照着本仓库那张 outbox 抄的
> （`uk_yak_dev_lineage_outbox_revision (node_id, revision_id)`）。
> 一开始把 `status` 放进唯一键是个错：那样同一实体只能留一条 `DONE` 行，
> "办结之后还能再登记"直接被判重挡死——正是 §6.2 用 `open_marker + CHECK` 才绕开的那类形状，
> 这里没必要重蹈：`source_updated_at` 本身就是天然的去重令牌。
> `status` 因此退回普通列（只在 `idx_…_due` 里做过滤）。
> **DONE/DEAD 行可定期清理**，因为这张表不是审计面——审计在 append-only 的 `yak_md_change`（§2.6）。
> 保留 T17 的告诫：唯一键的组成列一律 NOT NULL，`source_updated_at` 缺失时
> 由源域用写入时刻补齐，**绝不允许 NULL**（NULL 与 NULL 在 MySQL 唯一索引里彼此相异，键会形同不存在）。

**`DEAD` 是终态**：退避到上限（`attempts ≥ 12`）后不再重试，只留可观测。
必须给它一条出口——概览页（ticket 126）显示 `status='DEAD'` 计数，
且**对账任务（§3.2b 副通道）每轮会把这些实体重新捞回来**，
所以 DEAD 不等于永久丢失。这条闭环要说死，否则重试表会变成没人看的垃圾堆。

### 3.3 增量：双指纹（`content_hash` 结构 / `source_hash` 投影），服务端判增量（✅ 蒸馏 §3.3）

**指纹生成**（不是 md5(整行)，是 md5(规范化关键字段串)）：

```
表级 = join('|', type, comment, colFingerprint1, colFingerprint2, ...)
列指纹 = join(':', lower(name), lower(typeName), jdbcType, size??-1, scale??-1,
              nullable?1:0, ordinal, pk?1:0, normalize(comment))
```
- 列**按 `ordinalPosition` 升序**参与串联；ordinal 缺失或全 0 时退化为按 name 字典序
  （与 OM `_get_column_sort_key` 同一策略，蒸馏 §3.3）。
- `normalize(comment)` = 折叠连续空白为单空格 + 去首尾
  （OM `_normalize_whitespace`，`ingestion/src/metadata/utils/source_hash.py:46-52`）。
  不折叠空白 → 数据库注释里的换行差异会被判成"结构变更"。
- 大小写：`name`/`typeName` 转小写（MySQL 列名不区分大小写；Doris 同理），
  注释**不**转小写（中文无意义且丢信息）。
- **绝不进指纹**（对齐 OM 的 `VOLATILE_*` 清单，蒸馏 §3.3）：`href` 类链接、`deleted`、
  `inherited`、以及**认证的 appliedDate/expiryDate**。
  最后一条最容易被忽略：若 `expires_at` 进了指纹，"认证过期"会伪装成"表结构变更"，
  每天推一堆假变更。

**服务端判增量**：采集器/provider 一律把全量算出的 hash 交上来，**由仓库侧比对**：
```sql
INSERT INTO yak_metadata_asset
       (project_id, asset_key, asset_type, source_type, source_id, name, parent_asset_id,
        type_id, fully_qualified_name, fqn_hash, summary, md_attributes,
        provider_type, content_hash, source_hash, first_seen_at, last_collect_at,
        create_time, update_time)                                  -- ← 必填，见下
VALUES (…)
ON DUPLICATE KEY UPDATE
  id = LAST_INSERT_ID(id),                    -- 回带已存在行 id，upsert 标准手法
  content_hash = IF(content_hash <=> VALUES(content_hash), content_hash, VALUES(content_hash)),
  source_hash  = IF(source_hash  <=> VALUES(source_hash),   source_hash,  VALUES(source_hash)),
  last_collect_at = VALUES(last_collect_at),
  gone_at = NULL,                             -- 复活：曾 GONE 的实体回来即清标记
  ...
```
> **UPDATE 子句里没有 `asset_type` / `parent_asset_id` / `properties`**：
> 那是 lineage 的列（§2.3 后果 1/2 的 steward 分界）。元数据 INSERT 时按 `type_def.lineage_asset_type`
> 写一次图形状，之后不刷——否则两个模块在同一列上互相覆盖，正是 B 案要避免的那类事故。
>
> **全表只有一把唯一键，而且是 lineage 的**：`uk (project_scope_id, asset_key)`。
> 目录侧的 `fqn_hash` 由 `asset_key` 单点派生（§3.2b 硬约束 4），在它上面再建唯一键是冗余的，
> 且在现网存量行上根本建不起来（§2.3 的 1062 预演），所以本方案**只给它加普通索引**。
> 代价是说清楚：**没有第二把键替我们响**。若某条链路自己拼键、绕开派生函数，
> 数据库不会报错，只会让同一实体裂成两个节点。因此拦漂移的是**代码层不变量**而不是 DDL：
> 键由源域 provider 交出、`fqn_hash` 只允许在一处函数里派生、并由 grep 守护与 §10 测试 13 锁住。
> （初稿写的"A 案双唯一键 ⇒ 漂移当场 1062"随之作废，见 §2.4.3。）
> **`create_time` / `update_time` 必须显式带值**：`V1__baseline_lineage.sql:21-22` 把这两列定成
> `NOT NULL` 且**无默认值**（lineage 自己在 mapper 里写 `NOW(6)`，`LineageWriteMapper.xml:22`）。
> 本模块的 upsert 同理不能省，否则在 strict 模式下直接报错——
> 这是复用别人的表时会立刻撞上的那种小差异。
比对结果分 NEW / CHANGED / UNCHANGED / GONE 四类，写进 `yak_md_collect_run` 的四个计数。
**CHANGED 的判据按 provider_type 分岔（§1.3 两种指纹的落点）**：
`HARVESTED` 只比 `content_hash`，`REGISTERED` 只比 `source_hash`
（未提槽的属性变化仍在 `md_attributes` 里留痕，但不推 CHANGED，避免投影抖动刷屏）。
**效果**：一轮"什么都没变"的采集，除 `last_collect_at` 外零写入、零变更流水、零通知。
这替代了任何 state store——重跑幂等天然成立。

`last_collect_at` 单独刷、不触发 CHANGED 判定（蒸馏 §3.3 的"报告 no-change success 与完整
diff 结果一致"），这是 asset 已有的口径（"对账只刷在场时间不复活"，
`docs/data-asset/dev-plan.md` §4），保持一致。

**一条约束**：指纹字段清单**必须以常量集合的形式写死在一个类里，并用单测锁定**
（"改一个注释空格 → hash 不变"、"改一个列的 nullable → hash 变"、
"改 expires_at → hash 不变"三个用例）。蒸馏 §3.3 那份 volatile 清单是别人踩出来的，
不要靠注释提醒自己去遵守。

### 3.4 GONE 判定与质量熔断（✅ 蒸馏 §3.4，并加强）

OM 的 `deleteStale` 六道安全设计（空 seen 集 → 零删除、scope 不存在 → 零删除、
hash 比较、dryRun、单条独立事务、祖先覆盖跳过，
`EntityRepository.java:13378-13428`）**全部原样采纳**。前两条尤其救命，
原话（`:13383-13386`）：*"An empty seen-set cannot be distinguished from a connector run that
crashed or discovered nothing, so it must never be interpreted as 'every entity under the scope
is stale' — that would **silently delete the whole service/database**."*

**再加两道 OM 没有、但我们的处境需要的**：

- **坍塌比例熔断**：单轮 `gone / 上轮在场表数 > 30%`（阈值可配）→ 整轮标 `SUSPECT`，
  **不落任何 GONE**，只写 `collect_run` + 告警。理由：我们数据源数量小、单库表数波动大，
  一次连接超时或方言异常就能让"整库消失"看起来成立。OM 靠 scope 粒度天然分散了这个风险，
  我们必须显式拦。
- **连续两轮缺失才 GONE**：与 asset 的 `SOURCE_GONE 要求连续两个对账周期缺失（默认窗口 7 天）`
  完全对齐（`docs/data-asset/dev-plan.md` §4）。

GONE 的处理是**软删**（`gone_at` 置时间），不物理删，不删 `yak_md_label`，
但**从 lineage 图中撤销该节点**（否则血缘图上挂着一张不存在的表）。
撤销走 §3.6 的 API，且只有确认 GONE 才撤销，`SUSPECT` 一律不动。

### 3.5 统计采集：只补 SPI 缺的，且不碰存储量

`DataSourceCatalog` 不给：行数、分区、最后 DDL 时间。
按蒸馏 §3.2 的判定，**只在统计这一层双方言**：

```java
public interface MetadataStatsProvider {
  boolean supports(String dbType);
  /** 返回 null = 该方言无此项能力，采集器记 UNKNOWN 而不是 0。 */
  TableStats fetch(DataSourceRef ref, String database, String table);
  record TableStats(Long rowCount, String partitionKey, Integer partitionCount,
                    LocalDateTime lastDdlTime, Map<String, String> raw) {}
}
```
- 实现按 Spring `List<MetadataStatsProvider>` 注入、按 `supports()` 路由（同 `AssetProvider` 的
  Registry 模式，`asset/reconcile/AssetProviderRegistry.java`）。
- MySQL/Doris 两个实现；SQL 一律"候选语句按序尝试、首个可用即采纳"，
  **直接沿用 `StorageSnapshotService.statementsFor()` 的形状**（含 `SAFE_DB` 白名单校验，
  这是库名拼进 SQL 的必要防线）。
- **`size_bytes` 不在这里**：详情/列表页展示存储量时读 lifecycle
  `yak_lc_storage_snapshot`（键 `project_id, snapshot_date, datasource_id, table_name`，
  最新一天）。跨模块只读 → 走 lifecycle 的 api 包，
  本模块**不建 mapper 去查 `yak_lc_*` 表**。
- 一期 `MetadataStatsProvider` 可以只有 MySQL 实现（行数走
  `information_schema.TABLES.TABLE_ROWS` 的近似值，UI 必须标注"近似"）；
  Doris 分区留待有实例时验证，**不得写成已验证**。

### 3.6 前置改造：抽 `LineageRegistrationApi`（P0 阻塞项）

`LineageRegistrationService` 现在在 `…/lineage/registration/` 包里
（`registerAsset(RegisterAssetCommand):29`、`registerRelation:34`、
`registerAssetsBatch:39`、`registerRelationsBatch:45`，两个 record 在 `:50`/`:96`），
**不在 `api` 包**。§0.3 要求跨模块只走 `api`，故必须先把接口抽到
`io.yak.ops.business.lineage.api.LineageRegistrationApi`，实现留在原处
（模板：`modeling/api/ModelTtlQueryApi.java` + `modeling/catalog/ModelTtlQueryApiImpl.java`，
本仓库已在用的写法）。

**这是 lineage 侧的既有 API，不是新能力，也不改变 lineage 行为**，
所以可与其他 P0 项并行，风险只在"改包路径影响既有 import"，一次全量重命名 + 编译即收敛。

### 3.7 调度：Bridge + Handler 两段式（✅ 沿用，不参考 OM）

OM 的 `AppScheduler` 是 Quartz 在 JVM 内（蒸馏 §3.6），但**我们已有同构且更适配的实现**，
直接照抄 lifecycle：

- `MetadataScheduleEngineBridge`：`YakScheduleGateway.save(new ScheduleDefinition(key, name,
  ScheduleTrigger.cron(cron, ZoneId.systemDefault()), new ScheduleTarget(HANDLER, payload),
  SchedulePolicy.defaults(), true, metadata))`；
  登记前 `gateway.snapshot(scheduleName).isPresent()` 短路保幂等。
- `MetadataCollectScheduleHandler implements ScheduleHandler`，`@Component(HANDLER)`。
  **必须**先 `projectScope.call(new ProjectContext(projectId, null), () -> …)` 恢复项目上下文
  再执行——调度线程无 HTTP 头，不恢复则 `@ProjectScope` 全链路失效、
  `CurrentProject.requireProjectId()` 直接抛。这是采集任务最容易写错的一处
  （OM 无此概念，替不了我们；模板
  `lifecycle/schedule/LifecycleTtlScheduleHandler.java`）。
- 调度器未装配时 `gateway.available()` 为 false → **静默跳过登记，不阻断启动**
  （lifecycle 现例：`if (!gateway.available()) return;`）。
- 同时提供 `POST /api/v1/metadata/collect-jobs/{id}/run` 手工触发
  （**开发/演示主路径**，因为 Quartz 内存存储在重启后不补跑）。

push 通道（§3.2b）另带来两种调度形态，**不要混用同一套**：

- **重试 worker：`@Scheduled(fixedDelayString = "…")`，秒级轮询，不走 `YakScheduleGateway`。**
  理由有两条：① 它要的是"业务保存后 1 秒内目录跟上"，cron 的最小粒度给不了；
  ② 它是**修复机制**，必须连"调度器没装配"这种情况都能跑——
  挂在平台调度器上等于让修复者依赖被修复的对象。
  模板逐字照抄本仓库现例：`development/lineage/DevelopmentLineageWorker.java`
  （`due(20)` → `claim` → 干活 → `complete/fail` 退避）
  + `DevelopmentLineageSchedulingConfiguration`（`@EnableScheduling` 单独一个配置类，
  便于按开关摘除）。配置项命名沿用同风格：`yak.metadata.register-retry.poll-delay-ms:1000`。
- **投影对账任务：仍走 `YakScheduleGateway` 的 cron，与物理采集共用 `collect_job` / `collect_run`。**
  区别只在 `provider_type='REGISTERED'`（§3.3 末的判据分岔），产出的计数同样是
  NEW/CHANGED/UNCHANGED/GONE 四类。**不新开第三张运行历史表**。
- 两类调度**都必须**先恢复项目上下文再动手（`projectScope.run(new ProjectContext(projectId, null), …)`，
  本条上方已述；worker 侧同样适用，`DevelopmentLineageWorker.process` 就是这么写的）。

---

## 4. 查询设计

### 4.1 四个接口

| 端点 | 用途 |
|---|---|
| `GET /api/v1/metadata/search` | **跨类型统一搜索**（一次查询命中所有实体类型，类型是 facet；分页 + 筛选 + 聚合计数） |
| `GET /api/v1/metadata/entities/{id}` | 实体详情（属性/层级/历史/标签/**实时**外部指标/血缘入口）；按 `typeName` 出不同面板 |
| `GET /api/v1/metadata/entities` | 批量按类型+键取实体（供选择器、列表页内联展示用） |
| `GET /api/v1/metadata/types` | 元模型自省：已注册类型 + 各自字段定义（前端渲染表单/列/筛选项的唯一来源） |

前缀 `/api/v1/metadata` → **必须登记** `PROJECT_REQUEST_RULES`，见 §9 陷阱 T1。

**统一检索是这次改需求的核心诉求，所以它必须是"一次查询"而不是"N 次查询"**：
`MATCH … AGAINST` 打在 `yak_metadata_asset` 的一张表上，类型（`type_id`，join 元模型取名）只是一个 `GROUP BY` 维度。
这是 §2 选统一实体表而非分表的直接回报。

### 4.2 参数面照搬 OM 的搜索契约（✅ 蒸馏 §4.4）

不重新发明。参数名与语义**现在就按 `/v1/search/query` 定**
（`SearchResource.java:153-227`，逐个核对过：`q, index, deleted, from, size, search_after,
sort_field, sort_order, track_total_hits, query_filter, post_filter, fetch_source,
include_source_fields, exclude_source_fields, getHierarchy, explain`）。

一期实现子集：

| 参数 | 一期语义 |
|---|---|
| `q` | 空 = 浏览模式；非空走 `MATCH(name, display_name, summary) AGAINST(? IN BOOLEAN MODE)`，中文靠 ngram；再按 `field_def.searchable=1` 追加提槽列的 `LIKE`/精确条件（§4.5） |
| `index` | **多值**，取实体类型名（API 层是 `type_name`，落库按 `type_id` 过滤；如 `index=table&index=dataModel`）；空 = 全类型。**不查 `asset_type`**（命名规则见下）。对应 OM 的 index 抽象，日后换 ES 只改实现 |
| `queryFilter` | JSON：`{typeName, providerType, datasourceId, databaseName, layerCode, domainId, owner, entityStatus, tagged, hasSummary, attr.<field>=…}` — **参与聚合计数** |
| `postFilter` | 同结构 — **不影响聚合计数**（facet 联动的关键区分，必须一期就分对） |
| `includeFields`/`excludeFields` | 返回裁剪 |
| `searchAfter` | 深分页游标（一期 = `(sortValue, id)` 组合键），**替代 offset 深翻** |
| `sortField`/`sortOrder` | 白名单校验，防注入 |
| `trackTotalHits` | 默认 false：总数走 `EXPLAIN` 估算或分桶计数，不每页 `COUNT(*)`（无界禁止，§0.11） |
| `getHierarchy` | 1 = 返回时带父链（column 带其 table），一次自连接而非递归 |
| `explain` | 一期返回命中的后端类型、实际 SQL 与是否走 LIKE 降级，调试用 |

`attr.<field>` 这一族筛选**只能作用于 `field_def` 里声明了的字段**，
未声明即 49xxx 报错——这是防止 `attributes` 变成"什么都能塞但查不动"的黑洞的关口。

**命名规则（B 案带来的，必须写死，否则实现期会造出第三个名字）**：
类型判别符在 **API/JSON 层叫 `typeName`**（= `yak_md_type_def.type_name`），
在**落库侧是 `yak_metadata_asset.type_id`**（join 元模型表取名，§2.3 后果 1）。
**不要**把 `asset_type` 当类型判别列——它是 lineage 的图形状枚举（`TABLE`/`COLUMN`/…），
存量行已证明它与实体类型不等价。
本文档与后续代码里出现 `entityType` / `type` / `kind` 这类第三个名字即视为契约违反。
同理，属性袋**逻辑名/DTO 名为 `attributes`，落库列为 `md_attributes`**（绝不写进 lineage 的 `properties`，§2.3 后果 2）。

### 4.3 中文检索的现实边界

ngram `token_size=2` 下，**单字查询命不中**（少于一个 n-gram）。
一期处理：`q.length() == 1` 时降级为 `LIKE '%x%'` 并在 `explain` 里标注走了降级路径。
**不做前端输入长度限制**（那是在藏问题），只保证不返回空结果了事后感不到原因。

`MATCH … AGAINST` 在 BOOLEAN MODE 下需转义 `+ - > < ( ) ~ * " @`，
**必须在服务层集中做转义**，转义函数单测覆盖（这是搜索接口的注入面）。

### 4.4 ES 接缝与升级阈值

`MetadataSearchBackend` 接口，一期唯一实现是 `MysqlMetadataSearchBackend`。
**阈值写在这里，防止将来靠感觉决策**（计量口径已随 §2 从初稿的"列数"改为**目录总行数**——
统一表之后一个索引服务所有类型，容量与相关性是一起退化的）：
- 目录表 `yak_metadata_asset` 在场行数 > **10 万**且搜索 P95 > 1.5s；或
- 需要相关性打分排序（"最相关的实体在前"。MySQL FULLTEXT 的 BM25 近似在我们这个字段数下形同虚设，
  **且混装类型后跨类型的相关性更不可信**，见 §4.6）；或
- 需要向量/语义检索（自然语言找表）。

三条任一成立前引入 ES 都是纯负债（多一套集群、多一个 reindex 重试队列，蒸馏 §3.5）。
> **但别把 §3.2c 的 `yak_md_register_retry` 当成"这笔账已经付过了"**：它保的是**MySQL 目录行不丢变更**，
> 与倒排索引无关。真到引入 ES 那天，索引侧还要**第二份** outbox + reindex 通道
> （OM 正是把它们分成两层，蒸馏 §3.5），届时接缝在 `MetadataSearchBackend` 后面加实现、不改源域挂钩。

### 4.5 扩展字段的可搜性：由元模型驱动，不由代码驱动（✅ 蒸馏 §1.3 第三层）

**这条是"可扩展"能否兑现的真正关口。** OM 的实测教训是：
扩展字段**存进 json 了不等于搜得到**——它专门写了
`CustomPropertySearchFields.java` 把值映射到 `extension.<prop>`，
并且**每种资产类型一份搜索配置**（`AssetTypeConfiguration.searchFields` → `FieldBoost`，
含 boost 与 `exact` 标记）才让它参与检索（蒸馏 §1.3）。
如果我们的 provider 加完 `field_def` 还要改搜索代码，那"加字段免费"就是假的。

所以搜索条件**由 `field_def` 在运行时生成**，无代码分支：

| `match_type` | 生成的条件 | 前提 |
|---|---|---|
| `text` | 并入 `q` 的 FULLTEXT 目标（仅当已提槽到 `s_str_*`） | `searchable=1` 且有槽位 |
| `exact` | `slot = ?`（走索引，可作 facet） | 必须已提槽 |
| `like` | `slot LIKE CONCAT('%',?,'%')`（**全表扫，需在 UI 标注为慢查询**） | 必须已提槽 |
| `range` | `slot BETWEEN ? AND ?` | 必须已提槽（`s_num_*`/`s_date_*`） |

三条规则：
1. **`searchable=1` 但没提槽 → `field_def` 保存时直接拒绝（49xxx）**，
   不要等到搜索时才静默失效。"配了不生效"是最难查的一类 bug。
2. **`searchable` 默认 0。** 让每个新字段自动进全文检索，等于允许低价值字段污染排序；
   OM 用逐类型配置就是在防这个。开搜必须显式勾选并指定 `match_type` 与 `boost`。
3. **`boost` 一期只影响排序权重表达，不承诺 BM25 精度**
   （实现为 `CASE WHEN … THEN w` 加权和，MySQL 侧近似）。
   真正的排序质量要等 §4.4 的 ES 阈值触发——**不要在这里假装我们能做到"最相关的表在前"。**

槽位分配走 `MetadataSlotRegistry` 集中登记（§2.4.1），
两个类型抢同一个槽位时**报错而不是静默复用**。

### 4.6 混装的第一个代价：列会淹没其他类型（统一表的账单，必须一期就还）

统一实体表换来了"一次查询跨类型"（§4.1），代价是**所有类型挤在同一个 FULLTEXT 索引里，
而它们数量差一个量级**。按 §1.4 实测：列/表 ≈ **12.6 : 1**，
而内部实体（模型 40 + 标准字段 148 + 域 28 + 指标 4）合计 220 条，
数量级和表本身（373）相当。后果很具体：搜 `user_id`、搜"金额"、搜任何列名规律词，
**第一页会被列实体占满，用户找表反而找不到**——这正是 OM 用 `type` filter + 逐类型 boost
在处理的问题，我们躲不掉。

一期的处置，仍然**不写代码分支**（与 §4.5 同一套机制，配置在元模型里）：

1. `yak_md_type_def` 增加两列：`search_default_weight FLOAT`、`search_include_by_default TINYINT`
   （ticket 128 一起做，别等到搜索页上线才发现缺）。
2. 默认检索面 = **表 / 模型 / 标准字段 / 业务域 / 指标**；`tableColumn` 默认 `include=0`，
   但**在结果里以"命中 N 列"的聚合形式露出**，点进去是该表列的过滤视图。
   这样"列名很特殊只有列命中"的情况仍可见，只是不占行。
3. 用户显式传 `index=tableColumn` 时列直接出行——能力不减，只是默认值调过。
4. 排序权重一期只做 `type_def.search_default_weight` 的乘性加权（表 > 列），
   **不承诺相关性质量**（口径同 §4.5 规则 3）。

> 这条是"统一表 vs 分表"取舍里**唯一真正偏向分表的论据**，写出来而不是藏起来：
> 分表天然不会互相淹没。我们的答案是"用元模型配置解决，而不是退回 N 张表"——
> 若将来连加权都压不住，那属于 §4.4 第二条阈值成立，换 ES，而不是拆表。

---

## 5. 元数据建模与消费方（本方案的收益兑现处）

采集本身不是目的。**这一节决定这套元数据是资产还是又一张冷表。**

### 5.1 消费方一览（按价值排序）

| # | 消费方 | 现在怎么做 | 接元数据后 | 状态 |
|---|---|---|---|---|
| 1 | **modeling 符合性对账** | 模型列与物理列无系统性比对 | 物理列 vs `yak_modeling_model_column` 三方差异报告 | 新建，本方案核心 |
| 2 | **quality 表/列选择器** | 实时打 `DataSourceCatalog` | 读已采集元数据，秒开 + 可筛"有注释/无注释" | 已有挂载点 |
| 3 | **asset TABLE provider** | `AssetSourceType` 只有 `MODEL/METRIC/DATASET/DASHBOARD/CHART/TASK/MANUAL` | 新增 `TABLE`，物理表进资产台账 | 依赖 asset |
| 4 | **lineage 物理节点** | 只有模型/指标/数据集节点，血缘图缺物理层 | 采集即登记，边可接上真实表 | 依赖 §3.6 |
| 5 | **semantic 分层覆盖度** | 分层只配了库，不知库里有什么 | 按 `layer_code` 统计表/列/注释覆盖率 | 低成本附带 |
| 6 | **metric 源表校验** | 指标指向的表是否还存在，无系统性检查 | 存在性 + 列存在性校验 | 二期 |

**#1 是本平台"数据治理"成色的分水岭**。此前分析指出的"标准落地校验缺失"，
缺的就是物理元数据这一块拼图。

**这张表只写了半个方向。** 上表是"别人消费元数据"；本次需求修正后还有一条反向收益：
`dataModel` / `standardField` / `domain` / `metric` 这些内部实体**第一次获得一个跨类型统一发现入口**
（§4.1 的 `search`，一次查询命中物理与逻辑两侧）。此前它们各自散在
modeling / semantic / metric 的列表页里，只能按域逐级点进去，无法"我记得有个跟毛利有关的口径"这样找。
所以元数据中心不只是一张采集落库表，它是**平台级的发现层**——
这条决定了 §4 的检索必须是跨类型的真统一，而不是各模块搜索的聚合壳。

### 5.2 符合性对账（消费方 #1）详细设计

**归属：不放在元数据模块。** 元数据只出物理列（事实），符合性判定放 modeling
（它已有 `StandardFieldMatcher`、`ModelStructureService`、`yak_modeling_model_column`）。
理由：判定规则是建模域知识，放元数据会让元数据反向依赖 semantic + modeling，
破坏"元数据是下层事实供给方"的定位。

元数据侧只提供 API（**都读同一张 `yak_metadata_asset`，只是 `type_id` 过滤不同**）：
```java
// io.yak.ops.business.metadata.api.MetadataQueryApi  （新，P0 一起建）
PagingData<EntityDTO> search(EntityQuery q);                    // 跨类型统一入口
Optional<EntityDTO>   getEntity(long id);
List<EntityDTO>       listChildren(long parentId, String typeName);   // 表→列
/** ↓ 两个便捷方法**只是 type_name 过滤的语法糖**，不是第二套模型；实现里禁止出现独立表 */
List<EntityDTO>       listPhysicalColumns(String datasourceId, String database, String schema, String table);
Optional<EntityDTO>   findPhysicalTable(String assetKey);
```
`EntityDTO` 携带 `typeName + attributes + slotValues`，消费方按 `typeName` 解释属性；
字段清单的**权威来源是 `GET /api/v1/metadata/types`（§4.1）**，不是某个常量类——
否则"加字段免改表"会在 API 边界上被重新写死。

modeling 侧新增比对（**复用既有 `StandardFieldMatcher`，禁止另写一份匹配规则**）：

```
差异矩阵（按列）：
  MISSING_IN_PHYSICAL   模型有、物理无      → 模型不可发布（阻断）
  MISSING_IN_MODEL      物理有、模型无      → 提示补登（不阻断）
  TYPE_MISMATCH         两侧都有、型不兼容   → 警告，附兼容表（int→bigint OK，反向不 OK）
  NULLABILITY_MISMATCH  物理 NOT NULL、模型允许空 → 警告
  PK_MISMATCH           主键标记不一致       → 提示
  COMMENT_EMPTY         物理无注释           → 计入治理缺口（不阻断）
  STD_UNGOVERNED        未命中标准字段        → 生成 yak_md_task 待办
```
`STD_UNGOVERNED` 的判定直接调 `StandardFieldMatcher.match(...)`，
并且**沿用它的 `isAuthoritative()` 口径**（`modeling/governance/StandardFieldMatcher.java:35-38`：
只有 `exact`/`comment` 算命中，`fuzzy` 只作建议）。
该类 javadoc 里写明"**故意不做同类型即命中**"并给了理由（*"错标的治理状态是静默的；
留成未命中可被修正，代价远小于误标"*）——**这个判断必须继承，不得为了匹配率放宽。**

**这是"标准落地校验"从口号变成可执行的地方，也是全方案 ROI 最高的一节。**

### 5.3 quality 选择器（消费方 #2）

`QualityTableAssetRepository` 已存在（`QualityLayeringConventionTest` 里可查）。
改造：选择器数据源从"实时 catalog"切到"元数据查询 API"，
**保留实时 catalog 作为元数据缺失时的回落**，并在 UI 标注数据来源（已采集 / 实时）。
理由：采集有延迟（默认每日一次），切干净会让"刚建的表选不到"变成新问题。

### 5.4 asset TABLE provider（消费方 #3）

`AssetProvider` SPI 已是正确形状（`asset/api/AssetProvider.java`：
`sourceType()` / `cursorList(AssetCursorQuery)` / `refresh(String sourceId)`），
`AssetSourceType` 加 `TABLE` 一枚，实现类放**元数据模块**
（`metadata/asset/MetadataTableAssetProvider.java`）。
约束（SPI javadoc 原文）：*"assetKey 必须复用本域血缘登记键生成器，与 `yak_metadata_asset` 同源"*
→ 与 §2.3 后果 6、§3.2b 硬约束 4 是**同一条约束的三次表述**（asset SPI / metadata provider /
lineage 生成器），三方都只复用、不另起键——这才是"同源"的真正保障，
不靠任何一把唯一键拦（§2.4.3）。

### 5.5 语义回流（消费方 #4/5）

采集到的中文注释是 semantic 标准字段/指标的**冷启动语料**。一期只做到：
详情页展示"该列注释"与"关联标准字段"并排，人工点"沉淀为标准字段"
（调 semantic 既有接口，不新造写入路径）。**不做 NLP 自动抽取**——
错误沉淀会污染标准字段库，且不可逆。

---

## 6. 治理层（✅ 蒸馏 §4.1/§4.2/§4.5）

一期只借三件，且都是"表 + 一列"级别的改造，不借 OM 的框架。

### 6.1 标签溯源 `yak_md_label`

对齐 OM `tag_usage` 的 `labelType` × `state` 双维度（`v001:369`）：

```sql
CREATE TABLE IF NOT EXISTS yak_md_label (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    project_id   BIGINT NOT NULL,
    asset_id     BIGINT NOT NULL COMMENT 'yak_metadata_asset.id；表/列/模型/标准字段一律同构引用',
    label_code   VARCHAR(64) NOT NULL COMMENT '标签字典码，复用 asset 标签体系',
    label_type   VARCHAR(16) NOT NULL COMMENT 'MANUAL|AUTOMATED|PROPAGATED|DERIVED',
    state        VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED'
                 COMMENT 'SUGGESTED|CONFIRMED：机器/继承默认 SUGGESTED，等人工确认',
    applied_by   VARCHAR(64) NOT NULL,
    applied_at   DATETIME(6) NOT NULL,
    reason       VARCHAR(512) NULL COMMENT '为什么打这个标（回答"凭什么是 PII"）',
    derived_from BIGINT NULL COMMENT 'PROPAGATED 时指向父标签行；不建通用传导框架（蒸馏§4.3）',
    expires_at   DATETIME(6) NULL COMMENT '仅 CERTIFICATION 用；不进 content_hash（§3.3）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_md_label (project_id, asset_id, label_code),
    KEY idx_yak_md_label_asset (asset_id),
    KEY idx_yak_md_label_state (project_id, state, label_type),
    KEY idx_yak_md_label_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据标签（含溯源与认证）';
```

**这里能看出统一实体表白捡的一笔收益**：初稿的 `target_type VARCHAR(32)` 只列得出 `TABLE|COLUMN`，
每接一类新目标（模型？标准字段？）都要扩枚举 + 加分支；
换成 `asset_id` 后治理面对所有类型**同构**，OM 也是这么做的（`tag_usage` 挂在实体 id 上）。
按类型统计标签覆盖率时 JOIN `yak_metadata_asset` 取 `type_id` 即可，不需要在本表冗余类型列——
冗余一份就等于多一个会漂移的事实（§0.2）。

三个坚持：
1. **`state` 与 `label_type` 分离**。自动/继承标注一律 `SUGGESTED`，人工点确认才 `CONFIRMED`。
   → 直接治好"自动标注淹没人工判断"这个治理平台通病（蒸馏 §4.1）。
2. **`reason` 必填于 `AUTOMATED`/`PROPAGATED`**。没理由的机器标签不可信。
3. **认证 = 特定 `label_code` + `expires_at` 非空**，不是布尔位（蒸馏 §4.2）。
   无期限认证等于永久免检。**`expires_at` 绝不进 `content_hash`。**

**继承（🟡 蒸馏 §4.3）**：域/分类从表传导到列时，写新行 `label_type='PROPAGATED'` +
`derived_from` 指父，重算任务按 `derived_from` 扫。**不引入 `PropagationDescriptor` 式通用框架。**
OM 自陈的坑要记住：传导会产生"内容没变但溯源过期"的状态
（`EntityRepository.java:4571` 注释），所以重算必须是**幂等全量重刷**，不是增量。

### 6.2 状态与待办 `yak_md_task`

借 `EntityStatus` 的 7 值 + **默认 `Unprocessed`** 的语义（蒸馏 §1.1，
载体 `type/status.json`）：`Unprocessed` 与 `Rejected` 的区分正是
"没人看过"和"看过但不合格"——治理度量必须能分这两个。

```sql
CREATE TABLE IF NOT EXISTS yak_md_task (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    project_id   BIGINT NOT NULL,
    task_type    VARCHAR(32) NOT NULL COMMENT 'FILL_COMMENT|CONFIRM_LABEL|FIX_CONFORMANCE|REVIEW_GONE',
    asset_id     BIGINT NOT NULL COMMENT 'yak_metadata_asset.id；同 §6.1，对所有实体类型同构',
    entity_status VARCHAR(24) NOT NULL DEFAULT 'Unprocessed',
    assignee     VARCHAR(64) NULL,
    created_by   VARCHAR(64) NOT NULL,
    due_date     DATE NULL,
    resolved_at  DATETIME(6) NULL,
    resolve_note VARCHAR(512) NULL,
    open_marker  BIGINT NOT NULL DEFAULT 0
                 COMMENT '去重位：未办结恒 0（互斥），办结时由服务写入自身 id（彼此相异）',
    create_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_md_task_open (project_id, task_type, asset_id, open_marker),
    -- 把"忘了刷 marker"这种半更新钉死在库层，而不是靠 code review 记
    CHECK ((open_marker = 0) = (resolved_at IS NULL)),
    KEY idx_yak_md_task_assignee (assignee, entity_status, due_date),
    KEY idx_yak_md_task_project (project_id, entity_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据治理待办';
```

**这里有两处"看起来对、实际错"，都在本机 MySQL 8.0.46（`yak_security`）上实测过：**

1. **初稿的 `UNIQUE (…, resolved_at)` 语义是反的。** 本意"同一目标同类待办只允许一条未处理"，
   但 **MySQL 唯一索引里 NULL 彼此相异**，所以开放待办（`resolved_at IS NULL`）**可以无限重复插入**，
   反倒把已办结的历史行互相挤掉。
2. **直觉解法"用生成列 `IF(resolved_at IS NULL, 0, id)`"在本仓库不可用**：
   MySQL 直接拒绝——`3109 Generated column 'x' cannot refer to auto-increment column`（已实测）。
   所以 `open_marker` 只能是**服务层写入的普通列**，并用 `CHECK` 把
   "marker=0 ⟺ 未办结"这个不变式压到库层兜住遗漏。

实测三条结论（临时表，会话结束即销毁，未在任何库中留对象）：
```
同类同目标第二条未办结待办  -> 1062 Duplicate entry '…-0' for key 'uk_yak_md_task_open'   ✅ 拦住了
只写 resolved_at 忘了刷 marker -> 3819 Check constraint 'zz_task_chk_1' is violated          ✅ 漏更新会被拒
按 id 刷 marker 后再建同目标  -> OK                                                        ✅ 历史行不互斥
```
→ 必测用例照此三条写；办结语句必须是 `SET resolved_at=?, open_marker=id`。
（边界：若将来要直接插入"已办结"的历史行，插入时拿不到 id，须先插后更——本方案一期不回填历史待办，不构成约束。）

**❌ 不引 Flowable/BPMN**（蒸馏 §4.5）：3 步流程用状态列 + 一张迁移记录表足够，
多引一个引擎的收益远小于理解与运维成本。
**但 OM 那条铁律原样保留：提单人不能自审（self-approval can never happen）**，
在状态迁移服务里硬校验 + 单测锁定。

### 6.3 明确不采纳

| OM 有 | 不做 | 为什么 |
|---|---|---|
| Flowable `WorkflowDefinition` | ❌ | 见 §6.2 |
| JSON Schema → Java/TS 代码生成流水线 | ❌ | 蒸馏 §1.2。**只否 codegen**，§2.2 的元模型（类型与字段作为行）照借 |
| 每种实体类型一张表（≈700 张） | ❌ | 与"统一检索"直接矛盾（§1.1），且每类都要新表 + 新检索分支；蒸馏 §2.2 |
| Python 连接器进程 | ❌ | 蒸馏 §0/§3.1 |
| 一期引入 ES | ❌ | §4.4 有阈值 |
| DataProduct / ontology / knowledge page | ❌ | 与当前痛点无关 |
| 120+ 方言反射层 | ❌ | §3.2 SPI 已覆盖 |
| 运行时 JSON Schema 校验器 | ❌ | `field_def` 的约束用服务层校验实现，不引 schema 引擎（蒸馏 §1.3 代价表） |
| 数据 profile / 内容采样 | 二期 | 需采样配额与脱敏前置（数据安全域） |
| 血缘自动推断（SQL 解析） | 不做 | 已有 modeling 侧人工/派生血缘 |

---

## 7. 分期与 Ticket

| 里程碑 | 内容 | Ticket |
|---|---|---|
| M0 底座 | 模块可启动、菜单可见、表结构就位（含共表目录列 ALTER） | 110~112, 133, 134 |
| M0b **元模型** | 类型/字段两张定义表 + 两个 registry + 类型自省接口 | 128~129 |
| M1 采集闭环 | SPI 采集 + 指纹增量 + GONE 熔断 + 调度/手工触发 | 113~116 |
| M1b **注册闭环** | **写时登记主通道**（`MetadataRegistrationApi` + `yak_md_register_retry` outbox + 重试 worker）+ 四个源域挂钩 + **对账副通道**（`EntityProvider` 批量：模型/标准字段/业务域/指标） | 130~131, 135 |
| M2 查询 | **跨类型**搜索/详情/实体批量（MySQL 后端 + ngram + 类型 facet） | 117~118 |
| M3 消费打通 | Lineage 登记 + modeling 符合性 + quality 选择器 | 119~121 |
| M4 界面 | 采集任务页 + 浏览/**统一搜索**/详情 + 按元模型渲染 | 122~123, 132 |
| M5 治理 | 标签溯源 + 认证过期 + 待办 + 概览驾驶舱 + 规则对齐守卫 | 124~127 |

> **编号只做 ID，不按拓扑排序**（110~127 已被 §8/§9/§11 引用，不动；
> 128~135 是本次需求修正后新增的项，其中 128~129 在实施顺序上排在 114 之前，133 紧跟 112，
> 134 是 114/130 的前置但**依赖 lineage / data-development 排期**，是唯一一张不能独自开工的票，
> 135 是 130/131 的**副通道**（push 漏了靠它长回来），可与 131 并行）。

| 编号 | Ticket | 阶段 | 关键产出 | 状态 |
|---|---|---|---|---|
| 110 | 模块骨架：pom/BOM/`yak-ops-business/pom.xml` 注册 + `MetadataPersistenceConfiguration`（`yakMetadataFlyway` / `flyway_schema_history_metadata`）+ 契约文件集 6 份 | P0 | 可编译可启动 | backlog |
| 111 | 菜单权限 V2033（group `data-metadata` + 4 子页）+ `securityMenuCodes.ts` + `navigation.ts` + `navigationMenuContract.test.ts`（**并把 V2028/V2032 补进 `CATALOG_EXTENSION_MIGRATIONS`**） | P0 | 菜单可见且过契约测试 | backlog |
| 112 | 存储层（本模块自持的 7 张）：`yak_md_asset_extension` / `_collect_job` / `_collect_run` / **`_register_retry`（§3.2c 的 outbox）** / `_change` / `_label` / `_task`（元模型 2 张在 128，共管的 `yak_metadata_asset` 在 133）+ PO + Mapper + `MetadataErrorCode`(49001~) + `DATA_METADATA` namespace | P0 | Flyway 可跑通；迁移落 `db/migration/yak-metadata` | backlog |
| 133 | **共表改造（B 案专属）**：§2.3 的目录列 `ALTER` 以**新版本文件**落 `db/migration/yak-lineage`（绝不改 `V1__baseline_lineage.sql`）+ 7 个提槽生成列一次建齐（§2.4.1 实测只能 `ALGORITHM=COPY`）+ `ngram` FULLTEXT + **`type_id` 判别列与 `lineage_asset_type` 映射列成对出现**（后果 1：目录判别走 `type_id`，`asset_type` 只图表形状）+ **不新增唯一键**（`fqn_hash` 仅普通索引，理由见后果 4/§2.4.3）+ **列 steward 契约**写进两份 `ARCHITECTURE.md`（lineage 拥有图语义列 / metadata 拥有 `md_attributes`、`s_*` 与目录治理列，两侧都不得写对方列）+ **lineage 写入路径回归**：证明目录列在 `LineageWriteMapper` 的两种 upsert 下不被清空、`project_id` 不被覆写（§2.3 后果 2/5，T19） | P0 | 一张表两套语义，靠契约而非靠自觉 | backlog |
| 113 | **前置改造**：抽 `LineageRegistrationApi` 到 `lineage/api`（§3.6）+ 既有引用全量迁移 + lineage 测试不破 | P0 | 跨模块只走 api | backlog |
| 134 | **跨模块 Java 改动（§2.3 后果 7，B 案专属）**：① 把 `TableIdentityResolver.PhysicalTableIdentity#assetKey()` 的纯字符串逻辑**下沉到 `yak-ops-common`**，data-development 改为引用同一份、**行为逐字不变**并由其现有测试守护（元数据不得 import 别模块内部包，§0.3）；② 给 `LineageAssetType` 增加 `DATABASE_SERVICE` / `DATABASE` / `DOMAIN` 三个常量（`asset_type` NOT NULL 且被 `valueOf` 解析，后果 1）。**须与 lineage / data-development 一起排期，不由元数据单方面提交** | P0 | 目录与 lineage 共用一把键的物理前提；114/130 的前置 | backlog（待跨模块协商） |
| 128 | **元模型落地**：`yak_md_type_def`（含 §4.6 的 `search_default_weight` / `search_include_by_default` 两列，**随建表一起出，不留后补 ALTER**）+ `yak_md_field_def` V1 基线（含 §2.3 的 8 类实体 INSERT，`tableColumn` 默认不进检索面）+ `MetadataTypeRegistry`（类型/字段解析与失效缓存）+ `MetadataSlotRegistry`（7 槽集中登记，冲突即报错不静默复用，§2.4.1） | P0 | **"加一类元数据/一个字段不改表"的机制前提** | backlog |
| 129 | `GET /api/v1/metadata/types` 类型自省接口 + **两处级联保存校验**：`type_def` 侧——`kind=ENTITY` 必填 `key_prefix`/`fqn_pattern`/`lineage_asset_type`，且 `lineage_asset_type` **必须是 `LineageAssetType.values()` 里的常量名**（否则别人 `valueOf` 读行时抛异常，后果 1）、`key_prefix` 与该类型登记的键格式自洽；`field_def` 侧——`searchable=1` 而无槽位 → 49xxx 直接拒、`base_type=ENTITY_REFERENCE` 校验 `entity_type_ref` 存在 | P0 | 前端渲染与检索配置的**唯一来源**；错配置进不了库 | backlog |
| 114 | 采集核心：`MetadataHarvestService`（游标 ≤500）+ `content_hash` 规范化与指纹常量类 + NEW/CHANGED/UNCHANGED 判定 + upsert | P1 | 一轮采集落库 | backlog |
| 115 | GONE 与熔断：per-run FQN 集合 + `deleteStale` 六道安全 + 坍塌比例熔断 + 连续两轮 + 软删 + lineage 撤销 | P1 | 空集/超时绝不清库 | backlog |
| 116 | 触发：Bridge + Handler（上下文恢复）+ `POST …/run` 手工 + `collect_run` 读写 + 单测（含"调度线程无请求头"用例） | P1 | 定时与手动双通道 | backlog |
| 130 | **写时登记主通道（§3.2b/§3.2c）**：`MetadataRegistrationApi.register/unregister`（在 `metadata/api`）+ 与采集**共用**同一 upsert / GONE 熔断路径 + **post-commit 约定**（登记失败绝不向上拖垮源域事务）+ `yak_md_register_retry` outbox + `@Scheduled(fixedDelayString)` 重试 worker（`due→claim→complete/fail` 退避，照 `DevelopmentLineageWorker`）+ **保序**（`sourceUpdatedAt` 旧于现值则只刷在场时间、不改内容） | P1 | 内部实体**改完即可搜**，且目录故障不影响业务保存 | backlog |
| 131 | 四个源域挂钩：modeling（`ModelingLineageController` 同侧的保存/发布点）/ semantic 标准字段 / semantic 业务域 / metric。每个挂钩 ≤30 行、只读源域 service + **`sourceHash` 算法留在源域**（push 与对账共用同一函数，§3.2b 硬约束 2）。**跨模块协商项，不得反向依赖 metadata 内部包** | P1 | 投影注册接上写路径 | backlog |
| 135 | **投影对账（§3.2b 副通道）**：`EntityProvider` 四个批量实现（游标 ≤500）+ 走 `collect_job`/`collect_run` 的 cron 任务（`provider_type='REGISTERED'`）+ 补漏历史实体与 DEAD 行 + 刷 `source_hash` + GONE 判定复用 §3.4 熔断 | P1 | push 漏了能自己长回来；220 条存量实体首次进目录的通道 | backlog |
| 117 | 搜索：`MetadataSearchBackend` 接缝 + MySQL 实现 + OM 参数子集 + **跨类型（`type_name` 作 facet、一次查询、聚合按类型分桶）** + BOOLEAN MODE 转义 + `search_after` 游标 + §4.5 元模型驱动的扩展字段可搜性 | P2 | 中文可搜、跨类型真统一 | backlog |
| 118 | 详情聚合：实体详情（按 `typeName` 出不同面板；物理侧带列/历史/标签/血缘，投影侧**实时**调源域）+ **存储量读 lifecycle** + 分区容错（单块失败不整体 500） | P2 | 一屏看全 | backlog |
| 119 | `MetadataQueryApi` + lineage 物理节点登记打通（消费方 #4）+ **共表看门狗断言**：按 `asset_key` 分组时 `gone_at IS NULL` 的行数 ≤ 1（唯一键抓不到 §2.3 后果 5 的 `project_id` 覆写——行被搬进 `project_scope_id=0` 全局桶后与原行并存，只能靠这条查询发现重复目录行） | P3 | 血缘有物理层，且重复行可被自动发现 | backlog |
| 120 | **modeling 符合性对账**（§5.2，消费方 #1，复用 `StandardFieldMatcher`）+ 差异矩阵 API + 发布前校验挂钩 | P3 | 标准落地可校验 | backlog |
| 121 | `AssetSourceType.TABLE` + `MetadataTableAssetProvider` + quality 选择器切数据源（含实时回落） | P3 | 元数据被三方消费 | backlog |
| 122 | 前端·采集任务页（作用域选择器 / cron / dry-run 预览 / 立即采集 / 运行历史） | P4 | 能选择就不填 | backlog |
| 123 | 前端·统一搜索与浏览（类型切换 = facet 而非多入口）+ 实体详情 + 变更历史时间线 | P4 | 看得见 | backlog |
| 132 | 前端·**目录/详情/筛选项由 `/types` 驱动渲染**（列清单、表单、facet 项均不写死；新类型接入后不改前端代码即出现在检索面） | P4 | 可扩展性在 UI 侧兑现 | backlog |
| 124 | `yak_md_label` + 溯源打标 + 继承重算（幂等全量） | P5 | 标签可解释 | backlog |
| 125 | 认证带 `expires_at` + 过期扫描 + `yak_md_task`（含 §6.2 `open_marker`+CHECK 三条用例）+ 禁自审 | P5 | 认证会过期 | backlog |
| 126 | 概览驾驶舱（覆盖率/新鲜度/变更趋势，**且按类型分面**，≤8 次查询预算） | P5 | 治理度量 | backlog |
| 127 | `PROJECT_REQUEST_RULES` ⇄ 后端 `@ProjectScope` 自动对齐守卫（仿 `navigationMenuContract.test.ts` 扫 controller 源码），根治 T1 类缺陷 | P5 | 同类坑不再复发 | backlog |

```
L0  110 ▶ 111 ▶ 112 ▶ 133                  骨架/菜单/自持表/共表目录列
L1  128 ▶ 129                              元模型（依赖 112）     ‖  113   lineage 侧改造（与 128/129 无关，但排在 133 之后）
L1' 134                                    共键改造（下沉生成器 + 枚举加值）**唯一需跨模块协商的一张，卡住 L2 全部**
L2  114 ▶ 115 ▶ 116                        采集（依赖 128 + 134）  ‖  130 ▶ 131  写时登记（依赖 128 + 134 + 114/115 的机制）
                                             ‖  135  对账副通道（依赖 130 + 115/116）
L3  117 ▶ 118                              跨类型检索 / 详情（依赖 L2）
L4  119 ▶ 120 ▶ 121                        被三方消费（依赖 L3 + 113）
L5  122 ▶ 123 ▶ 132                        前端（依赖 L3/L1 的契约，可并行先行）
L6  124 ▶ 125 ▶ 126                        治理（依赖 L5 的挂载点）
L7  127                                    守卫，任意时刻可做
```
**128/129 必须在 114 之前**：没有类型注册表与槽位登记，`yak_metadata_asset` 的
`type_id`/`md_attributes`/提槽列就没有解释规则，采集上来的是无法渲染也查不动的裸 JSON。

113 与 114~116 **可并行**（113 只碰 lineage，114 起才依赖它）；
130/131 **复用** 114/115 建好的同一套 upsert 与 GONE 熔断路径（两条入口只换触发者，不换机制），
故紧随 115 开工，不必等 116；135 需要 116 的调度通道（`collect_job`/`collect_run`），排在 M1b 最后；
122/123/132 只依赖 117/118/129 的 API 契约，可在后端完成前按契约先行开发。

---

## 8. 验收标准

**P0（110~113, 133, 134）**
- `./mvnw -q -o -pl yak-ops-business/yak-ops-business-metadata test` 通过；
  `./mvnw -q -o -pl yak-ops-common install -DskipTests` 通过（PO/枚举在此）。
- 用户重启 IntelliJ 后，菜单出现"元数据"及其 4 个子页，未授权账号看不到。
- `yak_metadata_asset` 行数**不因采集而改变**（P0 不采集）。
- 全库无新增跨模块内部包 import（含 113 完成后的 lineage）。
- **（B 案专属）** 目录列 `ALTER` 以**新文件**落 `db/migration/yak-lineage`，
  `V1__baseline_lineage.sql` 在 `git diff` 里**零改动**（改了就是破坏既有环境，直接判不通过）。
- **（B 案专属）** 跑一次 lineage 既有关系登记链路后，被触及的目录行
  `md_attributes` / `entity_status` / `fqn_hash` / `project_id` **均未被清空或覆写**，
  且**按 `asset_key` 分组时"在场行"（`gone_at IS NULL`）数量 ≤ 1**——
  这一条专防后果 5：`project_id` 被覆写成 NULL 会把行搬进 `project_scope_id=0` 的全局桶，
  与真实项目行并存，而**没有任何唯一键会拦**（§2.3 后果 4/5、T19）。lineage 既有测试全绿。

**P0b 元模型（128~129）——本次需求修正的核心验收，必须实测而非只看建表成功**
- `GET /api/v1/metadata/types` 返回 8 类一期实体 + 各自字段定义，前端未写死任何类型名即可渲染筛选项。
- **插一行 `yak_md_field_def`（`searchable=1` + 指定空闲槽位）后，不重启、不改代码，
  该字段立刻可被 `queryFilter=attr.x=1` 过滤、可进 `q` 命中**——这是"加字段免费"的硬证明。
  做不到就是元模型白建了，必须在评审时判该票不通过。
- `searchable=1` 但未分配槽位 → 保存被拒（49xxx），不落库。
- `MetadataSlotRegistry`：两个类型抢同一槽 → 第二次分配**报错**，不静默复用（§2.4.1）。
- 加一个新 `yak_md_type_def` 行（如 `dataset`）后，`/types` 与搜索的 facet 分桶自动含它，**零 DDL**。

**P1 采集（114~116）**
- 对 trade_db 手工触发一次采集：`type_name='table'`（join 元模型取 `type_id`）且未 GONE 的行数
  == 该库 `listTables` 结果数（用只读脚本比对，不靠人眼）；`type_name='tableColumn'` 行数
  == 各表 `column_count` 之和。**不要用 `asset_type` 计数**——物理表与逻辑模型同为 `TABLE`（§2.3 后果 1）。
- **连续两次采集，第二次 `cnt_changed == 0 && cnt_new == 0`**
  （幂等与指纹正确性的硬指标；不成立说明指纹进了易变字段）。
- 拔掉数据源密码触发采集 → run 状态 `FAILED`，**实体行一行未删**。
- 构造"仅返回 3 张表"的假中断 → 触发坍塌熔断，run = `SUSPECT`，`cnt_gone == 0`。
- `seenFqns` 为空 → 零 GONE（单测必测，对齐蒸馏 §3.4）。
- 无请求头的调度线程能正确写入带 `project_id` 的行（回归 §3.7 陷阱）。

**P1 写时登记（130~131）**
- modeling 现有模型 / semantic 标准字段与业务域 / metric 指标，**四类各至少 1 条**经源域保存动作进目录，
  `provider_type='REGISTERED'`，`source_hash` 非空。
- **改完即可搜**：在 modeling 改一个模型的 `displayName` 并保存后，**不触发任何采集/对账任务**，
  直接搜索新名即命中，且 `update_time` 前进。
  做不到就是 push 没接通（定时拉取会替它掩盖，故这一条必须显式断言"未跑任务"）。
- **目录故障不拖垮业务**：让 `MetadataRegistrationApi` 抛异常（或直接停掉 metadata 的写路径），
  源域保存**仍然成功返回**，且 `yak_md_register_retry` 恰好多出一行 `PENDING`；
  恢复后重试 worker 在 `poll-delay` 的若干轮内把它落成 `DONE`，目录行与源域一致。
  这是 §3.2c 三个必须里"post-commit"的唯一硬证明。
- **保序**：对同一实体并发提交两次编辑（晚的 `sourceUpdatedAt` 更大），最终目录里的内容是**后者**；
  人为让 worker 先消费新变更再消费旧变更，断言旧的那条**只刷在场时间、不改内容**（§3.2c 必须 3）。
- **重试有上界**：连续失败的行按退避增长 `next_attempt_time`，超过阈值转 `DEAD`，
  且 `DEAD` 有可观测出口（P1 阶段先用一条看门狗查询断言计数为 0；§7 ticket 126 把它做成概览指标位）。
- 登记写入的 `md_attributes` 里**不含源域业务内容**（列定义/公式仍由详情页实时读源域，§1.3）。

**P1 对账（135）**
- push 之前就已存在的 220 条存量实体，**跑一轮对账后全部进目录**（这是它们唯一的入场通道）。
- 人工删掉一条目录行 → 一轮对账把它补回，且**不产生第二行**（按 `asset_key` 分组在场行仍 ≤ 1）。
- 让某个 `EntityProvider` 抛异常 → 该轮该类实体**零 GONE**（熔断路径对对账同等生效，§3.2b 硬约束 3）。
- 对账刷新 `source_hash` 时，若与 push 写入的算法产出不同 → **必须能被发现**（同一实体的两次上送指纹可比对；不同即判不通过，§3.2b 硬约束 2）。
- 详情页读投影实体的列清单时**走源域实时接口**：改源域数据后刷新即变，无需等一轮对账。

**P2（117~118）**
- **跨类型统一检索是"一次查询"**：物理表 + 物理列 + 模型 + 标准字段 + 业务域同时在场时，
  `q=毛利` 的一次调用即返回混合结果，且**服务端执行的 SQL 条数不随类型数增长**
  （用拦截器计数：5 类实体在场时搜索 SQL ≤ 分页 + 聚合各 1 条）。
  若实现退化成"每类一次查询再合并"，本票判不通过——那正是 §1.1 否掉的做法。
- 结果的类型分布来自**同一次聚合**（`GROUP BY type_id`），前端类型切换只改 `index` 参数、不换接口。
  （按 `asset_type` 分桶即判不通过：那会把 `table` 与 `dataModel` 显示成同一个类型。）
- `getHierarchy=1` 时列结果带出其所属表，且是**一次自连接**，不是逐行回查（N+1 守护）。
- 中文注释关键词搜索命中，且响应 `explain` 标明实际走的路径（ngram 还是 LIKE 降级）。
- `queryFilter` 改变时聚合计数随之变；`postFilter` 改变时聚合计数**不变**（两者语义区分必须实测）。
- 表详情页存储量 == `yak_lc_storage_snapshot` 最新值，并显示快照日期；
  **本模块无 `SHOW DATA`、无 `information_schema` 字节量查询**（grep 守护）。

**P3（119~121）**
- 一张物理表在 `yak_metadata_asset` 有且仅有一个节点，`asset_key` 与 §2.3/§5.4 的生成器产出**逐字相同**
  （契约测试锁定）。B 案下"目录行即图节点、无孤儿"是表结构本身的性质，不需要断言；
  要断言的是**幂等与身份**：同一 `asset_key` 二次登记**不产生新行**，
  且它在 `gone_at IS NULL` 下的行数**始终 ≤ 1**。
  注意 `fqn_hash` 只是 `asset_key` 的派生值、**不单独立键**（§2.3 后果 4、§2.4.3），
  所以"按 `fqn_hash` 查不到第二行"是上一条的**推论**，不是另一把锁；别在测试里把它当独立断言写。
  归属被改（`project_id` → NULL 落进全局桶）由 §7 ticket 119 的看门狗查询兜（T19）。
- 给一个模型加一个物理库中不存在的列 → 符合性报 `MISSING_IN_PHYSICAL`，发布被阻断。
- 物理库新增一列 → 报 `MISSING_IN_MODEL`，不阻断，且生成一条 `yak_md_task`。
- 模糊（fuzzy）匹配出的标准字段**只能作为建议**出现，不得自动落库为关联（§5.2）。
- quality 表选择器首屏不打数据源（读元数据）；元数据未覆盖时回落实时 catalog 且 UI 标注来源。

**P4（122/123/132）**
- 采集任务新建默认关 + dry-run 预览（§0.13）；未 dry-run 不允许启用。
- **前端零类型常量**：新插一行 `yak_md_type_def` 后刷新页面，该类型自动出现在类型筛选与详情面板里，
  **不改任何 `.tsx`**（grep 守护：页面里不得出现 `'table' | 'dataModel'` 这类硬编码类型联合）。
- 搜索结果列表按类型混排展示，每行用 `type_def.display_name / icon / color` 渲染，不是"表专区 + 模型专区"两块。

**P5（124~127）**
- 自动标签一律 `state='SUGGESTED'`，人工确认才 `CONFIRMED`；概览分别统计两者。
- **对一条 `tableColumn` 实体打标 / 建待办，与对一张表走的是同一套代码**
  （`asset_id` 同构，§6.1；断言不存在 `if (targetType)` 分支）。
- `yak_md_task` 三条库层用例全绿：重复开放待办撞唯一键、漏刷 `open_marker` 被 CHECK 拒、
  办结后同目标可再建（§6.2 实测口径）。
- 认证过期后详情页显式提示"认证已过期"，且**不触发** CHANGED（§3.3）。
- 提单人自审被拒（403 语义 + 单测）。

---

## 9. 陷阱清单（本仓库已验证的真实坑，逐条必查）

| # | 陷阱 | 症状 | 防线 |
|---|---|---|---|
| **T1** | **`/api/v1/metadata` 未登记 `PROJECT_REQUEST_RULES`** | 整个新模块所有接口 999 | `src/utils/security/projectContext.ts` 加 `{ prefix: '/api/v1/metadata', mode: 'PROJECT_REQUIRED' }`。**ticket 111 强制项** |
| **T1b** | ✅ **已修复**（§11.1.3）：asset 用 `@ProjectScope(PROJECT_REQUIRED)`（`AssetController.java:44` 等 5 处）但 `/api/v1/assets` 曾不在 `PROJECT_REQUEST_RULES` → 头不带 → 全模块 999 | 同上 | 规则与断言已补。**残留风险是"两份清单靠人记"无自动守卫**，见 ticket 127 |
| **T2** | 返回裸 `PageData` | 整模块 `*/page` 999，读写正常 | 一律 `Result.success(PagingData.from(page))`；`MetadataLayeringConventionTest` 守（§10） |
| **T3** | `@RestControllerAdvice(basePackages=…)` 与 controller 实际包不符 | 错误码 49xxx 被兜成 999，排查方向全错 | ticket 110 起就在真机打一个必失败请求验证 49001 能透传 |
| **T4** | 权限码字符串在 SQL / `securityMenuCodes.ts` / `navigation.ts` 三处不一致 | 菜单可见但接口 403，或反之 | 三处逐字节比对；`navigationMenuContract.test.ts` 是现成守卫 |
| **T5** | **改动已应用的迁移** | `yakSecurityFlyway … Migration checksum mismatch`，应用整体启不来 | 永不编辑已应用文件；只加新版本号。恢复：`flyway repair` 或改 `flyway_schema_history.checksum`；**不要删 history 行**（菜单已插过会重复） |
| **T6** | 跨模块 import 内部包 | 编译过、架构腐化 | 参照 `QualityLayeringConventionTest` 写 `MetadataLayeringConventionTest`；`ModelTtlQueryApi`/`ModelTtlQueryApiImpl` 是正例，`StandardFieldMatcher:3` 是反例 |
| **T7** | `.m2` 里的 `yak-ops-common` 陈旧 | "程序包 io.yak.ops.common.bean.po.metadata 不存在" | 先 `./mvnw -q -o -pl yak-ops-common install -DskipTests`，再建模块 |
| **T8** | `-am` 连带跑兄弟模块 | surefire "No tests matching pattern" | `-o -pl <module> test` 不带 `-am`；必须带时加 `-Dsurefire.failIfNoSpecifiedTests=false` |
| **T9** | 离线全量构建 | `yak-ops-boot compile` 缺厂商 JDBC 驱动 jar（yashandb/highgo/xugu/duckdb…） | boot 运行归用户 IntelliJ；我们只做单模块隔离验证，**不承诺 boot 可编译** |
| **T10** | 调度线程无请求头 | 采集全部 999 或 project_id 丢失 | §3.7 `projectScope.call(new ProjectContext(projectId, null), …)` + 单测（ticket 116） |
| **T11** | 后端由用户 IntelliJ 启动 | Java 新类与新迁移不生效 → "明明改了"却验不到 | 任何后端契约变更都显式标注"待用户重启后验证"，**不得声称已验证** |
| **T12** | Doris/Paimon 无本地实例，数仓五库 0 表 | 数仓侧路径无法端到端验 | 验收走九个业务库；Doris 特性路径标"环境受限"，禁止写成 verified |
| **T13** | 采集器直插别模块表 | 一行脏数据毒死对方接口（本周实例） | §0.4；code review 红线 + grep `yak_ops_data_source`/`yak_lc_` 在本模块内不得出现在写路径 |
| **T14** | 前端页面直连 DTO | OM 的 13.9:1 教训（蒸馏 §4.6） | 读接口收敛在 `src/services` / `rest` 层，页面不 import 生成式类型 |
| **T15** | `information_schema` 在 Doris 上 `DATA_LENGTH` 恒 0 | 采集"成功"却产不出数据（静默空快照） | 统计层沿用 `statementsFor()` 的"候选语句按序尝试"；本模块只读 lifecycle 快照，进一步降风险 |
| **T16** | lifecycle 快照唯一键不含 `database_name`（`uk_yak_lc_snapshot (project_id, snapshot_date, datasource_id, table_name)`） | 同数据源多库时表名互相覆盖，元数据详情页读到别库的量 | 读接口必须**同时按 `database_name` 过滤**并在 SQL 层断言；发现覆盖即记为 lifecycle 侧缺陷上报，不在元数据模块偷偷补偿 |
| **T17** | **用 `UNIQUE (…, nullable列)` 表达"只允许一条开放行"** | 约束方向正好写反：NULL 与 NULL 在 MySQL 唯一索引里彼此相异，重复开放行照插不误，反倒把已办结的历史行互相挤掉 | 见 §6.2 `open_marker` + `CHECK`。**注意生成列方案在本仓库不可用**：`3109 Generated column cannot refer to auto-increment column`（实测）。同类形状全模块禁用，code review 见到 `UNIQUE (… , *_at)` 立即拦 |
| **T18** | 把 `attributes` 当"什么都能塞"的口袋，字段不进 `field_def` 登记 | 目录半年后变成既查不动、也说不清字段来源的黑洞，等于把 §2.2 的元模型架空 | `attr.*` 筛选与提槽**只认 `field_def`**（§4.2/§4.5）；provider 写入未登记字段直接 49xxx；`MetadataLayeringConventionTest` 断言不存在绕过 registry 直写 `attributes` 的路径 |
| **T19** | **（B 案专属）共表：lineage 的 upsert 把 `project_id` / `properties` 当作自己私有列覆写**（`LineageWriteMapper.xml:26` 单条、`:59` 批量各有一行 `project_id = VALUES(project_id)`，`:31`/`:64` 各有一行 `properties = VALUES(properties)`） | 目录行的 `project_id` 被写成 NULL → 落进 `project_scope_id = 0` 的**全局桶**，与真实项目里同 `asset_key` 的行**并存**（两行各自满足那把唯一键）→ **没有任何键会拦，下一轮采集静默产生第二份真相**；`md_attributes` 不受影响，但**读侧若误用 `properties` 会拿到被对方整包覆写的袋** | 目录读侧永不使用 `properties`（§2.3 后果 2）；两侧各自只 own 自己的列并写进两份 `ARCHITECTURE.md`（ticket 133）；**按 `asset_key` 分组的在场行数 ≤ 1** 看门狗查询（ticket 119）——这是唯一能发现本条的手段，DDL 发现不了。**133 回归必覆盖的两条既有语句**：`selectAssetForUpdate`（`:82-94`）在 `projectId == null` 时**不加 project 谓词**、按 `asset_key LIMIT 1` 命中任意项目的行；`claimLegacyAssetProject`（`:5-14`）把 NULL-project 行搬进某个项目 —— B 案后若该项目已存在同 `asset_key` 行，这条 UPDATE 会撞 lineage 自己的 `uk_yak_metadata_asset_project_key` 报 **1062**（不是新键，是既有键），要它**以可识别的错误冒出来**而不是让 lineage 侧吐未知 500 |
| **T20** | **写时登记（push）的错误落地形状**：把 `register()` 放进源域业务事务内 / 让它的事务外异常向上抛 / post-commit 但不落持久队列 | 四种各自不同：① **事务内** → 目录侧一次抖动把用户的"保存模型"变成回滚，失败原因（"搜索里还没有"）与业务动作毫无关系，绝不可接受；② 事务内且登记已成功、业务后续步骤回滚 → 目录里多出一条**从未存在过的实体**，且下一轮对账不会删它（源域游标里根本没有它的踪迹）；③ 事务外但异常上抛 → 数据其实存上了，用户看到 500，再点一次保存又产生新版本；④ post-commit 但只在内存里重试 → 进程重启即永久丢一条变更，而"有对账兜底"会变成不修这条链路的借口 | **三个必须**（§3.2c）：登记只发生在事务提交之后；失败落 `yak_md_register_retry`（outbox）由 `@Scheduled(fixedDelayString)` worker 消费；同实体按 `sourceUpdatedAt` 保序。源域挂钩里 `catch` 到登记异常**只写队列、不再抛**（§8 P1 写时登记第 3 条为唯一硬证明，§10 测试 14 锁死）。对账是副通道，不是本条的对策 |

---

## 10. 测试要求

**必写（非可选）**
1. `MetadataLayeringConventionTest` — 本模块**没有**同类测试（现存 7 个
   `*LayeringConventionTest` 中无 metadata）。至少守：repository 不暴露 HTTP/VO/Mapper 类型、
   分页返回 `PageData`、无跨模块内部包 import、Controller 返回 `PagingData`、
   **无绕过 `MetadataTypeRegistry` 直写 `attributes` 的路径**（T18）。
2. **指纹稳定性测试** — 三条硬用例：注释空白差异 → hash 不变；`nullable` 变化 → hash 变；
   `expires_at` 变化 → hash 不变。
3. **采集安全测试** — 空 seenFqns / 中断致表数坍塌 / 数据源不可达三例，
   断言"零删除"与 `SUSPECT`/`FAILED` 状态。
4. **调度上下文测试** — 无请求头执行 handler，断言 PO 的 `project_id` 正确。
5. **契约测试：asset_key 同源 + 共表不互踩**（对齐 asset dev-plan §4 对 ticket 94 的同款要求）—
   ① 断言元数据产出的 `asset_key` 逐字等于 lineage 键生成器输出；
   ② **同一 `asset_key` 重复登记/重复采集不产生第二行**（幂等，§2.3 后果 5）；
   ③ 对一条已有目录行执行 lineage 的 upsert 后，`md_attributes`/`entity_status`/`fqn_hash`/`project_id` 不变（T19）；
   ④ **现网 234 行认领不增殖**（有真实数据可比，不必等新采集）：跑完注册后
   `asset_type='TABLE' AND source_type='MODELING'` 仍 11 行、`semantic:field:%` 仍 8 行、
   `metric:%` 仍 4 行，只是 `type_id` 从 NULL 变非空；任一数字翻倍即本条失败（§2.3 后果 6）。
6. **搜索转义测试** — BOOLEAN MODE 特殊字符全枚举。
7. **元模型驱动检索测试**（本次需求修正后新增，是"可扩展"的唯一凭据）—
   运行时插一行 `field_def`（含 `searchable=1` + 槽位），**不重启、不改代码**，
   断言新字段随即能进 `q` 命中并进 `queryFilter`；反向断言未登记字段被 49xxx 拒。
8. **槽位登记测试** — `MetadataSlotRegistry` 两类型抢同槽 → 报错；
   且 `searchable=1` 无槽在保存时即拒（§4.5 三条防线各一条用例）。
9. **注册通道安全测试** — provider 抛异常/返回空页 → 该类实体零 GONE；
   断言注册与采集走的是**同一个** upsert 与熔断入口（防止后来者复制一份判定逻辑）。
10. **投影不越界测试** — 断言 `dataModel`/`standardField` 等投影实体的 `attributes`
    不含源域业务内容（列清单/公式），锁死 §1.3 那条线。
11. **库层约束测试**（对真实 MySQL，非 H2/mock）— §6.2 三条：
    重复开放待办 → 1062；漏刷 `open_marker` → 3819；办结后同目标可再建 → OK。
    这组已在 `yak_security`（MySQL 8.0.46）用临时表预演通过，落地时转为正式用例。
12. 前端：`npx tsc --noEmit` **不得高于 199 基线**、被改文件无新增错误；
    `navigationMenuContract.test.ts` 通过；**类型常量 grep 守护**（§8 P4）。
13. **（B 案专属）枚举映射 + 键派生守护** — 两条，都是"数据库拦不住、只能靠 CI"的那类：
    ① 遍历 `yak_md_type_def` 每个 `kind=ENTITY` 行，断言其 `lineage_asset_type`
    **是 `LineageAssetType.values()` 里的常量名**。价值在 lineage 将来重命名常量时
    **CI 先红**，而不是线上 `valueOf` 抛 `IllegalArgumentException` 炸别人的血缘查询（§2.3 后果 1）。
    ② grep 守护：`md5(` + `assetKey` 的组合**全库只允许出现在 `MetadataKeyCodec` 一处**。
    这是"不新增唯一键"换来的那条代码层不变量（§2.4.3、§3.2b 硬约束 4）——
    派生点一旦分叉，同一实体就会裂成两个节点且**没有任何 DDL 会报错**。
14. **写时登记三必须测试**（§3.2c / T20，对真实 MySQL，同测试 11 口径不 mock）—
    ① **post-commit**：把 `MetadataRegistrationApi` 的落库桩成抛异常，断言源域事务**已提交**
      （回查源域行在）且 `yak_md_register_retry` 恰好多一行 `PENDING`，挂钩调用方**拿不到异常**；
    ② **退避与上界**：连续失败时 `next_attempt_time` 按 `Math.min(3600, 1L << Math.min(12, attempts))`
      增长（与 `DevelopmentLineageWorker` 同式），超阈值转 `DEAD`，且该 `DEAD` 行会被 ticket 135 的对账捞回；
    ③ **保序**：人为让 worker 先消费新变更、再消费同一实体的旧变更，断言目录内容仍为新值、
      旧的那条**只刷在场时间不改内容**；
    ④ **并发合并**：同一 `(project, type_name, asset_key, source_updated_at)` 并发写两次 →
      `uk_yak_md_retry_change` 撞 1062，且代码把它 catch 成"已排队"而不是失败上抛
      （**注意键在"变更"上、不在 `status` 上**：键含 status 会让一条实体完成后再也无法重新登记，§3.2c）。

**离线执行**：`./mvnw -q -o -pl yak-ops-business/yak-ops-business-metadata test`。

---

## 11. 决策与待议

### 11.1 已定（2026-09-19 评审确认）

1. **一期采集粒度到列级**。列量约为表量 12.6 倍（§1.4 实测：373 表 / 4684 列，量级无压力），
   换来 §5.2 符合性对账一期即可落地——砍到表级就没有本方案 ROI 最高的那一节。
   列的增删改与表同级走 `yak_md_change`（初稿为列单立的 `yak_md_column_change` 已随统一实体表取消：
   列本身就是一等实体，不需要第二张历史表）。
2. **元数据为独立一级菜单**（V2033 + `/api/v1/metadata` + `data-metadata:*` 权限码）。
   理由：采集任务/运行历史是运维心智，与资产台账的目录心智不同源；
   并入会让 asset 承接采集调度，违背其"管目录不管内容"的既有定位。
3. **asset 的 `PROJECT_REQUEST_RULES` 缺口已当场修复**（原 T1b）：
   `yak-ops-ui/src/utils/security/projectContext.ts` 补 `{ prefix: '/api/v1/assets', mode: 'PROJECT_REQUIRED' }`，
   `projectContext.test.ts` 加 3 条断言（含 `/api/v1/assetsx` 不被误命中的反向用例）。
   实测：纯函数转译执行 8 例全 PASS；`npx tsc --noEmit` 保持 199 基线、被改文件零错误。
   **资产侧 5 个 controller 全部在 `/api/v1/assets*` 且全部 `PROJECT_REQUIRED`，
   一条前缀规则即可全覆盖，无 `LEGACY_GLOBAL` 端点被过度收敛的风险。**
4. **范围修正（2026-09-19 二次评审，已定）**：采集对象不止外部数据源，
   内部模型/数据标准/业务域/表→字段层级**全部作为可扩展实体统一登记、统一检索**。
   这条**推翻了初稿的两处判断**，且推翻得对：
   - 初稿"❌ 不采纳 OM 的文档存储"→ 混淆了 **schema-first 代码生成**（仍判 ❌）
     与 **类型即数据的元模型**（改为 ✅ 采纳，§2.2）。因为反对前者顺手否掉了后者，
     结果方案只剩物理表可采，"统一检索"是假的。
   - 初稿"每种元数据一张归一化表"→ 与统一检索直接矛盾（§1.1），已废弃。
   > 留这段是为了防止后来者以为统一实体表是原设计的一部分，也是为了记下教训：
   > **参考系统里"存储形状"与"代码生成"是两件事，评价时必须拆开。**
5. **统一实体表 = 就地扩展 lineage 的 `yak_metadata_asset`（B 案，2026-09-19 确认；A 案作废）**。
   不新建 `yak_md_entity`。买到的：§0.2"不建第二套真相"自此**由表结构本身保证**而非靠同步器维持，
   统一检索是真的一次查询（§4.1），平台不再出现第三张目录形状的表，
   也不必再写第四个 `*LineageSynchronizer`（analysis/dashboard/dataset 已各一个）。
   代价：这张表**由 lineage 与 metadata 共管**——列 steward 清单与写入权要写进两份 `ARCHITECTURE.md`，
   §2.3 因此多出七条专属后果（含最阴的第五条：`project_id` 被覆写 → 行落进 `project_scope_id=0`
   全局桶 → 与原行并存、**没有任何键会拦**，只能靠 ticket 119 的看门狗查询发现），
   并单独立 ticket 133 承载 ALTER 与 lineage 回归；
   还换走了一样 A 案免费提供的东西——**对称性**：全表唯一的 `uk (project_scope_id, asset_key)`
   是 lineage 的，我们不给 `fqn_hash` 另立第二把键（后果 4、§2.4.3），
   于是"键派生只有一处函数"从 DDL 约束降级成了**代码层不变量 + CI 守护**（§10 测试 13）；
   外加两处不由元数据单方面提交的跨模块 Java 改动（ticket 134）
   和一笔命名债（表名叫 metadata_asset、语义却是"统一实体目录"，§2.3 末已如实记下）。
   判据留档在此，**下次有人提"要不要拆回独立表"时先回答 §2.5 的 A/B 表**：
   拆回去只买回"列所有权干净"这一样，而它可以用 steward 契约免费得到。
6. **内部实体进目录的时机 = 写时登记（push）为主，定时拉取降级为对账**（2026-09-19 定，§3.2b/§3.2c）。
   问题原本是"内部模型/标准字段/指标该统一定时采集，还是双写到目录 + OpenSearch"。结论取**写时投影**：
   源域写成功后调 `MetadataRegistrationApi.register(...)`，失败落 `yak_md_register_retry` 由 worker 重试。
   理由不是偏好，而是三条证据同向：
   - **OM 也是这么做的**，且做成了三层——post-commit 派发（`PostCommitActionQueue`）、
     失败重试队列 + worker（`SearchIndexRetryQueue`/`SearchIndexRetryWorker`）、全量重建（`ReindexingOrchestrator`），
     外加 `OrderedLaneExecutor` 保同一实体不乱序（蒸馏 §3.5 的"不建重试队列"判断**在此翻案**）。
   - **本仓库已有同形实现**：data-development 的 `yak_dev_lineage_outbox` + `DevelopmentLineageWorker`
     （`due→claim→complete/fail` + 指数退避 + `writeIfLatest` 保序）。等于这不是引入外来模式，是跟上自家约定。
   - **定时拉取的语义代价**：内部实体一天改几十次，用户改完搜不到会变成日常。
     时序问题不是实现细节，所以这条单独记录为决策而不是留给实现。
   **索引侧不因此双写**：目录就是 MySQL 的一张表，写时登记只喂它；OpenSearch/ES 仍按 §4.4 阈值，
   未触发前不引入（也就不存在"双写到 ES"这条链路要设计）。
   代价两条，都记清楚：① 新增一条跨模块耦合（源域 → `metadata/api`），
   红线是**它绝不能让业务事务失败**（§9 T20 的四种错误形状就是这条耦合的边界）；
   ② 多一张 outbox 表和一个 worker，以及 `DEAD` 行需要人看（§8 P1、ticket 126 的指标位）。

### 11.2 待议（需要你拍板的排在前面）

> 原第 6 条"统一实体表落在哪张表（A/B）"**已定，移至 §11.1.5**。

5. **采集默认频率**：现写每日 03:00。指纹机制让廉价重跑的代价几乎为零，
   若要与发布节奏对齐可提到每小时。
   **§11.1.6 之后这条管两个通道**（同一张 `collect_job`，值分开调）：
   物理采集决定"外部库的变化多久进目录"，投影对账决定"push 漏了以后多久长回来"——
   前者可以慢（表结构一天变几次算快），后者决定故障暴露时延，建议更密（每小时甚至每 15 分钟，
   廉价性由 §3.3 的指纹增量保证：无变化时除 `last_collect_at` 外零写入）。
6. **`EntityProvider`（§3.2b）与 asset 的 `AssetProvider` 是否合并为一个 SPI。**
   两者形状刻意同构（`typeName/sourceType()` + `cursorList()` + `refresh(sourceId)`），
   输入同源（都用 lineage `asset_key`），**看起来该合**。
   但职责不同：asset 管"上架与目录归属"（治理动作，需要人工确认才可见），
   metadata 管"存在与可发现"（事实登记，登记到即可搜）。
   合并的净收益是少一套 Registry 与一次 provider 实现；净风险是
   **"没上架但存在"的实体被 asset 的可见性规则挡在搜索之外**，那是治理语义污染事实层。
   **§11.1.6 定下 push 之后，这条的账变了，且两个方向都变**：
   `EntityProvider` 已收窄成**只对账**（补漏 / 刷 `source_hash` / 判 GONE），
   它不再是任何实体的入场路径 → "少实现一次接口"的净收益**变小**（一个批量游标方法而已）；
   同时真正干活的入口换成了 `MetadataRegistrationApi`，而这个形状 `AssetProvider` **根本没有**，
   合并也省不掉它。所以净收益与净风险同时缩水，"该合"的论证比初稿弱。
   倾向：**不合并，但把两个 SPI 的签名对齐到可以共享一个 provider 实现**
   （源域写一个类同时实现两个接口），先落地再观察是否有第三家需要同构 SPI。
   **等 130/131/135 三张票落地后再拍**：届时能看见四个对账实现到底是不是同一份代码，
   而现在争的是形状、不是事实。这条现在影响的只有 ticket 135 的四个批量实现放谁的包里
   （131 的挂钩只 import `metadata/api`，与答案无关）。
7. **熔断阈值 30%**：拍脑袋值。给我们库里"一轮最多消失几张表"才合理？
   **对账侧**可能还要另一个阈值——§11.1.6 之后 push 路径本身不做 GONE 判定
   （GONE 只来自显式 `unregister()` 与对账轮次），所以阈值只在 135 那条 cron 上生效；
   而它的风险来源与物理侧不同（provider 逻辑改错 vs 连接超时），合理值也不同。

### 11.3 评审顺带发现（不属本模块，但记在此处防止丢失）

- **`navigationMenuContract.test.ts` 的 `CATALOG_EXTENSION_MIGRATIONS` 缺 V2028（mdm 审批）
  与 V2032（数据资产）**——菜单注册契约测试对这两个模块不设防。ticket 111 顺带补齐。
- **`yak-ops-ui` 的 jest 在当前环境跑不起来**：`jest.config.ts` 里
  `from '@umijs/max/test'`（无扩展名）在 Node v24 的 ESM 解析下报
  `ERR_MODULE_NOT_FOUND`，提示应为 `@umijs/max/test.js`。
  属仓库级/环境级既有问题，**不由元数据模块引入**，故本 ticket 内不擅动配置；
  验证方式改为把纯函数模块转译后在 node 里直接断言（见 §11.1.3）。
  后续需要一个独立 ticket 统一收敛。
- **`PROJECT_REQUEST_RULES` 与后端 `@ProjectScope` 缺少自动对齐守卫**：
  本次 bug 的根因是"两语言两份清单，靠人记"。
  可行的守卫是仿 `navigationMenuContract.test.ts` 读后端源码的路子，
  扫描 controller 的 `@ProjectScope` + `@RequestMapping` 与规则表比对。
  本方案不实现，但**登记为 ticket 127（backlog，P5）**——否则同样的坑还会再踩。

---

## 12. 相关文档

- [OpenMetadata 蒸馏笔记](./openmetadata-distill.md) — 本方案的判据来源
- `docs/data-asset/requirement.md` §九 — 元数据采集此前的**有意排除**声明（本方案是补这个空位）
- `docs/data-asset/information-map.md` — 复用/禁自建口径的出处
- `docs/architecture/PROJECT_SCOPE.md` — 项目隔离与"无物理外键"
- `docs/INTERACTION_PRINCIPLES.md` — 交互硬约束
- `yak-ops-business/yak-ops-business-lineage/src/main/resources/db/migration/yak-lineage/V1__baseline_lineage.sql` — 图节点真相
