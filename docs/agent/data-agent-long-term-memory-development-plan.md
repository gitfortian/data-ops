# 数据智能体长期记忆与自进化 · 开发计划（v1.0）

> 日期：2026-08-28
> 定位：长期记忆能力的**执行排期**——任务拆解、PD 估算、每期可证伪验收标准。
> 设计依据：[data-agent-long-term-memory-design.md](data-agent-long-term-memory-design.md)（功能规划与架构裁决，下称"设计稿"）。
> 与总计划的关系：本计划是 [data-agent-development-plan.md](data-agent-development-plan.md)（v1.2）的记忆线补充，**排期冲突时以 v1.2 为准**；本文档只定义记忆线自身的期次与顺序。

---

## 一、执行前提与插入时点

### 1.1 硬前提（全部已满足）

| 前提 | 状态 | 证据 |
| --- | --- | --- |
| Middleware 管道就位（onSystemPrompt / onActing / onModelCall） | ✅ | `AgentRuntime.doAssemble()` 四件套已注册，Commit B 已合入 |
| 轮终态钩子就位（写入触发点） | ✅ | `AgentTurnExecutor.finishCompleted()` 已区分完成/失败/取消三终态 |
| 调用级计量就位（记忆成本可观测） | ✅ | `LlmResilienceMiddleware` 落 `KIND_LLM_CALL`，step 表可归因 |
| HITL 反馈通道就位（GLOSSARY 记忆源） | ✅ | `AgentRuntime.resume()` 以 ToolResultMessage 续跑，`AgentResumeIntegrationTest` 闭环 |
| 定时任务基建就位（巩固任务） | ✅ | `AgentSchedulingConfiguration` 已启用（PI-001 修复项） |
| 压缩与记忆互补关系确认 | ✅ | 提取管线读轮次事件与回答，不依赖被压缩历史 |

### 1.2 插入时点建议（对齐 v1.2 主线）

```text
v1.2 主线：Phase 1「稳」→ Phase 2「续」→ Phase 3「磨」→ Phase 4「验」……
记忆线：                M1 → M2（可与 Phase 2 后端并行）
                                    M3（Phase 3 期间或之后）
                                              M4（Phase 4 之后，复用其验收实验方法）
```

- **M1/M2 可与 Phase 2 后端并行**：记忆骨架是纯后端增量（新 memory 包 + 新表），不触碰前端三件套与 turn 管线改造面；但**不与 Phase 1 并行**——稳定性先行是 v1.2 铁律。
- **M3 建议在 Phase 3 后启动**：口径/教训/模板三类业务记忆的质量依赖真实使用流量的积累，提前做是空转。
- **M4 必须在 Phase 4 之后**：NM-3/验收实验复用 Phase 4 建立的对照实验方法与计量数据，避免重复搭实验基建。

排序原则与 v1.2 一致：**先稳后强、先闭环后深化**——M1+M2 交付后记忆闭环（L1）即已成立，后续期次是增强而非返工。

---

## 二、期次总览

```text
M1「骨架」 记忆表 + 写读闭环 + feature flag + 计量接线        （后端 ≈3 PD）
M2「巩固」 提取质量调优 + 巩固任务 + 衰减淘汰                  （后端 ≈3 PD）
M3「业务化」HITL 口径沉淀 + 教训记忆 + 查询模板 + 晋升候选      （后端 ≈3 PD）
M4「治理与验证」管理 API + 前端管理页 + 对照验收实验            （后端 ≈2 PD + 前端 ≈2 PD + 业务 ≈1 PD）
────────────────────────────────────────────────────────────
合计 ≈ 后端 11 PD + 前端 2 PD + 业务 1 PD = 14 PD
```

每期独立可交付、可回退；`yak.agent.memory.enabled=false` 时全部路径关闭，行为与今日字节级一致。

---

## 三、各期详细范围

### M1「骨架」— 写读闭环成立（后端 ≈3 PD）

