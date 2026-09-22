# Ticket 131：四个源域挂钩——把 push 接到写路径上

> ⚠️ **改的是 modeling / semantic / metric 的代码。** 每个挂钩 ≤30 行、纯新增、不改既有接口签名；仍属**跨模块协商项**，动手前需确认范围。

**对应需求：** 内部实体进目录 | **阶段：** P1 | **模块：** modeling + semantic(×2) + metric

**What to build：** 四类内部实体在**自己的写操作成功后**登记进目录：模型（保存/发布点）、标准字段、业务域、指标。挂钩只做"取投影 + 调 api"，不含任何目录逻辑。

**Blocked by：** 130

**现成的同侧先例**（说明这条链路在本仓库是常态，不是新发明）：`ModelingLineageController.java:56/80` 已在保存/发布时调 `registerModel(...)`；analysis/dashboard/dataset 三个 `*LineageSynchronizer.syncCurrent(detail)` 是同形写法。本票只是把"登记进血缘"换成"登记进目录"。

**验收清单**
- [ ] 四个挂钩各 ≤30 行，**只 import `io.yak.ops.business.metadata.api`**（plan §0.3），不得 import metadata 内部包
- [ ] 每个挂钩：从本域 service 取投影 → 组 `RegisterCommand` → 事务提交后 `register(...)`；`catch` 到任何异常**只写重试队列、绝不再抛**（plan §9 T20）
- [ ] **`sourceHash` 算法留在源域**（如 `ModelSourceHash.of(po)`），push 与 `cursorList` 都调它（plan §3.2b 硬约束 2）
- [ ] `assetKey` **由源域交出**，沿用既有生成器（`modeling:model:{id}` / `semantic:field:{id}` / `metric:{id}`），元数据不替它拼键（硬约束 4）
- [ ] 删除实体时调 `unregister(typeName, sourceId)`（软删，不物理删）
- [ ] 目录里**绝不出现源域业务内容**：模型的列定义、指标公式、标准字段的字典项一律不进 `md_attributes`（§1.3 投影线；§10 测试 10 锁死）
- [ ] 四个源模块既有测试全绿；grep 守护：挂钩文件内不出现 `yak_metadata_asset` 直写（plan §0.4）

**验收口径**：§8「P1 写时登记」第 1、2 条——四类各至少 1 条经**保存动作**进目录，且改完 `displayName` 不跑任何任务即可搜到。
