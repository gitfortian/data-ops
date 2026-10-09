# Architecture review · 架构评审记录

> Class: Evidence / Review · **非正式迁移执行授权**。

- [2026-10-03 架构优化提案](20261003/architecture-optimization-plan.md)
- [2026-10-03 实施记录](20261003/implementation.md)
- [2026-10-03 运行和迁移合同盘点](20261003/runtime-and-migration-contracts.md)
- [当日运行证据 JSON](20261003/evidence.json) 与 [清单 JSON](20261003/inventory.json)

实际 Framework / Security / Persistence / Migration 的依赖和装配边界以目标模块 `ARCHITECTURE.md`、`DEPENDENCIES.md`、当前 Maven reactor、现行 Flyway 检查和对应架构 PR 为准。不能仅据早期优化提案移动 Bean、SQL、Mapper 或改造运行入口。