目标：L1 记得住的最小闭环——有记忆产生、有记忆被注入、全程可观测。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | Flyway 迁移 `yak_agent_memory` | 位置：`data-ops-business-agent/src/main/resources/db/migration/yak-agent/V8__agent_memory.sql`（该目录当前最新为 V7）；字段按设计稿 §3.3/§七：scope/scope_key/memory_type/content/summary/keywords/confidence/source_turn_id/source_session_id/layer/hit_count/last_hit_at/status/merged_into/时间戳；索引 `(scope, scope_key, status, memory_type)`；keywords 前缀索引 |
| 2 | `memory` 包骨架 + 仓储 | PO/Mapper/Repository/Adapter 对齐现有分层纪律（REVIEW 约束：dao 不外泄）；`MemoryQuery` 检索查询（LIKE on keywords/content） |
| 3 | `MemoryFlushService`（异步提取） | 轮终态 enqueue → 独立线程池 → 闸门（实质内容/THROTTLED 5min/开关）→ LLM 提取调用（数据域 prompt，设计稿 §4.2）→ JSON 解析（失败放弃）→ 内容校验 → LEDGER 入库；成功/失败/跳过全落 `KIND_MEMORY_FLUSH` step |
| 4 | `LongTermMemoryPromptMiddleware` | 注册进 `SystemPromptAssemblyMiddleware` 管道（order 靠后）；查询构造（本轮+近 1 条用户历史）→ USER/PROJECT/GLOBAL 三层检索 → 排序截断（≤8 条/≤2000 字符）→ 段落模板注入；命中异步更新 hit_count/last_hit_at；`KIND_MEMORY_RECALL` step（hit 数/字符数/延迟） |
| 5 | 配置与开关 | `AgentProperties.Memory`（enabled/flush 间隔/注入预算/检索条数/decay 半衰期/置信度门槛）；feature flag 贯穿写读两侧 |
| 6 | 契约与守护 | REQUIREMENTS/DOMAIN/ARCHITECTURE 三件套补录记忆能力条目；架构守护测试（memory 包依赖边界：middleware→service→repository 单向）；SDK 白名单确认（middleware 内模型调用走框架 seam） |

验收标准（可证伪）：

- [ ] 同一会话间隔 >5min 的两轮含口径澄清 → LEDGER 出现 ≥1 条 GLOSSARY/PREFERENCE 记忆，step 表有对应 FLUSH 行（含 token 计量）；
- [ ] 新会话首问命中该记忆 → system prompt 含记忆段落、`KIND_MEMORY_RECALL` step 记录 hit=1、回答未重复反问；
- [ ] `yak.agent.memory.enabled=false` → 无 FLUSH/RECALL step、无记忆表写入、轮次行为与关闭前一致；
- [ ] 注入 12 条候选 → 实际注入 ≤8 条且总字符 ≤2000，截断记录可查；
- [ ] 提取 LLM 故意返回非法 JSON → 轮次无感、step 落 FLUSH 失败行、无脏数据入库。

### M2「巩固」— 两层模型与进化底盘（后端 ≈3 PD）

目标：记忆从"只进不出的日志"变成"有合并、有淘汰、有置信度的活系统"。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | `MemoryConsolidationJob` | @Scheduled 每日 + LEDGER 条数阈值触发（复用 `AgentSchedulingConfiguration`）；按 scope+type 分组，LLM 合并视野 = 组内 LEDGER + 相关 CURATED；输出 MERGED（merged_into 指针）/CONFLICT 标记/配额淘汰 |
| 2 | 检索排序升级 | `score = type_weight × confidence × decay(last_hit_at)`（半衰期默认 30 天）；GLOSSARY/LESSON 类型权重高于 FACT；decay 参数化 |
| 3 | 置信度生命周期 | 提取默认 0.6；命中正信号 +δ、轮内修正负信号 −δ（仅影响排序与巩固优先级，不自动删除）；低于门槛（默认 0.3）的条目不进注入候选 |
| 4 | 过期策略 | LEDGER 保留 14 天，未晋升即 ARCHIVED（调度扫描）；CURATED 无限期但受 per scope+type 配额约束（默认 50 条，尾部 ARCHIVED） |
| 5 | 巩固可观测 | `KIND_MEMORY_CONSOLIDATE` step/日志：合并组数、淘汰数、冲突数；巩固报告可作为 NM-5 度量数据源 |

