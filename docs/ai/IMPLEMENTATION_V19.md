# V19 场景回归与 J2 验收工具

基于已合并 #338（main 306563f4），分支 codex/ai-skill-evaluation-j2。当前合同 [F-030](../product/features/F-030-agent-scenario-evaluation.md)，沿用 F-011 与 F-023–F-029 的源域及执行边界。

- 新增 20 例四场景题集，含原子/派生/复合草稿、历史解释与负向场景；5 例多步人工，保留已有 12+16 例。
- 复用 runner，新增嵌套目标绑定、同轮 continuation/history 交付观测、bizData 兼容、有界 JSON、精确轮次清理；不把 SSE 或回执结构当候选语义正确。
- 只读 J2 采集固定 GET，核对模型 TYPE 引用、来源映射、指标不可变快照、验证/发布精确摘要、Dataset 声明与 canonical 身份。消费覆盖与本次执行证据分开，操作审计/查询/全程验收仍 PENDING。
- 基线汇总分开缺失/零分、成功/失败耗时与 token、生成/人工报告采纳/保存；评分绑定报告及轮次哈希。未获真实计价/对照时费用 UNKNOWN、收益 NOT_MEASURED。

不修改生产业务、数据库、运行时 Skill 或前端。确定性回归通过现有 scripts/architecture/agent-evaluation.test.mjs 纳入 CI；执行步骤见 [场景与 J2 说明](./evaluation/scenario-j2.md)。真实账号/模型/源保存/查询与人工收益尚未执行，不提升既有 Feature 状态。
