# OpenMetadata 蒸馏笔记（元数据 采集 / 存储 / 查询 / 建模）

> 目的：为 yak-ops 的元数据体系提供一份**可对照的参考实现**。
> 对象：`D:\tianxy\code\OpenMetadata`（下称 OM，2.x 分支快照，本文所有结论均在本地树上核对过）。
> 写法约定：每条结论后附 `路径:行号` 或 `路径` 证据；**证据缺失或路径为推断的一律标注**。
> 判定标记：✅ 直接借鉴 · 🟡 借思想不借实现 · ❌ 明确不采纳。

---

## 0. 一页总览：OM 是什么量级的东西

OM 是**一个 Java 后端 + 一个 React SPA + 一个 Python 采集框架**，全部由
`openmetadata-spec` 下的 **904 个 JSON Schema** 定型（`ARCHITECTURE.md` PART 1）。
12 个 Maven 模块，`openmetadata-service` 主源码 1,777 个 `.java`；UI 4,725 个 ts/tsx；
Python 侧 120+ 连接器（`ARCHITECTURE.md` PART 2 表格）。

这跟 yak-ops 的差距不是"缺功能"，是**缺一个数量级的工程规模**。所以蒸馏的正确姿势不是照搬，
而是回答一个问题：**OM 用某个机制解决了什么痛点，这个痛点在 yak-ops 存在吗，
如果存在，最小版本长什么样。** 下面四节就按这个套路走。

一个必须先记住的结构性事实（决定了后面所有判断）：
**OM 的连接器不在 JVM 里跑。** 采集是 Python 进程，通过 HTTP 把实体 POST 回后端 REST API，
即 OM 的"采集路径 B"与"API 路径 A"是同一条写入口（`ARCHITECTURE.md` Path B：
`sink/metadata_rest.py` → 后端）。yak-ops 反之——`DataSourceCatalog` SPI 是进程内 Java 调用。
**这个差异让 OM 大量机制（跨进程契约、批量 HTTP 重试、断点续传、连接器插件契约）对我们是纯负担。**

---

## 1. 类型系统：Schema-first

### 1.1 它怎么做

904 个 JSON Schema 是唯一真相，四个生成器分别产出三语言模型（`ARCHITECTURE.md` I4）：

| 生成器 | 产物 | 落地位置 |
|---|---|---|
| jsonschema2pojo | Java POJO | `openmetadata-spec/target/…`（构建产物） |
| datamodel-code-generator | Python Pydantic | `ingestion/src/metadata/generated/`（gitignore） |
| quicktype | TypeScript | `openmetadata-ui/…/src/generated/`（**提交进库**） |
| ANTLR | FQN 解析器 | Python + JS 两份 |

`type/` 目录下的枚举定义极简，值得看一眼它的克制程度——比如实体的治理状态就是一枚
7 值字符串枚举（`openmetadata-spec/src/main/resources/json/schema/type/status.json`，
`javaType: org.openmetadata.schema.type.EntityStatus`）：
`Draft / In Review / Approved / Archived / Deprecated / Rejected / Unprocessed`，默认 `Unprocessed`。

### 1.2 判定

**❌ 不采纳 schema-first 代码生成流水线。** 注意这条 ❌ **只针对 codegen**，
不针对 §1.3 的元模型——初稿把两件事混成一件、连带否掉了元模型，是判断错误，已在 §1.3 纠正。

理由三条，都跟 yak-ops 现状硬冲突：
1. yak-ops 是三语言但**后端与前端没有跨语言类型契约需求**——PO→VO→TS interface 手工三段，
   26 个业务模块一直这么活过来的；引入 jsonschema2pojo 等于给每个字段改动加一次 codegen 心智。
2. OM 自己的 `ARCHITECTURE.md` 就把 codegen 写成了需要 **PreToolUse hook 阻止人工编辑生成目录**
   （I3 "Enforces: partially"）的防御性机制——这是它规模才付得起的税。
3. 生成物漂移风险不对称：OM 有 CI 重新生成并 auto-commit（I4），我们没有等价基建。

**🟡 借"枚举即治理语义"这一层。** `EntityStatus` 把"资产处于流程哪一步"编码成
7 值字符串枚举（蒸馏 §1.3），**且 `Draft`/`In Review` 这种含空格的值直接当枚举字面量用**——
说明 OM 把枚举当**对外契约**而非内部常量，改值是数据变更不是改名。
yak-ops 的资产状态机（asset 模块）目前只管上下架，元数据侧应借这枚枚举做**符合性状态**，
详见 plan §治理。

### 1.3 实体模型与元模型（✅ 全方案骨架，初稿漏掉的一节）

这一节是"为什么整个目录可以统一检索"的机制来源。OM 的实体模型有**三层**，
且**三层的扩展成本差两个数量级**——这是评估"可拓展性极强"时唯一重要的区分。

