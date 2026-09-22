将前面整理的 Agent 可观测性官方化重构计划完整输出为文档 `docs/agent/agent-observability-official-refactor-plan.md`(仅创建/写入这一个文档,不做任何代码改动)。

文档内容(已整理完成,直接落盘):
- 背景与目标、现状评估(自建 vs 业务强依赖)、官方 AgentScope 2.0.2 能力核实、架构决策;
- 分阶段实施计划(Phase 0 官方构件验证 → Phase 1 OTel → Phase 2 删自建 span/step → Phase 3 AG-UI 迁移 → Phase 4 Studio);
- 文件级删留清单、前端改动清单、风险与缓解、验收标准、待办/开放问题。