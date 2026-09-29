# Data Service Stage 1: Runtime Reliability Plane

Stage 1 不引入 Project/RBAC，也不把当前 JVM-local Runtime 强行改造成分布式系统。目标是先保证：**业务调用结果、审计证据、缓存代际和详情查询互不拖垮。**

> Stage 3 已把本文最初的“definition update generation”升级为持久化单调 `runtime_generation`，并加入集群限流、集群调用指标、日志生命周期和审计脱敏。最新多实例契约见 [`CLUSTER_RUNTIME.md`](CLUSTER_RUNTIME.md)。

## 1. Invocation result 与 Audit evidence 解耦

```text
DataServiceInvoker
   |
   +-- authorize / compile / query / runtime protection
   |        |
   |        v
   |   business result / original failure
   |
   +-- best-effort invocation audit
```

固定规则：

- SQL 查询已经成功时，调用日志 INSERT 失败不能把 HTTP 调用改成失败；
- SQL / 401 / 429 / 503 等原始异常已经产生时，审计失败不能替换原始异常；
- Recorder 仍保持同步 evidence 写入，以保留当前日志时序和落库语义，但 Invoker 对 Recorder 故障做最终隔离；
- 审计失败仅写内部 warning，不记录 raw API key，也不把请求参数重新输出到应用日志。

这一步解决的是 **failure isolation**，不是异步日志吞吐。

## 2. Version-safe local cache identity

Cache 仍是 Caffeine/JVM-local，但 Cache Key 不能只包含 SQL 和 bindings：

```text
cache key
  = persisted runtime namespace
  + compiled SQL
  + bindings
  + pagination identity
```

当前 persisted runtime namespace 覆盖：

- stable Data Service ID；
- sourceType/sourceRef；
- sourceRevisionId/sourceRevisionNo；
- persisted monotonic `runtime_generation`；
- 影响执行/缓存的 settings 与 cache policy shape。

因此：

```text
Revision / Generation old on Pod B
   !=
Revision / Generation new on Pod A/Pod B
```

即使 Pod B 没收到 Pod A 的本地 `invalidate()`，当它下一次从 DB 读取到新 Definition 后也不会命中旧代际缓存结果。

这不是分布式缓存；它让 local cache 在多实例部署时具备版本隔离正确性。

## 3. Service-scoped invocation log read

详情页禁止继续使用：

```text
GET all recent logs
  -> browser filter(apiId)
```

Stage 1 增加：

```text
GET /api/v1/data-service/{id}/logs?limit=50
```

Repository 使用：

```text
WHERE api_id = ?
ORDER BY create_time DESC, id DESC
LIMIT ?
```

并增加 `(api_id, create_time, id)` 索引。详情页同时改用 `GET /api/v1/data-service/{id}`，不再先加载所有 Data Service 再 `find(id)`。

## 4. Stage 1 tests

关键行为测试覆盖：

- audit repository/recorder failure 不影响成功 invocation；
- audit failure 不覆盖原始 datasource failure；
- audit failure 不覆盖原始 authorization failure；
- persisted runtime generation 变化产生不同 cache namespace/key；
- 原有 local cache reuse / circuit breaker 行为保持不变。

## 5. Stage evolution

Stage 1 当时明确不做 Project/RBAC、distributed rate limit、cluster metrics、retention/rollup、parameter masking。它们后续分别由 Stage 2 `PROJECT_GOVERNANCE.md` 与 Stage 3 `CLUSTER_RUNTIME.md` 接管。
