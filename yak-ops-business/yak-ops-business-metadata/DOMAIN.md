# Metadata Domain

## 核心概念

### 统一实体目录（Plan §1.1 / §2）

一张统一实体表承载所有元数据类型：**类型判别列 + 属性 JSON + 提槽生成列 + 扩展字段定义表**。
加一类元数据 = 插一行 `yak_md_type_def` + 写一个 provider；加一个字段 = 插一行 `yak_md_field_def`，**不改表结构**。
这否掉了"每种元数据一张归一化表"——那会让"统一检索"退化成 N 路查询 + 人工合并排序。

统一表落点 = **就地扩展 lineage 既有的 `yak_metadata_asset`（B 案，plan §2.5 / §11.1.5；A 案新建 `yak_md_entity` 作废）**。
一张表同时是"血缘图节点"与"目录实体"，血缘、层级、检索、治理共享一份事实，零同步器。

### 元模型（`yak_md_type_def` / `yak_md_field_def`）

**类型与字段是行，不是代码**（借 OM 的元模型层，不借它的 JSON Schema → 代码生成流水线，蒸馏 §1.2）。
一期 8 类实体：`databaseService` / `database` / `table` / `tableColumn`（HARVESTED）
+ `dataModel` / `standardField` / `domain` / `metric`（REGISTERED）。
层级不靠 `parent_types`，靠**把列提成独立实体行 + `parent_asset_id` 挂父**（对 OM 的主动偏离：它用 ES 嵌套文档，我们没有 ES，
而符合性对账必须在 MySQL 里按列名跨表查）。

### 投影 vs 归属（本模块最重要的一条线，plan §1.3）

> **只有当"没有其他模块拥有它"时本模块才是 owner；否则只存"目录投影"**
> ——检索/展示/治理所需的少量字段 + 指向源域的指针 + 源侧指纹；详情实时读源域 SPI。

