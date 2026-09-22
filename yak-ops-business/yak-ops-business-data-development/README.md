# Data Development

Data Development 是 Yak-Ops 的数据开发工作台，负责 Node、Task Draft/Revision、编辑器运行、Dataset/Data Service 输出节点，以及 SQL Lineage evidence 的产生。

核心关系只有一句：

```text
Node != Draft != Revision != Execution
```

## 文档入口

- `REQUIREMENTS.md`：模块需要提供什么能力
- `DOMAIN.md`：实现不能破坏的领域事实
- `ARCHITECTURE.md`：代码角色与 package owner
- `DEPENDENCIES.md`：允许的依赖方向与跨模块 corridor
- `REVIEW.md`：提交前和 Review 时怎么检查
- `MIGRATIONS.md`：Data Development 独立 Flyway namespace、一期 V1 baseline 与正式发布后的版本规则
- `EXECUTION_CONTROL_PLANE.md`：编辑器异步提交、Execution 控制 API、重连与运行时恢复语义
- `PROJECT_GOVERNANCE.md`：Project 强隔离、权限动作与历史数据回填契约
- `ENGINEERING_HARDENING.md`：Stage 3 Repository/JDBC、后台 Project Context、legacy island 与前端 Workbench 硬化契约
- `../../CODE_STYLE.md`：仓库通用代码规范

## Package Map

```text
io.yak.ops.business.development
├── controller
├── node
├── directory
├── task
├── execution
├── dataset
├── dataservice
├── release
├── editor
├── lineage
├── domain
├── repository
├── dao
├── config
└── service      # frozen legacy island; 禁止新增
```

`Service` 不是禁用词。稳定应用入口可以叫 Service；禁止的是把不同角色重新塞回一个通用 `service/common/helper/utils` 大桶。

当前 `service` 仅保留尚未安全迁移的 SQL Lineage 大实现、Data Service Node 大入口、一个无逻辑 compiler compatibility shell 和两个兼容异常。Data Service Runtime Source Provider 已归 `dataservice`；Execution / Editor / Lineage Outbox 的 SQL 已归 Repository adapter。

## 数据库基线

第一期正式发布前，Data Development 只保留一个最终 Flyway baseline：

```text
classpath:db/migration/yak-data-development
└── V1__baseline_data_development.sql

history table
└── yak_data_development_schema_history
```

一期 baseline 直接描述当前最终表结构，不保留开发过程中的 `ALTER TABLE`，也不会重新创建已经移除的 `yak_dev_graph`。正式发布后 V1 冻结，后续数据库变化从 V2 开始增量迁移。具体 reset 和版本规则见 `MIGRATIONS.md`。

## 开发前

先确认改动属于哪个 truth owner，再确认入口角色和依赖方向。涉及 Draft / Revision / Execution / Task Catalog / Lineage 语义时，优先查看 `DOMAIN.md`；涉及新依赖时查看 `DEPENDENCIES.md`；涉及持久化、后台 project scope 或 legacy 迁移时查看 `ENGINEERING_HARDENING.md`；涉及数据库结构或 Flyway 版本时先查看 `MIGRATIONS.md`。

推荐验证：

```bash
mvn -pl yak-ops-business/yak-ops-business-data-development -am test
```
