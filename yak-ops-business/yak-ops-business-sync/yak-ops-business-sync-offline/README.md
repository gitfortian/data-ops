# Yak Ops Offline Sync

Offline Sync 是 Yak Ops 的批式数据同步控制面。当前运行模型固定为：

```text
Task -> Batch -> Attempt -> Link-Up
              |
              `-> Cursor after Batch SUCCEEDED
```

核心关系：`Task != Batch != Attempt`。

## Read First

本目录只维护**当前有效 contract**，不保存 Stage / Wave 过程文档。历史设计和迁移过程看 Git / PR。

建议按顺序阅读：

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块需要什么 |
| [`DOMAIN.md`](./DOMAIN.md) | 哪些领域规则不能违反 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 代码放哪里、角色如何协作 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package 谁能依赖谁、跨子系统从哪里进入 |
| [`MIGRATIONS.md`](./MIGRATIONS.md) | Offline Sync Flyway namespace、一期 V1 baseline 与正式发布后的版本规则 |
| [`CODE_STYLE.md`](../../../CODE_STYLE.md) | Yak Ops 仓库统一工程与代码规范 |
| [`REVIEW.md`](./REVIEW.md) | PR 按什么标准判卷 |

## Stable Entry

```text
OfflineJobDefinitionService
OfflineJobExecutionService
OfflineBackfillService
```

Controller 只进入这三个 Application Service；后台执行通过声明过的 Service / Gateway corridor 进入。

## Runtime Truth

```text
Task                = configuration
BatchExecution      = business identity + frozen snapshot + runtime truth
latest Attempt      = physical execution evidence
Task last-*         = query projection only
Cursor              = confirmed successful progress
```

Link-Up Job、Worker、Quartz、HTTP DTO、Credential 都停在边界，不进入 Core Domain。

## Flyway baseline

第一版正式发布前，Offline Sync 数据库结构只保留一个最终基线：

```text
classpath:db/migration/yak-offline-sync
└── V1__baseline_offline_sync.sql

history = yak_offline_sync_schema_history
```

V1 直接创建当前 Task / Batch / Attempt / Event / Cursor 最终结构，不重放 V2~V7 的 expand/backfill/contract 开发过程。正式发布后 V1 冻结，后续只新增 V2、V3……。

## Engineering Rule

新增代码先回答三个问题：

1. 属于哪个 subsystem？
2. 是什么 role？
3. 允许从哪里被依赖？

答不清楚时，先看 `ARCHITECTURE.md + DEPENDENCIES.md + MIGRATIONS.md + ../../../CODE_STYLE.md`，不要创建新的 `Common / Helper / Utils` 大桶。