**第一层：所有实体共享一个基座。**
`openmetadata-spec/src/main/resources/json/schema/entity/type.json` 是基座 `Type`，
每个实体 schema 都带 `"javaInterfaces": ["org.openmetadata.schema.EntityInterface"]`。
基座字段（`:30-105`，逐个核对）：
`id / name / fullyQualifiedName / displayName / description / category / nameSpace / schema /
customProperties / version / updatedAt / updatedBy / impersonatedBy / href /
changeDescription / incrementalChangeDescription / domains / dataProducts`。

三个字段的位置值得单独说：
- `domains`（`:98-101`）在**基座**上，不在某个子类上。注释：
  *"Domains the asset belongs to. **When not set, the asset inherits the domain from the parent
  it belongs to.**"* —— 业务域是通用能力，且自带继承语义（§4.3 的 propagation 在这里）。
- `fullyQualifiedName` 与 `name` 分离（`:39-41` "FullyQualifiedName same as `name`"）：
  顶层实体两者相同，嵌套实体（表.列）FQN 才是拼接结果。`name` 的 pattern 是
  `(?U)^[\w]+$`（`:13`），即**原始名永不含分隔符**，FQN 拆分才安全。
- `href` 存在实体里而非由 URL 规则推导——这也是 §3.3 把它列进易变字段的原因。

**第二层：类型本身是数据（元模型）。**
`entity/type.json` 的 description（`:5`）：
*"This schema defines a type as an entity. Types includes property types and entity types.
**Custom types can also be defined by the users to extend the metadata system.**"*

即"类型"不是编译期常量，而是一行 `Type` 记录，`category` 取 `field | entity`（`:19`），
自带 `nameSpace`（默认 `custom`，`:57`）和
`schema`（`:61`，**该类型的 JSON Schema 以字符串形式存在这行数据里**）。
`customProperties`（`:63-69`，注释 *"Only available for entity type"*）挂在 Type 上。

`type/customProperty.json` 定义一条扩展字段：
`required: [name, description, propertyType]`，`propertyType` 是**指向一个 `category=field`
的 Type 的实体引用**（`:44-47`），`customPropertyConfig` 可带约束
（`enumConfig` / `format` / `entityTypes` / `tableConfig`，`:24-39`）。

**第三层：扩展字段的存储与检索各有专门机制。**
- 存储：`entity_extension(id, extension, jsonSchema, json)`，PK `(id, extension)`（`v001:40`）。
  `extension` 是**点分名** `<entityType>.<fieldName>`——实测取值如
  `'pipeline.pipelineStatus'`（`openmetadata-service/…/jdbi3/EntityDataDAOs.java:997`）。
  即稀有大字段**不进主表 json**，按名字进侧表，主表保持窄。
- 检索：**动态字段不会自动可搜。** 需要专门的
  `openmetadata-service/…/search/CustomPropertySearchFields.java`（89 行）把值映射到
  `extension.<propName>` 索引字段，并按**每种资产类型一份搜索配置**
  （`AssetTypeConfiguration.searchFields` → `FieldBoost`）决定哪些扩展字段参与搜索、
  权重多少、是否精确匹配（`:14-18` `EXTENSION_PREFIX = "extension."`、`MATCH_TYPE_EXACT`）。
  另有 `customPropertiesTyped` 归一化视图承载类型化值。

#### ⚠️ 两层扩展的成本差两个数量级（评估"可拓展性"必须分清的）

| 动作 | 成本 | 证据 |
|---|---|---|
| 给已有实体**加一个扩展字段** | **运行时、纯数据、不重启**：建一个 `category=field` 的 Type + 在目标 Type 上加一条 CustomProperty | `entity/type.json:63`、`type/customProperty.json` |
| **加一个新的实体类型** | **要写代码**：新 Java 类 + 新 schema + 启动时注册进 `ENTITY_REPOSITORY_MAP` | `Entity.java:98,458`（`Map<String, EntityRepository>` 由 `registerRepository` 在启动时填充），`:767` 按字符串键取用 |

OM 的 `EntityType` **不是 Java enum**（已 grep 确认，全仓无 `public enum EntityType`），
类型键是字符串、仓储是启动期注册的 map——所以"新类型"不需要改枚举，
但**需要一个 Java 仓储类**。搜索面还要额外加一份 `AssetTypeConfiguration`。

**结论**：OM 的可扩展性是"**加字段免费、加类型要代码**"。
任何说它"完全动态"的描述都不准确。给 yak-ops 定方案时必须照这个形状设计：
**把"加字段免费"做成真免费，把"加类型"的成本压到"写一个 provider 类 + 插一行类型定义"，
并且明确不承诺加类型免代码。**

---


