# S5 ｜新对象版本化脚手架 checklist（一页落地版）

> 依据：`docs/multi_version_unification.md` 契约 C1–C5。给一个新业务对象加多版本时按本单逐项打勾。
> 参照实现：后端 = digital-screen `DigitalScreenPublisher`（追加式回滚）+ modeling V16 迁移（DDL）+ 本轮 W1-2 `OfflineJobRevisionService`（双表最小组合）；前端 = S4 `VersionHistoryPanel`/`JsonDiffView`。

## 1. DDL（C2，模板照抄）

```sql
CREATE TABLE IF NOT EXISTS xxx_version (          -- 版本表：append-only 全量快照
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    biz_id BIGINT NOT NULL,                       -- 指向主表业务 ID
    version_no INT NOT NULL,                      -- 对象内 MAX+1 递增
    content_json LONGTEXT NOT NULL,               -- 发布时冻结的全量内容（含元数据快照）
    checksum CHAR(64) NULL,                       -- 展示/追溯用；等值判断不依赖它
    created_by VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_xxx_version (biz_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE xxx                                 -- 主表五字段
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN published_version_id BIGINT NULL COMMENT '当前生效版本；NULL=从未发布',
    ADD COLUMN latest_version_no INT NOT NULL DEFAULT 0;
-- 另需 draft_revision（草稿修改计数，乐观并发用）与 created_by；已有则跳过。
```

- 存量回填：对「已生效但无版本行」的老数据生成 v1 并把指针指过去，保证执行/消费路径行为不变。
  **坑（W1-2 实测）**：主表列可空而快照列 NOT NULL 时，回填 SELECT 必须 `COALESCE` 兜底（如 config_digest），否则迁移直接失败卡启动。
- Flyway：迁移一旦应用不可改；每个模块登记自己的迁移清单测试（offline 有 `OfflineSyncFlywayContractTest` containsExactly 锁）。

## 2. 状态（C1）

- 发布态一律用 `io.yak.ops.common.enums.PublishState{DRAFT,PUBLISHED,OFFLINE}`；读写口一次性切换，不留双写；存量别名（ONLINE 等）在 `PublishState.of()` 解析层收敛 + 新 V 号 `UPDATE` 归一。
- ENABLED/DISABLED 类开关与发布态正交，不要复用 status 列。
- **坑（W1-4）**：schedule.status 等“别的对象的同名 ONLINE”不要被连坐替换，先盘点比较点归属。

## 3. 端点（C3 六件套）

`PUT /{id}/draft` · `POST /{id}/publish` · `POST /{id}/offline` · `GET /{id}/versions` · `GET /{id}/versions/{no}`（diff 数据源）· `POST /{id}/versions/{no}/rollback`

- 发布幂等 = **语义等值比较**（digest+内容双判），不依赖存储 checksum（回填行口径可能不同）。
- 回滚语义先裁决：追加式 activate（digital-screen/W1-2/W1-4）或两步「恢复为草稿→再发布」（W1-3，模型消费面大时选它）。同一路由不要把两种语义混卖。
- Controller 只依赖 Application Facade（offline 模块有架构测试执法）；operator 解析下沉到 service（经 audit 的 `AuditActorResolver`，模块不直连认证框架）。

## 4. Service 三件套命名

`XxxPublisher`（publish/publishIfNeededOnOnline/rollback）· `XxxVersionWriter/Mapper`（`nextVersionNo` 一律 SQL `COALESCE(MAX(version_no),0)+1`，禁 selectCount+1）· 读路径 `publishedRevision()/publishedStructure()` 指针取快照（C4）。

## 5. 草稿分离（C4）

- 主表内容列语义收窄为「可编辑草稿」；消费方（执行/调度/工作流/血缘/派生/DDL/资产投影）一律经 `published_version_id` 读快照。
- **坑（W1-3）**：消费点盘点要全仓 grep（`findColumns`/`structureService.get` 之类），设计期工具（映射/变更检测）可保留读活表，运行/下发期必须读快照；给「指针悬空/从未发布」准备自愈兜底。
- 上线/启用路径补发 v1（`publishIfNeededOnOnline`），保证存量与新建对象执行不中断。

## 6. 审计（C5 / S3 件）

- `AuditTransactions.completeOnCommit`（事务提交后落账）；diff 用 `AuditDiffs.diff(before, after)`，值可空时禁 `Map.of`（NPE）。
- 回滚覆盖草稿前留被丢弃草稿的轻量指纹；敏感字段脱敏在 AuditDiffs 内。

## 7. 前端（S4）

- 列表：`latestVersionNo` + `hasPendingDraft` 徽标（「V{n}/未发布」+「未发布修改」）。
- 操作：发布（确认文案写明影响面，`appended=false` 幂等 toast 区分）；版本抽屉直接挂 `VersionHistoryPanel`（listVersions/getVersion/rollback 三函数 + `currentVersionNo` + 回滚二次确认 description 按本对象语义改写）。
- 发布按钮：先「未保存守卫」，再「草稿 vs 上次发布」`JsonDiffView` 确认（W1-3 模式）。
- VO 加字段注意 `@AllArgsConstructor` 改构造器签名（W1-2 踩坑）；`npx tsc --noEmit` 对齐基线门禁。

## 8. 验收底线（README 统一条款）

改草稿不发布 → 消费方仍读旧发布版；连发两次相同内容不追加版本；回滚二次确认明示影响；版本列表/单版/diff/回滚四操作页面实测过；审计记录含字段级 before/after。

## 9. 测试基线套路（本轮五单经验）

- 架构契约红的归属判定：`git worktree add ../x HEAD` 跑同一批测试钉基线，禁止用 stash（共享 worktree）。
- 纯单测里 MyBatis-Plus `LambdaQueryWrapper` 需 `TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""), PO.class)` 预注册（W1-2 DAO 测试经验）。
- 模块全量绿 ≠ 存量绿：存量红如实上报，不混入本单修复。
