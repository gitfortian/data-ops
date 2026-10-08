# A6.1 Offline / Realtime Runtime 与 SPI 适配边界评估

> 关联 [架构治理 Issue #368](https://github.com/gitfortian/data-ops/issues/368)。
> **本轮不引入 YakFlow、不替换 Link-Up/Flink、不增建统一执行引擎接口。** 先冻结数据平面适配的真实契约，再由证据决定是否需要新的 Adapter。

## 实际代码事实（均来自当前 data-ops，而不是旧项目或上游实现）

| 维度 | Offline / Link-Up | Realtime / Flink CDC |
|---|---|---|
| 对外抽象 | `OfflineSyncConnectorAdapter`、`OfflineSyncConnectorAdapterRegistry`、`LinkUpJobSpecFactory` | `RealtimeEngineGateway`、`FlinkCdcEngineGateway`、`RecoverableRealtimeEngineGateway` |
| Source / Sink | `Role.SOURCE / SINK`；适配器 `supports/requiresDataSource/build/resolveForExecution` | `RealtimeDeployRequest` 的 source/sink 凭据绑定；显式 `ComputeEnvironmentSnapshot` |
| 定义版本 | 逻辑 `link-up/v1` `BatchSyncJob`；构建 JobSpec 时仅保留 DataSource Reference，执行时才解析凭据 | YAML pipeline；Project-local Idempotency-Key，经 `Project ID + key` 生成全局确定性的 runtime job name |
| 多表/调度 | `GUIDE_SINGLE`、`GUIDE_MULTI`；只有两端 adapter 均支持才用 `NATIVE_MULTI`，否则 `FAN_OUT` | Flink REST job status 与部署控制；独立生命周期协调 |
| 游标/恢复 | `OfflineCursorGateway.advanceAfterSucceededBatch` 及 STALE / ALREADY_ADVANCED 等幂等进度结果 | `RealtimeRuntimeIdentity` + identity store；`RealtimeRuntimeStateReconciler` 遇 UNKNOWN / CONFLICT 时需保护不可确定的外部状态 |
| 能力声明 | Connector `supportsNativeMultiTable` 默认 false；Registry 同 role 多匹配立即失败、未知适配器保持 passthrough | `FlinkCdcEngineGateway.capabilities` 声明 deliverySemantics=at-least-once、checkpoint API/metrics API 支持，但 checkpoint/restart **配置管理为 false** |
| 安全隔离 | BuildContext 没有 DataSourceDefinition，只在 ExecutionContext 注入执行期凭据 | `RecoverableRealtimeEngineGateway` 使用已验证的 `CurrentProject.requireProjectId()` 命名空间，绑定 identity 后再部署 |

以上是接口/实现静态源码事实，并不代表替代引擎已达到功能等价或生产验收。

## 本 PR 的可执行回归测试

1. **Offline Connector Adapter（既有 Registry 测试扩展）**
   - 新插件即使支持某 connector，也不能凭默认值开启原生多表执行；只有明确实现了 `supportsNativeMultiTable` 的源、目标适配才能走 NATIVE_MULTI。
   - `BuildContext` 的表清单为不可变快照，不能把 DataSourceDefinition 凭据下放到逻辑定义期。
2. **Realtime Recoverable Gateway（既有 Gateway 测试扩展）**
   - 缺少已验证 ProjectContext 时部署必须在写 runtime identity 和调用 Flink Gateway **之前**失败，不允许外部提交。
   - 不同 Project 即使复用相同的客户端幂等键，也必须生成不同 runtime identity，并分别按本 Project 绑定。
3. 这两组真实 Java JUnit 由 Architecture Checks 对应模块 CI 运行。此次 **仅修改测试、文档，不改生产实现、状态枚举或实际引擎部署逻辑**。

## 接下来允许替换引擎的准入条件（未达到前必须继续保留 Link-Up/Flink）

- Offline：至少有 Source/Sink 支持矩阵、单表/原生多表/FAN_OUT 一致性、执行期凭据脱敏、Cursor/Batch Commit 幂等、失败重试、历史 JobSpec 兼容的测试和 Demo 数据。
- Realtime：提供 `ComputeEnvironmentSnapshot` 与 DefinitionVersion/快照不漂移证明；Flink runtime identity 多 Project 隔离、提交超时后的 UNKNOWN/CONFLICT 补偿、状态巡检、外部终止、恢复与 Checkpoint 能力声明均需等价且可回归。
- SPI：配置/schema/version 稳定、插件重复注册冲突明确报错、健康检查与能力发现可依赖、错误不会被映射成虚假成功。
- 实际数据面运行：必须有生产代表性的 MySQL/PostgreSQL + Link-Up/Flink 环境 E2E 和真实截图/API/测试证据；本次静态/JUnit 检查**不能取代**上述验证。

## 刻意不做的事

- 不复制上游 Service / DTO / Schema / Controller，不硬编码 YakFlow 的具体实现为 data-ops 的新产品事实。
- 不重做 Phase3 分布式能力，不迁移或重命名已有 Link-Up/Flink 任务配置与历史事件。
- 不新增无实际消费者的 BaseEngine、Runtime Facade、统一 SPI 或多余配置项。
- 不改变当前 Realtime 的 `UNKNOWN`、`CONFLICT`、环境版本、Project Header、幂等键与 Credential Binding。

## 验收

```bash
bash ./mvnw -B -ntp -pl data-ops-business/data-ops-business-sync/data-ops-business-sync-offline,data-ops-business/data-ops-business-sync/data-ops-business-sync-realtime -am test
```

- [ ] Offline Native Multi/Logical Snapshot 新用例通过
- [ ] Realtime Project-less Deployment/Cross-project Identity 新用例通过
- [ ] 全仓 Architecture Checks、Product Guard 完成
- [ ] Draft、`do-not-merge`、`architecture-refactor`，仅提交不合并

未来 A7 需在组合分支复验；本工作包本身不含上游引擎替换提案。