## 2. 存储：文档库落在关系库上

### 2.1 它怎么做

每种实体类型一张表，整行是一个 `json JSON NOT NULL` 列，需要索引/约束的字段用
**STORED 生成列**从 json 里"提"出来。`table_entity` 的完整 DDL
（`bootstrap/sql/migrations/flyway/com.mysql.cj.jdbc.Driver/v001__create_db_connection_info.sql:136`）：

```sql
CREATE TABLE IF NOT EXISTS table_entity (
    id VARCHAR(36) GENERATED ALWAYS AS (json ->> '$.id') STORED NOT NULL,
    fullyQualifiedName VARCHAR(256) GENERATED ALWAYS AS (json ->> '$.fullyQualifiedName') NOT NULL,
    json JSON NOT NULL,
    updatedAt BIGINT UNSIGNED GENERATED ALWAYS AS (json ->> '$.updatedAt') NOT NULL,
    updatedBy VARCHAR(256) GENERATED ALWAYS AS (json ->> '$.updatedBy') NOT NULL,
    deleted BOOLEAN GENERATED ALWAYS AS (json -> '$.deleted'),
    PRIMARY KEY (id),
    UNIQUE (fullyQualifiedName)
);
```

`change_event` 甚至**只有生成列、没有普通列**（同文件 `:379`）——表里物理只有一个 `json` 列，
`eventType/entityType/userName/eventTime` 四个查询维度全是生成列 + 三个索引。
这说明生成列在 OM 不是优化手段，而是它的**建表范式**。

**FQN 索引长度问题用 hash 解决**（同文件 `:240`、`:290`）：MySQL InnoDB 索引上限 3072 字节，
`VARCHAR(1024)` 的 utf8mb4 FQN 建唯一键会超（1024×4=4096）。OM 的手法：
```sql
entity_fqn_hash VARCHAR(768) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,   -- :21
fqnHash varchar(256) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,               -- :240
```
`ascii_bin` 让 768 字符 = 768 字节，正好卡在限制内；`:297` 处 `UNIQUE KEY unique_name (fqnHash)`。

**关系是两张独立的边表**，不是 FK：
- `entity_relationship(fromId, toId, fromEntity, toEntity, relation TINYINT, jsonSchema, json, deleted)`，
  PK `(fromId, toId, relation)` + 三个方向索引（`v001:4`）。`relation` 是**序号**不是字符串。
- `field_relationship(fromFQN, toFQN, fromType, toType, relation, …)`，PK `(fromFQN, toFQN, relation)`
  （`v001:24`）。注意它**用 FQN 不用 id 做键**——注释写明是 `table.columns.column` 这类类型化 FQN。
  即"字段级血缘/join"是独立一层，且键稳定性靠 FQN 而非代理主键。
- `entity_extension(id, extension, jsonSchema, json)`，PK `(id, extension)`（`v001:40`）——
  按实体挂载的**命名扩展文档袋**，OM 拿它存版本历史与自定义属性。

### 2.2 判定

**这里要分两件事判，初稿把它们捆在一起否掉了，是错的。**

**❌ 不采纳"一实体类型一表"（700 张表）。** OM 给每种实体各建一张 `*_entity` 表
（`v001` 里 `dbservice_entity`/`database_entity`/`table_entity`/`dashboard_entity`… 二十余张起）。
新类型 = 新表 = 迁移 + 新 DAO + 新索引，**这是我们要避开的那一半**。

**✅ 采纳"单表 + `json` + STORED 生成列 + extension 侧表"作为目录骨架。**
这才是让**跨类型统一检索**成为可能的结构前提：一次 `MATCH` 扫过全目录，
`entity_type` 只是一个 facet。若每种类型一张表，"统一检索"退化成 N 路联合查询 + 人工合并排序。
统一实体表 + 类型判别列，我们**已经有了**——`yak_metadata_asset.asset_type VARCHAR(32)`
就是这个形状，只是它没承载属性、没建全文索引。

关于"文档存储会诱导出包环"这一节初稿的说法，**更正为不可依赖的推断**：
`ARCHITECTURE.md` 自己把环归因于 *"much of it misplaced value types under `resources/`"*，
不是归因于文档存储。初稿拿一个未经证实的因果去否掉一个结构决策，方法上是错的。
真正该守的边界是**我们的**边界（repository 不暴露 HTTP/VO 类型、分页返回 `PageData`），
与存不存 json 无关。

**✅ 借三件，且这三件都跟"元数据列数会爆"直接相关：**

1. **生成列 + STORED**：可变部分进 `extra JSON`，稳定可查部分提为真实列。这正是
   "宽表不想开 40 列，但又要对某几列建索引/加唯一约束"的正解，且不需要新组件。
