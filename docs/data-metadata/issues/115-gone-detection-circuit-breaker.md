# Ticket 115：GONE 判定与质量熔断——空集/超时绝不清库

**对应需求：** 物理元数据采集 | **阶段：** P1 | **模块：** metadata

**What to build：** 判断"某实体不再存在于源侧"并**软删**，同时保证任何异常（连接超时、方言异常、provider 抛错被 catch 成空页）都**不会清空一类实体**。这是采集侧唯一会造成不可逆损失的环节。

**Blocked by：** 114

**OM 的 `deleteStale` 六道安全全部原样采纳**（`EntityRepository.java:13378-13428`）：空 seen 集 → 零删除、scope 不存在 → 零删除、hash 比较、dryRun、单条独立事务、祖先覆盖跳过。原话依据（`:13383-13386`）：空 seen 集**无法与"连接器崩了/什么都没发现"区分**，若当成"作用域内全部过期"就会**静默删掉整个 service/database**。

**再加两道 OM 没有、我们处境需要的**
- [ ] **坍塌比例熔断**：单轮 `gone / 上轮在场数 > 30%`（阈值可配，plan §11.2 第 7 条待校准）→ 整轮标 `SUSPECT`，**不落任何 GONE**，只写 `collect_run` + 告警。理由：我们数据源少、单库表数波动大，一次超时就能让"整库消失"看起来成立
- [ ] **连续两轮缺失才 GONE**：与 asset 的 `SOURCE_GONE` 连续两个周期（默认窗口 7 天）完全对齐

**GONE 的处理**
- [ ] 软删：`gone_at` 置时间，**不物理删**、不删 `yak_md_label`（一张表消失可能只是临时下线；一个模型下架不代表它从未存在，plan §2.4.5）
- [ ] **从 lineage 图撤销该节点**，走 ticket 113 的 `LineageRegistrationApi`；`SUSPECT` 一律不撤销
- [ ] per-run `seenFqns` 集合构造与判空（空集 → 零 GONE）
- [ ] 单测必测：`seenFqns` 为空 / 中断致表数坍塌 / 数据源不可达 三例，断言"零删除"与 `SUSPECT`/`FAILED` 状态（plan §10 测试 3、§8 P1 采集）
- [ ] 验收用真库：拔掉数据源密码触发采集 → run `FAILED` 且**实体行一行未删**；构造"仅返回 3 张表"的假中断 → `SUSPECT` 且 `cnt_gone == 0`