验收标准（可证伪）：

- [ ] 造 3 条同义 LEDGER 条目 → 巩固后合成 1 条 CURATED、旧条目 status=MERGED 且 merged_into 正确；
- [ ] 构造口径冲突两条 → 合并结果带 CONFLICT 标记且注入模板不二选一（并列呈现）；
- [ ] LEDGER 条目按 retention 策略过期 → 调度扫描后 status=ARCHIVED，不再进入检索；
- [ ] 注入排序符合 type_weight × confidence × decay（单测构造三组条目断言顺序）；
- [ ] 巩固任务重复执行幂等（同输入两次运行结果一致，无重复 CURATED）。

### M3「业务化」— 业务知识深化（后端 ≈3 PD）

目标：L2 懂业务——让 Agent 的业务理解随使用单调增长。启动前提：已有真实使用流量（Phase 3 后）。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | HITL 口径沉淀 | `RequestClarificationTool` resume 反馈接入提取管线：clarify 问答对作为一等输入（当前 resume 后的 ToolResultMessage 已可从事件/StateStore 还原）；提炼为 GLOSSARY 条目并标 `source=clarification` |
| 2 | 教训记忆 | 从 step 数据提取"工具报错→参数修正→成功"轨迹 → LESSON 条目（含错误码类别与修正方式）；Guard 拒绝（GUARD_REJECTED）后的成功重试优先入库 |
| 3 | 查询模板 | 高频成功问答（按 query_log 聚类：同数据集+同维度组合出现 ≥N 次）→ TEMPLATE 条目（压缩为"问题→工具路径"骨架，不含数据） |
| 4 | 晋升候选 | 巩固层发现 ≥3 个不同用户独立陈述同口径 → PROMOTION_PENDING 条目生成；本期先落库与 API 可见，审批动作在 M4 |
| 5 | 提取质量回归 | 提取 prompt 的样本回归集（≥20 个真实轮次脱敏样本）：口径命中率、误提取率基线化，作为后续调优的对照 |

验收标准（可证伪）：

- [ ] 真机 HITL：反问"营收口径"→ 用户答"含税不含退款" → 下一个新会话问营收 → 回答按含税口径陈述且引用 GLOSSARY 记忆（RECALL step 可查）；
- [ ] 构造工具报错→修正→成功序列 → 产出 LESSON 条目且下次同类问题注入提示；
- [ ] 晋升候选生成条件（≥3 用户）单测覆盖；PROMOTION_PENDING 条目不进默认注入；
- [ ] 提取回归集基线报告产出（口径命中/误提取率有数可查）。

### M4「治理与验证」— 放开前的最后一公里（后端 ≈2 PD + 前端 ≈2 PD + 业务 ≈1 PD）

目标：可治理、可度量、有实验结论，才能对业务放开（对齐 Phase 4"验证后才放开"的同一纪律）。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | 治理 REST API | `GET /agent/v1/memory`（分页/筛选）、`PATCH`（编辑/禁用）、`DELETE`（软删）、`POST /{id}/promote`（晋升审批）；新权限码 `agent:memory:manage`；USER 记忆本人可见、PROJECT/GLOBAL 治理需权限 |
| 2 | 前端记忆管理页 | 列表（scope/type/status 筛选）+ 编辑/禁用 + 晋升审批队列；回答"参考了 N 条历史记忆"展示（消费 TURN_FINISHED 的 memoryHitCount） |
| 3 | 对照验收实验 | 25 问双通道（记忆开/关）盲评，复用 Phase 4 实验方法：NM-1 重复反问率、NM-2 热启动、NM-3 Token/步数、NM-4 抽样评审 ≥85%、NM-5 晋升率 ≥ 淘汰率 |
| 4 | 放开裁决 | 实验报告评审通过后，`memory.enabled` 由按用户灰度转默认开启；不达标项回喂记忆线 backlog（不阻塞主线） |