2. **`ascii_bin` FQN hash 唯一键**：我们的表 FQN 同样会撞 3072 字节限制（数据源 id + 库 + schema +
   表 四段拼接，中文库名按 utf8mb4 是 3 字节/字）。**必须一开始就上 hash 键**，
   而不是等有 5 万张表再改——改唯一键要重建表。
3. **边表独立、`relation` 用序号**：与我们 lineage 的 `yak_metadata_relation` 完全同构，
   说明我们既有选择是对的（见 §2.3）。

**✅ 借 `entity_extension` 侧表形状**（`v001:40`，PK `(id, extension)`、`extension` 为点分名）：
稀有大字段不进主表 json，按 `<type>.<field>` 存侧表，主表保持窄、扫描便宜。
详见 plan §2.2。

### 2.3 与 yak-ops 既有表的对照（关键：不要建第三套真相）

我们已经有一张边表了。`yak-ops-business-lineage` 的基线
（`.../db/migration/yak-lineage/V1__baseline_lineage.sql`）：

| OM | yak-ops lineage | 差异 |
|---|---|---|
| `table_entity` 等一实体一表 | `yak_metadata_asset`（单表 + `asset_type` 判别列） | 我们更紧凑；OM 更松 |
| `entity_relationship` | `yak_metadata_relation` | 结构几乎一致 |
| `relation TINYINT` 序号 | `relation_type VARCHAR(32)` 字符串 | 我们可读性更好，代价是索引变宽 |
| FQN 生成列 | `asset_key VARCHAR(512)` + `uk (project_scope_id, asset_key)` | 等价思路，我们没用 hash |
| 软删 `deleted BOOLEAN` 生成列 | 无（靠服务层） | 我们缺软删 |

**结论：`yak_metadata_asset` 已经是"图节点注册表"，`project_scope_id GENERATED ALWAYS AS
(COALESCE(project_id,0)) STORED` 甚至已经用了 §2.2 的生成列技巧。**
新元数据模块**不得**再造一张资产表，必须经 lineage 登记。这是 plan 的一条硬约束。

一个真实教训顺带记下：OM 的 `entity_relationship` 长期产孤儿行，以至于专门写了重设计文档
`docs/plans/2026-06-22-bulk-deletion-redesign.md`——开头就是"删一个 service（10 万~100 万后代
表/列）**当前要 2~6 小时**且经常留下孤儿 `entity_relationship` 行"。
我们的 `yak_metadata_asset.parent_asset_id` 与 `yak_metadata_relation` 都没有物理 FK（基线注释
明确写"integrity is enforced by lineage services"），**孤儿风险与 OM 同源**，
所以 §3 的采集必须自带孤儿清理，不能只管插入。

---

## 3. 采集：连接器 + 增量 + 陈旧删除

这一节是 yak-ops 最需要的，也是 OM 花最大力气设计的地方。

### 3.1 方言层：OM 的负担我们从哪里来

OM 每个数据库连接器都要自己实现反射（`ingestion/src/metadata/ingestion/source/database/` 下
90+ 个目录）。看 Doris 这个例子（`.../database/doris/metadata.py:15-55`）：它 import
`pydoris.sqlalchemy`、`MySQLTableDefinitionParser`、`IdentifierPreparer`，自己写
`_parse_type` / `_get_column` / `extract_number` / `extract_child`，
`queries.py` 里另有 `DORIS_GET_SCHEMA_COLUMN_INFO`、`DORIS_SHOW_FULL_COLUMNS`、
`DORIS_TABLE_COMMENTS`、`DORIS_VIEW_DEFINITIONS`、`DORIS_PARTITION_DETAILS`（`SHOW PARTITIONS FROM …`）
五段自带 SQL。

**它为什么这么累**：Python 侧没有 JDBC `DatabaseMetaData` 这个标准抽象，只能靠 SQLAlchemy +
各家 dialect，而 Doris 没有官方 SQLAlchemy dialect，于是借 MySQL 解析器再打补丁。

### 3.2 判定：方言层的适用范围要重新划

**✅ 我们不需要为"结构采集"写方言层。** 证据在 SPI 本身——
`yak-ops-plugins/yak-ops-plugin-datasource/*/src/main/java/io/yak/ops/spi/datasource/DataSourceCatalog.java`：

```java
List<String> listDatabases();
List<String> listSchemas(String database);
List<DataSourceTable> listTables(DataSourceCatalogQuery query);
List<DataSourceColumn> listColumns(DataSourceTablePath tablePath);
```

`DataSourceTable` 给 `database/schema/name/type/remarks`，`DataSourceColumn` 给
`name/typeName/jdbcType/size/scale/nullable/ordinalPosition/primaryKey/remarks`
（均在同包 `metadata/` 下）。**这 14 个字段就是表 + 列的结构元数据全集**，
由各插件走 JDBC 标准接口拿，Doris/MySQL/PG 全都覆盖。OM 需要 90 个方言目录的事，
我们一次 SPI 调用就完了。