| 实体 | 归属 | 目录里存 | 详情从哪来 |
| --- | --- | --- | --- |
| `table` / `tableColumn` | **本模块 own** | 全属性 | 目录本身 |
| `dataModel` / `standardField` / `domain` / `metric` | 源域 own | displayName/summary/owner/domain/状态/**源指纹** | **实时** SPI 调源域 |

**目录里绝不出现**：模型的列定义、指标的公式、标准的字典项。
"own" 指**语义所有权**（谁定义并写入这一行的这一列），不是物理表所有权——后者是 lineage 的。

### 双指纹（plan §3.3）

- 物理侧 `content_hash` = 结构指纹，**由本模块自己算、自己判增量**（规范化关键字段串，列按 ordinal 升序，注释折叠空白，name/type 转小写）。
- 投影侧 `source_hash` = 源域摘要，**由源域产出**（push 随请求交出、对账由 provider 批量产出，**同一个函数**），本模块只比对不参与语义。
- CHANGED 判据按 `provider_type` 分岔：`HARVESTED` 只比 `content_hash`，`REGISTERED` 只比 `source_hash`。
- **绝不进指纹**：链接类、`deleted`、`inherited`、认证的 `appliedDate`/`expires_at`（否则"认证过期"每天伪装成"结构变更"）。

### GONE 与质量熔断（plan §3.4）

OM `deleteStale` 六道安全**全部采纳**（空 seen 集→零删除、scope 不存在→零删除、hash 比较、dryRun、单条独立事务、祖先覆盖跳过），
再加两道我们处境需要的：**坍塌比例熔断**（单轮 gone/上轮在场 > 30% → 整轮 `SUSPECT`，不落任何 GONE）与
**连续两轮缺失才 GONE**（对齐 asset 的 `SOURCE_GONE` 窗口）。GONE = `gone_at` 软删，不物理删，并撤销 lineage 图节点。
**熔断路径对采集与对账两条入口同等生效**（否则 provider 抛异常被 catch 成空页就会清空该类实体）。

### 两条入口与三个必须（plan §3.2b / §3.2c）

内部实体**由源域写成功后 push 登记**，定时拉取降级为对账。判据：现网没有任何一个内部实体靠定时任务进图
（modeling/dataset/analysis/dashboard 全在写路径上登记），定时采集会把"搜不到刚改的东西"变成日常。
三个必须：**① 事务提交后登记**（事务内 → 回滚留幽灵行；异常上抛 → 目录故障拖垮业务保存）；
**② 失败落库可重放**（`yak_md_register_retry` outbox + `@Scheduled(fixedDelayString)` worker）；
**③ 同实体保序**（`sourceUpdatedAt` 旧于现值则只刷在场时间、不改内容，等价 `writeIfLatest`）。
**索引侧不因 push 双写**：目录就是 MySQL 一张表；ES 仍按 plan §4.4 阈值。

### 治理层（plan §6）

标签溯源 `yak_md_label`：`label_type`（MANUAL|AUTOMATED|PROPAGATED|DERIVED）× `state`（SUGGESTED|CONFIRMED）双维度分离——
机器/继承默认 `SUGGESTED` 等人工确认，治好"自动标注淹没人工判断"。认证 = 特定 `label_code` + `expires_at` 非空，不是布尔位。
待办 `yak_md_task`：`entity_status` 7 值（区分"没人看过"与"看过但不合格"）+ `open_marker` 去重位
（**`UNIQUE(…, resolved_at)` 语义是反的**：MySQL 里 NULL 彼此相异；生成列方案又被 `3109` 拒，故只能是服务层写入的普通列 + `CHECK`）。
**不引 Flowable/BPMN**，但**提单人不能自审**这条铁律原样保留并硬校验。

## 命名规则（写死，出现第三个名字即契约违反，plan §4.2）

| 语义 | API/JSON/DTO 层 | 落库列 | 说明 |
| --- | --- | --- | --- |
| 类型判别 | `typeName` | `type_id` → `yak_md_type_def.id` | **不是 `asset_type`**（那是 lineage 的图形状闭集枚举，存量已同值不同义） |
| 属性袋 | `attributes` | `md_attributes` | **绝不复用 `properties`**（lineage upsert 整包覆写，plan §2.3 后果 2） |
| 目录版本 | — | `catalog_version` | 不复用 lineage `yak_metadata_relation.version` 的语义 |
| 展示 FQN | `fullyQualifiedName` | `fully_qualified_name` | 仅展示与反查；`fqn_hash = md5(lower(asset_key))` 只建普通索引 |

`entityType` / `type` / `kind` 之类第三个名字视为违反。

## 不变量

1. **身份只有一个来源 = `asset_key`**（lineage 的 `uk (project_scope_id, asset_key)`）。本模块**不新增唯一键**；
   `fqn_hash`/`fully_qualified_name` **只允许在 `MetadataKeyCodec` 一处生成**（grep 守护，plan §10 测试 13）。
2. **登记出的行 `project_id` 必须非空**，入库前断言（49xxx）；provider/collector 永远带项目上下文。
3. **遗留 234 图节点的 NULL 是语义**（"早于目录机制"），不填假值；覆盖率/新鲜度类指标**必须排除 `provider_type IS NULL`**。
4. **认领而非增殖**：注册通道跑完后现网 `TABLE+MODELING` 仍 11 行、`semantic:field:%` 仍 8 行、`metric:%` 仍 4 行，
   只是 `type_id` 由 NULL 变非空；任一数字翻倍即失败。
5. **扩展字段的可搜性由元模型驱动，不由代码驱动**：`searchable=1` 而未提槽 → 保存即 49xxx 拒；
   槽位分配走 `MetadataSlotRegistry` 集中登记，两类型抢同槽**报错而非静默复用**（7 槽一次建齐，加第 8 槽是运维窗口）。
6. **默认检索面不含 `tableColumn`**（列/表 ≈ 12.6:1，会淹没其他类型），以"命中 N 列"聚合露出；显式 `index=tableColumn` 仍可直接出行。
7. **`yak_md_change` append-only**，永不 UPDATE/DELETE；outbox 的 DONE/DEAD 行可清理（审计在 change，不在 retry）。
8. **项目隔离**：`project_id` 只取 `CurrentProject` 服务端上下文，永不接受前端传入；不建物理外键。
9. **无界禁止**：游标分批 ≤500；概览查询 ≤8 次；列表一律分页；单库表数超阈值分页拉取。
10. **分区容错不伪造**：详情 fan-out 单块失败返回 UNAVAILABLE；统计能力缺失记 UNKNOWN 而非 0。
