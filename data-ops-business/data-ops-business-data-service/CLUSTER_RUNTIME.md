# Data Service Cluster Runtime

Stage 3 把 Data Service 从“单 JVM 运行保护”推进到“多实例语义正确”。本阶段刻意不引入新的 Redis / Gateway 基础设施：共享 Truth 使用现有 MySQL，数据面 Cache / Circuit 继续保持 node-local，并通过清晰 Port 保留未来替换空间。

## Runtime truth layers

```text
Persisted Data Service Truth
  definition / runtime policy / runtime_generation

Cluster Coordination Truth
  API Key minute windows
  invocation audit
  hourly invocation rollup

Node-local Resilience
  Caffeine result cache
  Circuit breaker state
  cache hit / circuit reject counters
```

不能把三类状态混成一个“Runtime”。

## 1. Cluster-wide API Key rate limit

`rate_limit_per_minute` 现在表示整个 Yak Ops 集群共享的硬上限，而不是“每个 Pod 各一份额度”。

```text
Pod A ─┐
Pod B ─┼─> yak_ops_data_service_rate_window
Pod C ─┘       (api_key_id, epoch_minute)
```

共享窗口采用 MySQL compare-and-set：

1. 不存在窗口时尝试插入 `request_count=1`；并发插入只允许一个成功。
2. 已存在时读取 count。
3. `count >= limit` 直接拒绝。
4. 否则执行 `UPDATE ... WHERE request_count = observedCount`。
5. CAS 冲突重新读取，直到成功或达到有界重试上限。

这保证多个实例并发调用同一 Key 时，共享同一份 RPM 配额。

共享限流存储异常时选择 **fail closed**：不能因为协调存储不可用而绕过用户配置的访问上限。

Key rotate / disable / delete 会删除该 Key 的共享窗口；历史过期窗口由维护任务统一清理，不进入调用热路径。

## 2. Version-safe node-local cache

Stage 3 不把结果 Cache 迁到 MySQL，也不伪装成分布式 Cache。Caffeine 仍然是每个实例独立的高速本地缓存。

跨节点正确性由 cache namespace 保证：

```text
api identity
+ source revision
+ persisted runtime_generation
+ maxRows / pagination
+ cache policy shape
+ compiled SQL
+ bindings
```

`runtime_generation` 是持久化单调代际，替代 Stage 1 使用 `updateTime` 作为 generation 的做法，避免时间戳精度碰撞。

同时把影响执行/缓存语义的 settings / policy 纳入 namespace。即便两个并发管理请求碰巧从同一 generation 开始写，也不会让不同 Runtime shape 共享缓存结果。

因此：

- 其他节点没有收到主动 invalidate，也不能命中新代际下的旧结果；
- 本地旧 entry 可以自然等 TTL/LRU 回收，不影响正确性；
- 未来接 Redis 时可以替换 cache port，而不用改变 Invocation contract。

## 3. Cluster invocation metrics vs local resilience

Runtime 状态接口现在明确分成两类指标。

### Cluster invocation evidence

来自所有实例共同写入的持久化调用证据：

- total calls
- success calls
- failure calls
- success rate
- average duration
- recent bounded P95 sample
- last success / failure

这些指标不再依赖请求恰好落在哪一个 Pod。

### Node-local resilience evidence

仍然只表示当前实例：

- cache entries
- cache hits / hit rate
- circuit state / open-until
- circuit rejected

API 返回 `metricsScope=CLUSTER_INVOCATION_LOCAL_RESILIENCE`，明确提醒调用方不能把本地 Cache/Circuit 当成集群状态。

P95 当前使用最近 raw audit 的有界 duration sample，不宣称是长期 rollup 的精确全历史 P95。

## 4. Invocation evidence lifecycle

原始调用日志不能无限增长。

默认策略：

```text
Raw invocation logs: 30 days
Hourly rollup:        365 days
```

维护过程按完整小时处理，并且每个小时在一个数据库事务内完成：

```text
INSERT ... SELECT -> hourly rollup
          +
DELETE raw rows for same hour
```

如果事务失败，两步一起回滚，不会产生“rollup 已计数、raw 还存在”的双算状态，也不会只删除原始日志而丢失聚合数据。

每轮维护最多处理有限小时桶，避免历史数据较多时长事务一次吞掉全部积压。

配置：

```text
yak.data-service.observability.raw-retention-days=30
yak.data-service.observability.rollup-retention-days=365
yak.data-service.observability.max-hourly-buckets-per-run=168
yak.data-service.observability.maintenance-cron=0 20 3 * * *
```

## 5. Audit masking

请求参数在 JSON 序列化、长度截断和数据库持久化**之前**完成脱敏。

默认规则：

| 参数名特征 | 落库行为 |
| --- | --- |
| password / passwd / pwd | `[REDACTED]` |
| secret / token / authorization | `[REDACTED]` |
| apiKey / accessKey / credential | `[REDACTED]` |
| mobile / phone / tel | 保留前 3 后 4 |
| idCard / identity / 身份证 | 保留前 6 后 4 |
| email / mail | 仅保留首字符和域名 |
| 其他字段 | 原值，继续受 4000 字符 audit 上限保护 |

API Key raw secret 本来就不进入 `AccessContext`；Stage 3 的 sanitizer 再保证普通请求参数中伪装成 key/token/password 的值也不会被写入调用日志。

## 6. Non-goals

Stage 3 不做：

- Redis shared result cache；
- distributed circuit-breaker state machine；
- API Gateway；
- 精确全历史 P95 histogram；
- 无限期调用日志保存；
- 将 raw request body 全量持久化。

当前目标是：**多实例部署时访问限制和业务指标语义正确，同时本地 resilience 明确保持本地。**

## 7. Upgrade path

未来引入 Redis / Gateway / Metrics backend 时，优先替换：

```text
DataServiceRateLimitRepository
DataServiceRuntimeMetricsRepository
LocalDataServiceRuntime cache/circuit implementation
```

Invocation、Publication、Project Governance、API Key Domain 不需要为基础设施切换重新建模。