**❌ 但 SPI 覆盖不到的部分必须自己开方言口。** `DataSourceCatalog` 里**没有**：行数、存储字节量、
分区、最后 DDL 时间、热点/倾斜。**这些只能按方言写 SQL。** yak-ops 已经付过一次这笔钱并踩过坑：
`yak-ops-business-lifecycle/…/stats/StorageSnapshotService.java` 的 `statementsFor()`
先试 `SHOW DATA FROM \`db\``（Doris 系，因为 `information_schema.TABLES` 在 Doris 上
`DATA_LENGTH` 恒为 0），语法报错再回落到标准 `information_schema` 查询。
这条"候选语句按序尝试、首个可用即采纳 + 库名过 `SAFE_DB` 白名单才拼 SQL"的模式
**就是元数据统计采集的正确形态**，直接沿用。

### 3.3 增量：sourceHash 指纹，且比对在服务端

OM 不让连接器自己决定"要不要写"。连接器给每个实体盖一个指纹，**服务端**批量入口比对后跳过。

- 生成侧：`ingestion/src/metadata/utils/source_hash.py`（`topology_runner.py:56` 引入
  `generate_source_hash`，在 `:440-441` 盖到 yield 出的实体上）。该文件 docstring 直说了稳定性三招：
  **按确定性键排序列表（列优先 `ordinalPosition`，退化到 name）、剔除易变字段、归一化 DDL 空白**。
  剔除清单是两组常量：`VOLATILE_ENTITY_REFERENCE_FIELDS = {"href", "deleted", "inherited"}`、
  `VOLATILE_CERTIFICATION_FIELDS = {"appliedDate", "expiryDate"}`。
- 消费侧：`openmetadata-service/…/jdbi3/EntityRepository.java:12788`（`isSourceHashUnchanged`），
  `:12841` 注释写明这是 fast-path：命中即报 no-change success，与"完整 diff 后什么都没变"结果一致。
  两个必要条件值得抄：**incoming/stored 双方 hash 都非空**；**该 FQN 在批次里只出现一次**
  （重复 FQN 必须走完整 updater，因为每次 occurrence 要跟"新快照"比）。
  `overrideMetadata=true` 时短路失效——人工强制覆盖优先于省算力。

`topology_runner.py:336`/`:420` 也确认了职责切分：*"by comparing sourceHash) is handled
server-side by the bulk endpoint, so the connector no …"*，连接器不再自带状态存储。

**✅ 直接借鉴，且对我们尤其值钱**：yak-ops 的采集在 JVM 内，本来就没有跨进程状态可言。
"指纹随实体走、服务端判增量"让我们**不需要任何 state store / watermark 表**，
重跑幂等天然成立。剔除易变字段那两行清单是踩坑成果——尤其是 `expiryDate`：认证有效期是会被
展示格式影响的对象，进指纹就会造成"每天全量变更"的假象。

### 3.4 陈旧删除：连接器交"看见集合"，服务端算差集 + 防空集误删

OM 的接口是 `DELETE /v1/tables/deleteStale`（`docs/generated/api-reference.md:539`；
请求体 schema `openmetadata-spec/src/main/resources/json/schema/type/bulkDeleteStaleRequest.json`）。
实现 `EntityRepository.java:13378` `bulkDeleteStaleEntities(BulkDeleteStaleRequest, deletedBy)`，
方法注释 `:13368-13377` + 实现里的六道安全设计：

1. 连接器交 `seenFqns`，"scope 下活着但不在集合里"即判陈旧。
2. **空 `seenFqns` → 零删除**，并 `LOG.warn`。注释是全场最值钱的一句
   （`:13383-13386`）：*"An empty seen-set cannot be distinguished from a connector run that
   crashed or discovered nothing, so it must never be interpreted as 'every entity under the
   scope is stale' — that would **silently delete the whole service/database**."*
3. `dryRun` 参数，走同一套判定只报告不删。
4. **FQN 按 hash 比较**（`:13375-13376`："so quoting or case differences between the
   connector-supplied and stored values never cause spurious deletes"）。
5. scope 不存在 → 零删除（`:13396`），与空集同样处理。
6. **每条删除独立事务**，单条失败不回滚整批（`:13377`）；另有
   `isCoveredByDeletedAncestor` 跳过已随祖先删除的 FQN，避免重复删与级联抖动。

### 3.5 变更事件与索引重试：两个"异步兜底"的表

- `change_event`：append-only 事件表，只有 `json` 列 + 生成列 + `eventTime` 索引（`v001:379`）。
  下游按自增偏移消费，事件是**出箱（outbox）而非通知**——消费者挂了数据还在。
