# Modeling

Modeling 是 Yak Ops 的数仓建模模块,负责数仓模型的定义、落地、加工映射与模型资产视图:正向(逻辑模型 → 物理模型)与逆向(存量库表导入)双通道,模型作为贯穿开发、血缘、数据服务的统一资产。

## Read First

本目录只维护当前有效 contract,历史演进以 Git / Pull Request 为准。

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块能力和非目标 |
| [`DOMAIN.md`](./DOMAIN.md) | Model / LogicalModel / Mapping / Version 语义 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 角色 package 与边界 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package、跨模块与 Maven 依赖方向 |
| [`REVIEW.md`](./REVIEW.md) | 评审和拒绝标准 |
| [`CODE_STYLE.md`](../../CODE_STYLE.md) | Yak Ops 工程规范 |

## 需求与计划来源

- 需求规格:[docs/model/modeling-requirements.md](../../docs/model/modeling-requirements.md)(决策 D1~D10 已确认)
- 开发计划与硬性约束:[docs/model/dev-plan.md](../../docs/model/dev-plan.md)(契约先行是硬性要求:任何 ticket 先更新本目录契约,再写代码)

## Current Contract

当前已生效契约:持久化边界 + 模型 CRUD 纵切(ticket 02)+ 目录/标签组织纵切(ticket 03)+ 回收站纵切(ticket 04)+ 表结构编辑纵切(ticket 05)。

```text
HTTP /api/v1/modeling/models|directories|tags(@ProjectScope PROJECT_REQUIRED)
          ↓
controller/v1(REST + DTO/VO + converter,不含业务规则)
          ↓
catalog/ModelCatalogService | ModelDirectoryService | ModelTagService
structure/ModelStructureService(表基础信息 + 字段全量替换保存)
          ↓
repository/…Adapter(requireProjectId + 全查询 eq projectId,存活行 deleted=0)
          ↓
dao/mapper → yak_modeling_model / yak_modeling_model_column / yak_modeling_model_index / yak_modeling_directory / yak_modeling_tag / yak_modeling_model_tag_rel(Flyway V2~V7)
```

审计走 `data-ops-business-audit` 的 `BusinessAuditService` 门面(fail-open),事务提交后落事件的能力由 `support/AuditTransactions.completeOnCommit` 统一提供。

后续 ticket 将按 dev-plan 逐步扩展逆向导入/DDL/画布/版本/逻辑建模;每次扩展必须同步更新本目录契约文件。

## Migration

模块自持 Flyway 实例:`locations=classpath:db/migration/yak-modeling`,`table=flyway_schema_history_modeling`,baseline 版本 0,开关由 `ConditionalOnModelingPersistence`(数据源启用)控制。菜单注册 migration 不在本模块 —— Yak Security 菜单目录统一放在 `data-ops-boot/src/main/resources/yak-security/db/migration`(建模入口为 `V2018__register_modeling_menu.sql`)。
