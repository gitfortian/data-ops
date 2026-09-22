# Ticket 08：语义补齐第三批——data-development + data-service + sync（P2）

**对应需求：** 全量审计方案 M2 | **优先级：** P2 | **阻塞于：** 02,03 | **模块：** data-development + data-service + sync

**What to build：** 缺口最大的三个域收尾（开发 27、服务 31、同步 38 个写接口，合计仅个位数留痕）。判定三选一**沿用 06**。

**范围与口径边界：**
- data-development：任务/脚本的创建、编辑、删除、提交、发布等管理面动作全记。
- data-service：只记**管理面**（API 定义增删改、发布、上下架、授权）；运行面调用流水留在自有 `yak_ops_data_service_call_log`，**不进统一审计**（tickets.md 第三节"不做"）。
- sync：offline 任务定义 CRUD/启停/发布补注解；执行期已有 `OfflineAuditBridge`、`OfflineExecutionCoordinator.java:411` 手工链保持不动；realtime 侧按同口径补齐。
- datasource（14 写接口仅 1 类 3 点）顺带在本票过一遍：连接删除/改密等高敏接口只记语义不记 payload。

**验收清单**
- [ ] 三域（含 datasource 余量）写接口全过判定，05 守卫相应模块 gap 归零/入清单
- [ ] data-service 运行面调用确认**没有**被写入 `yak_audit_operation`（负向断言）
- [ ] 高敏接口（改密/凭证）metadata 无明文（复用 03 脱敏测试模式）
- [ ] baseline 联动更新