- `search_index_retry_queue(entityId, entityFqn, failureReason, status, entityType, retryCount,
  claimedAt)`，PK `(entityId, entityFqn)`，`status` 默认 `'PENDING'`
  （`bootstrap/sql/migrations/native/1.12.4/mysql/schemaChanges.sql:98`）。
  `claimedAt` 是**领取令牌**：worker 先写 `claimedAt` 声明所有权，崩溃后其他 worker 可依超时接管。
  Java 侧 `openmetadata-service/…/search/SearchIndexRetryQueue.java`，
  IT `openmetadata-integration-tests/…/TableCertificationPropagationIT.java` 等。

**🟡 借"失败可重放"的形状，不借它的组件。** 我们暂不建搜索索引，也就没有索引重试队列；
但采集任务本身需要同样两件事：**采集运行记录（run）表 + 每个失败对象的独立事务**。
`claimedAt` 式领取令牌在我们单实例部署下是过度设计，**先不建**，
plan 里以"同表同键、后续加 claim 列"的方式留出。

### 3.6 调度：Quartz，在 JVM 里

`openmetadata-service/…/apps/scheduler/AppScheduler.java` —— OM 的后端内调度器。
（注意：这跟 §0 说的"连接器不在 JVM"不矛盾——JVM 里跑的是"触发"，被触发的采集进程是 Python。）

**✅ yak-ops 完全同构且已有实现**，不必参考 OM：`yak-ops-business-lifecycle/…/schedule/`
的 Bridge + Handler 两段式——`LifecycleScheduleEngineBridge` 用 `YakScheduleGateway` 按项目登记
幂等 cron（`ensure()` 先 `gateway.snapshot(name).isPresent()` 短路），
`LifecycleTtlScheduleHandler` 实现 `ScheduleHandler`，从 payload 取 `projectId` 后用
`projectScope.call(new ProjectContext(projectId, null), …)` **恢复项目上下文**再干活。
后者是采集任务最容易写错的地方：调度线程没有 HTTP 请求头，不恢复上下文则
`@ProjectScope` 全链路失效。OM 没有这个概念，替不了我们。

---

## 4. 治理与发现：标签溯源、认证过期、搜索契约

### 4.1 标签溯源（provenance）—— 一张表解决"这个标签谁给的、可信吗"

`v001:369` `tag_usage`：
```sql
CREATE TABLE IF NOT EXISTS tag_usage (
    source TINYINT NOT NULL,       -- Source of the tag label
    tagFQN VARCHAR(256) NOT NULL,
    targetFQN VARCHAR(256) NOT NULL,   -- 实体 *或字段* 的 FQN
    labelType TINYINT NOT NULL,    -- manual, automated, propagated, derived
    state TINYINT NOT NULL,        -- suggested or confirmed
    UNIQUE (source, tagFQN, targetFQN)
);
```
两个设计点值得单独夸：
- **`labelType` 五值把"机器打的"和"人打的"、"继承来的"和"直接打的"分开**，
  于是"这个表为什么有 PII 标签"永远有答案。
- **`state = Suggested | Confirmed`**：机器/继承的标签默认停在 Suggested，
  等人工确认才升 Confirmed。治理平台的通病是自动标注淹没人工判断，这一列就治住了。
- `targetFQN` 可指向**字段**——列级打标与表级打标同表同机制。

**✅ 直接借鉴（改成我们的命名）**。plan 里对应 `yak_md_label`。

### 4.2 认证是"带有效期的标签"，不是布尔位

`openmetadata-spec/src/main/resources/json/schema/type/assetCertification.json`：
```json
"properties": { "tagLabel": {...}, "appliedDate": {…}, "expiryDate": {…} },
"required": ["tagLabel", "appliedDate", "expiryDate"],
"additionalProperties": false
```
三点：认证复用标签体系（`tagLabel` 引用而非新造枚举）；**必须带 `expiryDate`**（无期限的认证等于
永久免检，一年后就是假信号）；`additionalProperties: false`。

配合 §4.1 的 `VOLATILE_CERTIFICATION_FIELDS = {"appliedDate","expiryDate"}`（`source_hash.py`）
——**认证日期不进指纹**，所以"认证过期"不会伪装成"表结构变更"。这个配对是细节但很见功力。

**✅ 直接借鉴**：我们 `certified` 若做成布尔列，半年后一定变成没人敢关的字段。

### 4.3 Propagation：继承要显式落库并可解释

`EntityRepository.java` 引入 `PropagationDescriptor`，`TableRepository.java`、
`GlossaryTermRepository.java` 等实现；IT 见
`openmetadata-integration-tests/…/TableCertificationPropagationIT.java`。
即"域/术语/标签/认证从上层资产传导到下层"是**服务端在写入时算好并落库**（记 `labelType=Propagated`），
不是查询时递归 join。`:4571` 注释还留了一句现状说明：
*"unchanged here, so only its provenance is stale."* —— 传导会留下"内容没变但溯源过期"的状态。

