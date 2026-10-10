# F-039 / #495 — 来源范围、分片计划与跨轮 CAS 内核

Status: KERNEL_IMPLEMENTED / PRODUCTION_NOT_CONNECTED  
Issue: #495; epic #493; depends on merged #494

本批次以一个 PR 提交可独立验证的最小长任务工程内核：

- SourceSemanticScope 为经过可信来源选择之后的表/列、项目、采集批次及指纹提供有界、不可变值对象和稳定 SHA-256。**它不执行权限或采集完整性检查**；HTTP/模型不能直接构造已授权来源的事实。
- SourceSemanticChunkPlanner 确定性拆分大表/多表、保留所有列、生成按来源指纹绑定的片段身份。无数据行/SQL/Python 或自动关系结论。
- SourceSemanticTaskState / SourceSemanticTaskLedger 依赖 AgentScope 2.0.3 的 versioned CAS，保存有限任务身份、进度摘要、plan hash、source hash、原 turn ID、结果摘要、跨 turn 保守预算。仅原 AgentTurn/StateStore 拥有实际模型执行、消息、pending、终态。
- 先持久化 reservation 才可安排下一原 turn；崩溃 INTERRUPTED 后必须重查 scope+plan 并建立新 turn；取消、迟到结果、并发争抢和额度用尽 fail-closed，失败不退款。非 CAS Store 坚决拒绝，不能降级为最后写入者覆盖。
- 使用 JUnit 内存 CAS、并发竞争、真实 CI MySQL CAS 重建和归属隔离验证。

**这个 PR 尚未完成 #495 的 UI/API 交付**。不注册生产 Agent Skill、工具、Controller、Job/Workflow，也不新增迁移或源域写操作。后续在 Metadata Owner 批准原只读来源事实指纹/权限接口、PlanMode 应用绑定审核、原 AgentTurnExecutor 与用户确认接入后，才能为页面提供实际入口；这不是产品自动批准或正式功能上线。还需补充真实 Token/时间预算、多节点任务 workspace、SSE 读视图、已验证片段模型输出及 UI E2E。

本内核的 cancel 只封锁辅助账本；正式调用时必须通过原 AgentTurnRegistry 精确停止活动轮并核对原状态。plan_exit SDK 本身不验证 PLAN.md；用户确认的文件/hash/来源权限必须由应用额外校验。
