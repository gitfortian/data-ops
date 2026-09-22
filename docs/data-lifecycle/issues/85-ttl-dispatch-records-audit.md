# Ticket 85：策略下发 + 下发流水 + 审计

**对应需求：** 3.6 | **阶段：** P1 | **模块：** lifecycle

**What to build：** 用户在预览确认后（可批量）下发：逐模型生成语句并经 `TtlSqlGateway` 执行 ALTER；每模型落一条 `yak_lc_dispatch_record`（语句快照、policy_updated_at、结果、错误、操作人、trigger、分区归类快照）；失败记录 next_retry_time；无 confirmToken 或过期 → 47009 拒发（防盲发）。

**Blocked by：** 84

**验收清单**
- [ ] `dispatch/TtlDispatchService.dispatch(modelIds, token, operator)`：单模型内 try/catch，互不影响，逐条返回结果
- [ ] `POST /api/v1/lifecycle/models/lifecycle/dispatch` {modelIds, confirmToken}
- [ ] 数据源不可达/不支持 → 47007/FAILED 记录（错误信息落库、可读）
- [ ] writable=false 的策略（未知方言）拒发并提示"仅可复制手工执行"
- [ ] 审计 TTL_DISPATCH（BATCH/MANUAL 记 metadata），成功/失败都留痕
- [ ] 流水查询 `POST /dispatch-records/page`
- [ ] 单测：成功/失败/部分成功批量语义、token 校验、记录字段完整性（gateway mock）