**🟡 借结论不借机制**：继承必须**可解释 + 可重算**（能答"为什么"，源变了能重刷），
但 `PropagationDescriptor` 那套通用框架对我们太重。最小版本：打标时写 `derived_from`，
重算任务按 `derived_from` 扫，标 `label_type='PROPAGATED'`。

### 4.4 搜索 API 契约：一套参数等式复用二十年

`openmetadata-service/…/resources/search/SearchResource.java` `/v1/search/query` 的参数集合
（`:153-227`，逐个核对）：
`q, index, deleted, from, size, search_after(多值), sort_field, sort_order, track_total_hits,
query_filter, post_filter, fetch_source, include_source_fields, exclude_source_fields,
getHierarchy, explain`。

这就是 Elasticsearch 的查询面直接摊在 REST 上：`search_after` 深分页、
`query_filter`（影响聚合）/`post_filter`（不影响聚合）之分、
`include/exclude_source_fields` 裁剪返回、`explain` 调试打分。

**✅ 借参数命名与语义分层，🟡 不借实现载体。** 我们第一版用 MySQL FULLTEXT + LIKE 顶，
但**接口参数名与语义要现在就照这套定**（`q/index/searchAfter/queryFilter/postFilter/
includeFields/excludeFields/trackTotalHits`）。这样日后换 ES 只换实现、不动前端契约。
plan §查询里的 `MetadataSearchBackend` 接缝就是为这句话存在的。

### 4.5 治理流程：状态 + 任务 + 工作流引擎

`EntityStatus` 7 值（§1.1）+ Flowable `WorkflowDefinition` + 一等 `Task` 实体。
OM 在 Task 上写死了一条铁律：**self-approval can never happen**（提单人不能自审）。

**🟡 借"状态 + 一等任务"两件套，❌ 不借 Flowable。** 引入 BPMN 引擎去驱动"元数据补齐/符合性复核"
这类 3 步流程，收益远小于多一个引擎的运维与理解成本。
但 **"谁欠谁一件事"必须有实体表**，否则治理就退化成报表；`self-approval` 那条必须原样保留。

### 4.6 OM 自己没做到的（对照价值最高的一部分）

`ARCHITECTURE.md` 的 Non-invariants 是作者自陈的债，恰好是我们的免费避坑清单：

- **文档库诱导出包环**：`resources ↔ jdbi3` 130/99，21 对里 18 对成环，只有 `security/` 是半汇聚点。
- **组件库迁移停滞**：antd 被 864 个文件 import，官方封装 `ui-core-components` 只有 522 个（1.65×），
  近 90 天里 68.5% 仍在改 antd。→ **一次"我们应该统一到 X"的迁移，两年后可能停在 60%。**
  yak-ops 前端已有 antd + 自建 components 混用现状，据此判断：不再新增封装层。
- **没有防腐层**：1,292 个 components/pages 直接 import `generated/`，`rest/` 里只有 93 个（13.9:1）。
  → 我们元数据的读接口必须**收敛在 `rest`/`api` 层**，不让页面直连 DTO。

---

## 5. 蒸馏结论对照表

