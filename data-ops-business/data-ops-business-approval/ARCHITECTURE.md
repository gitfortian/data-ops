# Architecture

```
io.yak.ops.business.approval
├── api/          对外契约: ApprovalApi(发起/查询/撤销)、ApprovalFlowHandler(业务回调 SPI)、
│                 ApprovalSubmitCommand / ApprovalDecision / ApprovalInstanceView(记录)
├── config/       ApprovalPersistenceConfiguration(@MapperScan + 自持 Flyway 链 yak-approval)
│                 ConditionalOnApprovalPersistence(datasource 开关守卫)
├── controller/v1 + dto   REST(/api/v1/approvals),权限注解 read/create/approve/manage
├── application/  FlowAdminService(流程 CRUD+启停)、ApprovalService(核心闭环:发起/批/拒/撤/查)
├── domain/       FlowStepsCodec(steps_json 解析校验)、ApprovalStateMachine(级推进/终态清理)
├── registry/     ApprovalFlowRegistry(handler Bean 按 flowCode 索引,缺失→49007)
├── dao/mapper/   3 个 MyBatis-Plus BaseMapper(无 XML,查询用 Wrapper)
└── exception/    ApprovalException + ApprovalExceptionHandler(49003 兜底 DuplicateKey)
```

分层纪律：controller 零业务逻辑；application 是唯一事务边界
（`@Transactional(transactionManager="yakBusinessTransactionManager")`）；
domain 纯函数可单测（状态推进/快照展开/JSON 校验不触库）。
