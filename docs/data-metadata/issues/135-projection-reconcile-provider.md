# Ticket 135：投影对账副通道——`EntityProvider` 批量 + cron

**对应需求：** 内部实体进目录（副通道） | **阶段：** P1 | **模块：** metadata（provider 实现在各源域）

**What to build：** push 之外的兜底通道：定时批量比对源域与目录，**补漏**（push 之前就已存在的历史实体、以及 `DEAD` 掉的行）、**刷 `source_hash`**、**判 GONE**。买到的是一条性质：**push 漏了目录能自己长回来**，而不是靠人发现。

**Blocked by：** 130（同一套 upsert）、115（熔断）、116（调度通道）；可与 131 并行

**`EntityProvider` 保留原形状，职责收窄为三件事**（plan §3.2b）：
```java
public interface EntityProvider {
  String typeName();
  /** 游标批量 ≤500，按主键升序；updatedAfter 非空时仅返回该时间后有变更的投影。 */
  EntityPage cursorList(EntityCursorQuery query);
  /** 单实体刷新（详情聚合/复核/重试重放用）；源已删除返回 empty。 */
  Optional<EntityProjection> refresh(String sourceId);
}
```

**验收清单**
- [ ] 一期四个 provider：`ModelEntityProvider`(modeling) / `StandardFieldEntityProvider`(semantic) / `DomainEntityProvider`(semantic) / `MetricEntityProvider`(metric)，**只读各域 service**
- [ ] 走 `collect_job`/`collect_run` 的 cron 任务，`provider_type='REGISTERED'`，**不新开第三张运行历史表**（plan §3.7）
- [ ] 调度线程恢复项目上下文；GONE 判定与熔断**复用 ticket 115 的同一条路径**
- [ ] 对账频率可比物理采集更密（plan §11.2 第 5 条：它决定故障暴露时延，廉价性由指纹增量保证）
- [ ] **220 条存量实体跑一轮全部进目录**（这是它们唯一的入场通道）：现网 `asset_type='TABLE' AND source_type='MODELING'` 11 行、`semantic:field:%` 8 行、`metric:%` 4 行，对账后**行数不增殖**、只有 `type_id` 从 NULL 变非空（plan §8 P3、§10 测试 5④）
- [ ] 人工删一条目录行 → 一轮对账补回且**不产生第二行**（按 `asset_key` 分组在场行 ≤ 1）
- [ ] provider 抛异常/返回空页 → 该类实体**零 GONE**（§10 测试 9）

**待议联动**：`EntityProvider` 与 asset 的 `AssetProvider` 是否合并为一个 SPI，**等 130/131/135 落地后再拍**（plan §11.2 第 6 条）。本票只需回答"四个批量实现放在谁的包里"。
