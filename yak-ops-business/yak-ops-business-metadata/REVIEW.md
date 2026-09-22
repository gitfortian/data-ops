# Metadata Review Guide

每张 ticket 评审时按以下清单核对（括号内为判据出处；plan §9 的 T# 是本仓库已验证的真实坑）。

1. **契约先行**：契约文件集 diff 先于代码 diff；`REQUIREMENTS.md` 覆盖本票全部用户可见行为。
2. **投影不越界（D-§1.3）**：`dataModel`/`standardField`/`domain`/`metric` 的 `md_attributes` 内**不得**出现列定义、指标公式、字典项；
   详情列清单一律实时读源域。做不到就是第二套真相。
3. **共表纪律（B 案专属）**：目录列 ALTER 是 `db/migration/yak-lineage` 的**新文件**，`V1__baseline_lineage.sql` 在 `git diff` 里**零改动**（T5）；
   本模块 upsert 的 UPDATE 子句**不含** `asset_type`/`parent_asset_id`/`properties`；读侧永不使用 `properties`；
   登记行 `project_id` 非空入库前断言；**看门狗断言"同一 `asset_key` 在场行 ≤ 1"**（T19，唯一能发现归属被改的手段）。
4. **键同源**：`asset_key` 由源域 provider 交出，本模块只校验非空 / ≤512 / 前缀等于 `key_prefix`；
   `fqn_hash` 与 `fully_qualified_name` **只允许出现在 `MetadataKeyCodec` 一处**（grep 守护，T-§2.4.3）；
   现网 234 行**认领不增殖**（TABLE+MODELING 仍 11 / `semantic:field:%` 仍 8 / `metric:%` 仍 4）。
5. **元模型驱动，不是代码分支**：搜索条件由 `field_def` 运行时生成；新增类型/字段零 DDL 零 `.java` 分支；
   `searchable=1` 无槽位在保存时即拒；槽位冲突报错不静默复用；`asset_type` **绝不当目录判别列**（目录走 `type_id`）。
6. **两条入口同一机制**：登记与采集走**同一个** upsert 与熔断入口（禁止复制一份判定逻辑）；
   CHANGED 判据按 `provider_type` 分岔；`source_hash` 算法在源域，push 与对账共用同一函数。
7. **写时登记三个必须**：post-commit（失败绝不拖垮源域事务，挂钩只写队列不再抛）；
   可重放（outbox + `@Scheduled fixedDelay` worker + `due→claim→complete/fail` 退避）；
   保序（旧 `sourceUpdatedAt` 只刷在场时间不改内容）。四种错误形状见 T20，逐条对照。
8. **采集安全**：空 seen 集 → 零 GONE；坍塌 >30% → `SUSPECT` 不落 GONE；连续两轮缺失才 GONE；软删不物删；
   单表 `listColumns` 空/异常记 `PARTIAL` 而非静默空表（否则下一轮误判"全部列被删"）。
9. **指纹稳定性**：改注释空格 → hash 不变；改 `nullable` → hash 变；改 `expires_at` → hash 不变。字段清单以常量集合写死在一个类 + 单测锁定。
10. **搜索边界**：BOOLEAN MODE 特殊字符集中转义（`+ - > < ( ) ~ * " @`）；单字查询降级 `LIKE` 并在 `explain` 标注；
    `queryFilter` 影响聚合计数、`postFilter` 不影响；`attr.<field>` 只认 `field_def`；跨类型 SQL 条数不随类型数增长；
    `search_after` 代 offset、`trackTotalHits` 默认 false（无界禁止）。
11. **不建第二份采集**：无 `SHOW DATA`、无 `information_schema` 字节量查询；存储量读 lifecycle 且**同时按 `database_name` 过滤**（T16）。
12. **分层与返回契约**：Controller 返回 `Result<PagingData<VIEW>>`（裸 `PageData` 会 999，T2）；
    错误码 49xxx 不被兜成 999（T3）；零源域内部包 import（T6）；PO/错误码/权限码在 `yak-ops-common`。
13. **调度上下文**：采集/对账/重试三类线程均先恢复 `ProjectContext` 再动手（T10）；调度器缺位不阻断启动。
14. **治理同构**：对一条列打标/建待办与对一张表走同一套代码（断言不存在 `if (targetType)` 分支）；
    自动标签一律 `SUGGESTED`；认证必经 `expires_at`；**提单人不能自审**（硬校验 + 单测）；
    继承重算必须**幂等全量**（不是增量）。指标类概览查询 ≤8 次且**排除 `provider_type IS NULL`** 的遗留行。
15. **迁移与库层约束**：Flyway `yak-metadata` 只增不改；`yak_md_task` 三条库层用例对**真实 MySQL**（1062 / 3819 / OK），不用 H2 mock；
    全模块禁 `UNIQUE (…, 可空列)` 表达"只允许一条"（T17）。
16. **前端契约**：menuCode 过 `navigationMenuContract.test.ts`；`PROJECT_REQUEST_RULES` 已登记（T1）；
    不改 `yak-ops-ui` 下任何 `.md`；类型常量 grep 守护；读接口收敛在 `src/services`（T14）；
    表单遵守"**能选择就不填、能默认就不留空**"，删除/忽略类批量操作必经预览，规则必须 dry-run 后才可启用。
17. **环境诚实**：Doris/Paimon/数仓五库路径标"环境受限、不可本地验收"，**禁止写成 verified**（T12）；
    新 Java 类与新迁移待用户 IntelliJ 重启后方可端到端验证（T11）。
