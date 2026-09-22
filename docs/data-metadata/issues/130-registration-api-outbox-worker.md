# Ticket 130：写时登记主通道——`MetadataRegistrationApi` + outbox + 重试 worker

**对应需求：** 内部实体进目录（2026-09-19 定，plan §11.1 第 6 条） | **阶段：** P1 | **模块：** metadata

## 要造什么

`MetadataRegistrationApi.register/unregister` 这条**主通道**：源域在自己的写事务提交之后，把目录投影交给元数据；登记失败绝不向上拖垮业务保存，而是落 `yak_md_register_retry`（outbox）由 worker 重放。做到"内部实体改完即可搜"。

## 接口形状（plan §3.2b）

```java
package io.yak.ops.business.metadata.api;   // 接口在 metadata 的 api 包，源域只 import 这里
public interface MetadataRegistrationApi {
  /** 幂等 upsert：同一 (project, assetKey) 重复登记不产生第二行。 */
  void register(RegisterCommand command);
  /** 源域删除实体时同步撤销目录行（软删，不物理删，§2.4.5）。 */
  void unregister(String typeName, String sourceId);
}
```
`RegisterCommand` 与 `EntityProjection` **共用同一个 DTO**（`typeName/sourceId/assetKey/投影字段/sourceHash/sourceUpdatedAt`），免得长出两套投影定义。

## 三个必须（本票的主体，逐条要兑现）

1. **post-commit**：参照 `PostCommitActionQueue`（`ThreadLocal<List<Runnable>>` + `rollbackToCheckpoint`，回滚即丢弃待办）——**两个方向的反例都要拦**：事务内登记 → 业务回滚但目录留脏行；登记失败上抛 → 目录故障拖垮业务保存（后者更糟）。
2. **可重放**：`yak_md_register_retry` DDL **逐字照抄 plan §3.2c**（键在 `(project_id, type_name, asset_key, source_updated_at)` 这个**变更**上，不在 `status` 上）。worker 逐字照抄 `DevelopmentLineageWorker`：`@Scheduled(fixedDelayString = "${yak.metadata.register-retry.poll-delay-ms:1000}")` → `due(20)` → `claim` → 干活 → `complete/fail`，退避 `Math.min(3600, 1L << Math.min(12, attempts))`，`attempts ≥ 12` 转 `DEAD`。
3. **保序**：命令带 `sourceUpdatedAt`，比目录行现值旧 → **只更 `last_collect_at`、不改内容**（等价于本仓库的 `writeIfLatest`）。不抄 OM 的 `OrderedLaneExecutor`：持久化 outbox 重启不丢，而目录登记恰恰是最不该在重启时丢的那类副作用。

## 三条硬约束（不是 OM 的移植，是元数据自己的红线）

- [x] `register` 与采集**走同一个 upsert 与 GONE 判定入口**，不得复制一份判定逻辑（plan §3.2b 硬约束 3）
- [x] `sourceHash` **由源域产出**，push 与对账**同一函数**（否则两条通道互相把对方的行判成 CHANGED）——目录侧按"源域交来的值"消费，函数在源域挂钩（131）处唯一化
- [x] worker 与挂钩执行前**必须恢复项目上下文**（`projectScope.run(new ProjectContext(projectId, null), …)`）

## Blocked by

114（复用其 upsert 与指纹机制）、115（熔断路径）、128/129（类型解释规则）、134（共键）

## 验收

- [ ] §8「P1 写时登记」5 条全绿：**不跑任何任务**即可搜到；metadata 挂掉时源域保存仍成功且多一行 `PENDING`、恢复后 worker 追平；并发两次编辑不被旧覆盖新；退避与 `DEAD` 上界；`md_attributes` 不含源域业务内容
- [ ] §10 测试 14 四条用例（post-commit / 退避上界 / 乱序保序 / `uk` 并发合并 catch 成"已排队"），跑真实 MySQL 不 mock
- [ ] `DEAD` 有可观测出口，且对账每轮会把这些实体捞回（plan §3.2c 末：DEAD ≠ 永久丢失，闭环要说死）