| # | OM 机制 | 证据 | 判定 | yak-ops 落点 |
|---|---|---|---|---|
| 1 | 904 JSON Schema → 四路 codegen | `ARCHITECTURE.md` I4 | ❌ | 手写 PO/VO/TS，维持现状 |
| 1b | **`Type` 即数据（元模型）：类型/扩展字段可在运行时以数据新增** | `entity/type.json:5,19,57,61,63`；`type/customProperty.json` | ✅ | `yak_md_type_def` + `yak_md_field_def`（plan §2.2） |
| 1c | 基座 `Type` 统一携带 `domains`/`fullyQualifiedName`/`version`/`changeDescription` | `entity/type.json:30-105` | ✅ | 基座字段 = `yak_md_entity` 真实列 |
| 1d | 扩展字段：**存储免费、检索要配置** | `CustomPropertySearchFields.java:14-18`；`AssetTypeConfiguration.searchFields` | ✅ | `searchable`/`boost`/`match_type` 三列进字段定义（plan §4.5） |
| 1e | 扩展成本两档：加字段=数据、加类型=代码 | `Entity.java:98,458,767`（无 `EntityType` enum） | ✅ | 如实写进契约，**不承诺加类型免代码** |
| 2 | 一实体一表（700 张 `*_entity`） | `v001` 二十余张起 | ❌ | 拒绝表爆炸 |
| 2b | **单表 + `json` + STORED 生成列 + `entity_extension` 侧表** | `v001:40,136`；`EntityDataDAOs.java:997` | ✅ | `yak_md_entity` 骨架 + `yak_md_entity_extension` |
| 3 | `ascii_bin` FQN hash 唯一键破 3072 限制 | `native/1.12.0/mysql/schemaChanges.sql:21,240,297` | ✅ | `fqn_hash CHAR(32)` |
| 4 | `entity_relationship` / `field_relationship` 边表 | `v001:4,24` | ✅ | 复用既有 `yak_metadata_relation`，不建第三表 |
| 5 | 90+ 方言连接器做结构反射 | `.../database/doris/metadata.py` | ❌ | `DataSourceCatalog.listTables/listColumns` 已覆盖 |
| 6 | 方言自带统计/分区 SQL | `doris/queries.py` `SHOW PARTITIONS` | ✅ | `StatsProvider` 按方言，沿用 `statementsFor()` 回落式 |
| 7 | `sourceHash` 指纹 + 服务端跳写 | `source_hash.py`；`EntityRepository.java:12788,12841` | ✅ | `content_hash`，无需 state store |
| 8 | 易变字段白名单剔除 | `VOLATILE_*_FIELDS` | ✅ | 指纹字段清单显式枚举 + 单测锁定 |
| 9 | `deleteStale` 空集 → 零删除 | `EntityRepository.java:13383-13386` | ✅ | 采集质量守卫（外加 OM 没有的坍塌比例熔断） |
| 10 | dryRun / 单条独立事务 / hash 比较 | `:13374-13377` | ✅ | 采集预演 + 失败隔离 |
| 11 | `change_event` append-only outbox | `v001:379` | 🟡 | `yak_md_collect_run` 运行记录，事件后续再议 |
| 12 | 索引重试队列 + `claimedAt` 令牌 | `native/1.12.4/mysql/schemaChanges.sql:98` | 🟡 | 形状保留，单实例下不建 claim 列 |
| 13 | `tag_usage` 的 `labelType`×`state` | `v001:369` | ✅ | `yak_md_label(label_type, state, …)` |
| 14 | 认证 = 标签 + 起止日期，`required` 三者 | `assetCertification.json` | ✅ | 认证必须带 `expires_at` |
| 15 | `PropagationDescriptor` 继承落库 | `EntityRepository.java`、`TableCertificationPropagationIT.java` | 🟡 | `derived_from` + 重算，不造通用框架 |
| 16 | `/v1/search/query` 参数面 | `SearchResource.java:153-227` | ✅ | **接口参数名照搬**，实现先用 MySQL |
| 17 | `EntityStatus` 7 值 + 默认 `Unprocessed` | `type/status.json` | ✅ | 符合性状态沿用 |
| 18 | Flowable + `Task` + 禁自审 | `service/governance/`、IT | 🟡 | 建 `yak_md_task` 与迁移表，**不引 BPMN 引擎** |
| 19 | DataProduct Port / ontology 等 2.x 新品 | — | ❌ | 与当前痛点无关 |
| 20 | 连接器进程外 + 批量 HTTP 重试 | `ARCHITECTURE.md` Path B | ❌ | 进程内 SPI，无此问题 |

---

## 6. 引用勘误（写作时须遵守）

以下三处是我在调研中主动纠正的**错误前提**，本文已按纠正后事实书写；后续引用不要退回旧说法：

1. OM 2.0 **没有** Python 侧 `pipeline.py` 状态存储与 `SourceStateHandler` 的现行实现——
   增量判定已迁到服务端 `sourceHash`（`topology_runner.py:336` 明确 "handled server-side"）。
   不要引用旧版"连接器自带水位/状态文件"的说法。
2. `openmetadata-spec/…/json/schema/entity/governance/domain.json`、`collection.json` **不存在**
   于当前树（已核对）；域/集合的具体位置请以 `find` 结果为准，别照抄旧路径。
3. autocomplete / agentic search **不在** 当前 `/v1/search` 契约中（`SearchResource.java:153-227`
   无相关参数）；`/fieldQuery`、`/aggregate`、`/nlq/query` 属另外的 resource。本文 §4.4 只主张
   `/query` 这一条契约。
4. `entityStatus.json` 这个文件名**不存在**；7 值枚举的真实载体是
   `openmetadata-spec/src/main/resources/json/schema/type/status.json`（`title: EntityStatus`）。
5. OM 的 **Doris 连接器不采集存储字节量**（`doris/queries.py` 只有
   schema/columns/comments/views/partitions 五段，无 `SHOW DATA`）。
   反倒是 yak-ops 的 `StorageSnapshotService` 有。所以"存储量"我们不是学生是先生——
   见 plan §复用约定，元数据模块**必须复用 lifecycle 快照，禁止二次采集**。