验收标准（可证伪）：

- [ ] 治理页完成一次全操作流：查看→编辑→禁用→晋升审批→生效；无 agent:memory:manage 权限访问 403；
- [ ] 禁用某条高频 GLOSSARY 记忆 → 下轮注入不再包含（RECALL step 可证）；
- [ ] 对照实验报告产出且 NM-1/NM-4 达标（NM-3 不劣化为底线，改善为达标）；
- [ ] 前端 lint/tsc 干净，记忆页走既有分页/权限组件纪律。

---

## 四、工作量汇总

| 期次 | 后端 | 前端 | 业务 | 合计 | 关键交付物 |
| --- | --- | --- | --- | --- | --- |
| M1 骨架 | ≈3 PD | — | — | 3 PD | 记忆表 + 写读闭环 + 计量 |
| M2 巩固 | ≈3 PD | — | — | 3 PD | 两层模型 + 衰减淘汰 |
| M3 业务化 | ≈3 PD | — | — | 3 PD | 口径/教训/模板三类业务记忆 |
| M4 治理与验证 | ≈2 PD | ≈2 PD | ≈1 PD | 5 PD | 治理界面 + 实验报告 + 放开裁决 |
| **合计** | **11 PD** | **2 PD** | **1 PD** | **14 PD** | L1→L3 全闭环 |

估算基准：与 v1.2 相同的"以代码为准"口径——middleware/轮终态/计量/调度四个接入面已就绪，M1 无基建性工作；最大不确定性在提取 prompt 质量调优（M2/M3 各预留了回归/基线任务对冲）。

---

## 五、风险登记（记忆线补充，全局风险见设计稿 §十）

| 风险 | 等级 | 对策 |
| --- | --- | --- |
| 提取 prompt 质量不达标（误提取/提取过泛） | 高 | M3#5 回归集基线化；提取是机会性的（失败放弃不影响主链路）；prompt 可独立迭代不动代码 |
| 记忆线与主线并行拉扯 | 中 | M1/M2 严格限定在新 memory 包 + 新表，不触碰 turn 管线与前端；架构守护测试锁依赖边界 |
|巩固任务误合并不同业务口径 | 中 | LLM 合并视野强制含组内全部条目 + 冲突永远标 CONFLICT 不二选一 + 治理页人工裁决 |
| 与 Phase 4 口径卡职能混淆 | 低 | 设计稿 §十已划界（本体派生 vs 用户口述历史沉淀，冲突本体优先）；M4 实验中一并验证 |
| Token 成本失控 | 低 | 注入预算硬上限 + 提取 THROTTLED + KIND_MEMORY_* 全计量，NM-3 监控兜底 |

---

## 六、执行纪律（继承 v1.2 §七不变式，记忆线特化条目）

1. **A1 红线扩展**：记忆不是数据真相源；数字必须来自当轮工具；注入段落强制携带"仅供参考"声明与来源标注。
2. **失败也记账**：提取/检索/巩固三类 LLM 与 SQL 操作全部落 step，失败不影响主链路，Recorder 自身异常只告警。
3. **开关字节级回退**：`yak.agent.memory.enabled=false` 时写读两侧全关闭，行为与无记忆版本一致（M1 验收项覆盖）。
4. **写入有闸、共享有门**：模型无记忆写工具（防滥用）；跨用户共享只走晋升审批（防污染扩散）。
5. **只进不出的反例**：每期验收必须包含一条"淘汰/归档"路径的验证——不能只验证记忆增长。

---

## 七、文档治理

- 本文档与设计稿随做随更：每期完成在 §三 对应期次追加《执行复盘》（实际 PD、偏差、归因）；
- 逐项问题跟踪沿用 [pending-issues.md](pending-issues.md) 模式，记忆线问题以 `MEM-` 编号登记（首发：无——M1 启动时建立）；
- 设计稿 §1.2 的裁决变更（推翻审计报告 §8.3"不做跨会话记忆"）已在设计稿留痕，本计划不再重复。
