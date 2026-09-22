# yak-ops Agent 生产化优化方案（内部使用版）

> **状态：已被取代（superseded）**——本文的稳定性/可观测性/体验项目已并入《[data-agent-development-plan.md](data-agent-development-plan.md)》统一排期（含本体语义线合并与工作量修正），执行以新文档为准；本文保留为历史设计论证，不再单独维护。
>
> 日期：2026-08-27
> 适用对象：`yak-ops-business-agent`（AgentScope ReActAgent · NL2Query · SSE 对话）+ `yak-ops-ui` ai-agent 页面
> 背景：内部使用，单模型 OpenAI 协议，不涉及计费与多租户。聚焦**可观测性**与**稳定性**。
> 依据：AutoGPT 平台蒸馏、AutoGPT 前端体验蒸馏、Open WebUI 全栈蒸馏（详见 `knowledge-docs/agent/`）

---

## 一、现状诊断

| 维度 | 当前状态 | 目标态 | 差距 |
| --- | --- | --- | --- |
| 执行架构 | SSE 同步请求内完成全部推理，断线即丢 | 提交即返回 turn_id，后台执行，事件流可重放 | 提交/执行分离 |
| 步骤记录 | `yak_agent_step` 已落地，整轮 Token 统计 | 每次 LLM 调用独立计量（tokens/latency/retry） | 调用级粒度不足 |
| LLM 工程 | 300s 整轮超时，错误码分类 | 单次调用超时 + 指数退避重试 + 结构化输出闭环 | 缺少重试与输出容错 |
| 消息模型 | 树状 `yak_agent_message` 双写已到位 | 数据结构支撑编辑/fork/regenerate | 后端已到位，前端未消费 |
| 可观测 | step 表 + 查询审计 + Token 汇总 | 每次 LLM 调用可追溯 + Token 预算 + 日志截断 | 调用级计量与预算缺失 |
| 前端流式 | SSE 直驱动 setState | 30ms throttle + 词级平滑 + 连接状态机 | 体验层全部待建 |
| 错误体验 | `e.message` 直出 | 错误码→文案→动作映射表 + 四级分发 | 无映射、无分级 |
| 配置管理 | `@ConfigurationProperties` 启动时加载 | 关键参数运行时可调，不重启 | 无热更新 |

---

## 二、剔除项（与内部使用场景无关）

| 原方案项 | 剔除原因 |
| --- | --- |
| 模型目录 as-code（多模型 slug/价格） | 内部单模型，OpenAI 协议即可 |
| 模型预设表（业务域 prompt 预设） | 内部使用，无多业务域差异化需求 |
| 计费核算（cost_usd / credit / PlatformCostEntry） | 内部不计费 |
| 多租户 org/team 层级 | 现有角色 + 权限够用 |
| 用户上传 Python 插件 exec | 安全边界问题，扩展走 MCP Server 路线 |

---

## 三、三层递进路线（稳定性 > 可观测性 > 体验）

```text
稳定性       提交/执行分离 · LLM 重试与输出闭环 · 断线续播 · 配置热更 · 协作取消
─────────── ──────────────────────────────────────────────────────────────────
可观测性     LLM 调用级计量 · Token 预算 · 日志截断器 · semantic_ref 关联
─────────── ──────────────────────────────────────────────────────────────────
体验层       SSE 渲染管线 · 连接状态机 · 错误映射 · Loading 分层 · 消息树 UI · 进度诚实化
```

原则：**稳定性是地基**，地不牢上面全白搭；可观测性紧随其后，让每一步都有据可查；体验层在前两层稳固后逐步补齐。

---

## 四、稳定性详细设计

### 4.1 提交/执行分离（P0）

**现状**：`POST /chat/stream` 同步完成全部推理与工具调用，断线即丢，无法重放。

**目标**：

- `POST /chat/turns` 仅做验证 + 写 DB（QUEUED 态），立即返回 `turn_id`；
- 后台线程池消费（DB 行作队列：QUEUED → 条件 UPDATE 抢占才执行，天然幂等）；
- SSE 端点改为按 `turn_id` 订阅事件流，支持断线后 `Last-Event-ID` 增量拉取；
- 取消改为协作式：CANCELLED 状态位 + 执行循环每步检查。

**事件通道**：

- 现阶段：DB 事件表 + `Last-Event-ID` 增量拉取（单体够用）；
- 远期（有 Redis 后）：升级 Redis Stream 全量重放 + 前端去重。

**验收标准**：

- kill -9 执行进程 → 重启后该 turn 从最后一个 COMPLETED 步骤之后继续；
- SSE 断开 60s 内重连续播无重复无丢失。

### 4.2 LLM 单次调用超时 + 指数退避重试（P0）

**现状**：300s 整轮超时已有，但单次 LLM 调用无独立超时，5xx/网络错误无重试。

**目标**：

1. 单次 LLM 调用 120s 硬超时（`CompletableFuture.orTimeout`），**TimeoutError 不重试**；
2. 错误分类：
   - 401/403/429 = USER_ERROR → 不重试、不上报，直接返回用户可读错误；
   - 5xx/网络错误 → 指数退避 + jitter 重试 ≤3 次，每次尝试落 step 记录；
3. 所有重试尝试的 Token 全部累计。

**验收标准**：

- 注入 500ms 延迟的 mock LLM → 无请求悬挂；
- 构造 500 响应 → 自动重试且 step 表有 ≥2 条尝试记录。

### 4.3 结构化输出闭环（P0）

**现状**：依赖模型自觉返回 JSON，解析失败无兜底。

**目标**：

1. 要求模型把 JSON 包进一次性随机标签 `<json_output id="{token_hex}">`；
2. 三级解析：纯 JSON → 花括号边界匹配 → 标签切片；
3. 校验失败把坏回复 + 错误信息回喂重试 ≤2 次；
4. 全部尝试计入 step 记录（失败也记账）。

**验收标准**：

- 构造非法 JSON 回复 → 自动回喂重试且 step 表有 ≥2 条尝试记录。

### 4.4 SSE 断线续播（P1，依赖 4.1）

**目标**：

- DB 事件表持久化全部 SSE 帧，帧带递增 `event_id`；
- 客户端重连时携带 `Last-Event-ID`，服务端从该 ID 之后增量推送；
- 60s 内重连续播无重复无丢失；
- 超时未重连则 turn 仍在后台执行，客户端可通过 REST 拉取最终结果。

### 4.5 配置热更新（P1）

**目标**：

- `yak_config` per-key 表（key / value JSON / updated_at）；
- env 只作种子（seed 只补缺不改值）；
- 运行路径现查 + `get_many` 批量；
- 首批纳管：`llm.timeout`、`llm.max-iters`、`semantic.default.limit`、审批策略开关；
- **严禁模块级静态缓存配置**。

### 4.6 协作式取消增强（P2，依赖 4.1）

**现状**：`AgentTurnRegistry` 有 dispose + 互斥标记释放，但非持久化。

**目标**：配合提交/执行分离，CANCELLED 状态位写入 DB，执行循环每步检查，级联停子执行，轮询 DB 等终态而非硬杀线程。

---

## 五、可观测性详细设计

### 5.1 步骤级执行记录（✅ 已完成）

`yak_agent_step` 表已落地：

- 步骤类型：`LLM_CALL` / `TOOL_CALL` / `GUARD` / `TURN_SUMMARY`；
- 错误分类：`TIMEOUT` / `USER_ERROR` / `PROVIDER_ERROR` / `GUARD_REJECTED`；
- 失败也记账，文本截断 8000 字符，Token 汇总。

### 5.2 LLM 调用级计量落库（P0）

**现状**：整轮 Token 统计已有（`TURN_SUMMARY.totalTokens`），但每次 LLM 调用无独立记录。

**目标**：每次 LLM 调用恰好落一条 step 记录，包含：

| 字段 | 说明 |
| --- | --- |
| promptTokens | 输入 Token 数 |
| completionTokens | 输出 Token 数 |
| latencyMs | 本次调用耗时 |
| retryCount | 重试次数 |
| status | COMPLETED / FAILED |
| error_code | 失败时的分类错误码 |

**价值**：Token 优化、验收实验（D4 指标）、Prompt 回归测试的数据基础。

### 5.3 Token 预算（P1）

**目标**：

- 发送前估算 Token（jtokkit 或字符数近似 `chars / 3.5`）；
- 超过模型上下文窗口 → 压缩对话历史到半窗（保留 system + 最近 N 轮）；
- 防止上下文膨胀导致推理变慢/失败。

### 5.4 应用日志截断器（P1）

**现状**：step 表已有 8000 字符截断，但应用日志层无统一截断。

**目标**：

- 统一 `TruncatedLogger`：LLM 请求/响应体、工具输入输出超 1000 字符头尾截断；
- 截断标记 `[TRUNCATED orig=N]` 便于排查；
- 防止大量 Token 内容撑爆日志存储。

### 5.5 semantic_ref 关联（P2）

**目标**：`yak_agent_query_log` 增加 `semantic_ref` 字段记录 SemanticQuery 摘要 + 关联 `step_id`，让语义查询也能完整溯源（意图→检索→口径→结果）。

### 5.6 Prometheus 指标暴露（P2，远期）

先把计量数据落库（5.1 + 5.2 已做），有真实流量后再接入。指标规划：

- `agent_llm_calls_total{status}` — LLM 调用计数；
- `agent_llm_duration_seconds` — 调用耗时直方图；
- `agent_tool_calls_total{tool,status}` — 工具调用计数；
- `agent_tokens_total{type="prompt|completion"}` — Token 消耗；
- `agent_active_turns` — 当前活跃推理数。

反高基数纪律：**不带 user_id / session_id**。

---

## 六、体验层详细设计（yak-ops-ui）

### 6.1 SSE 渲染管线（P1）

- 30ms throttle + 词级平滑 TransformStream；
- **SSE 不直接 setState**；
- 成本极低观感差距巨大。

### 6.2 连接状态机（P1，依赖 4.4）

- 五态：`connecting` / `connected` / `restoring` / `reconnecting` / `exhausted`；
- 60s 活动看门狗（心跳超时自动重连）；
- `visibilitychange` 事件重同步（切回标签页时检查连接状态）；
- 消灭"卡死在转圈"。

### 6.3 错误码→文案→动作映射表（P1）

- 四级分发：toast / inline 卡 / ErrorBoundary / 路由级；
- 每个错误码对应标题 + 描述 + 下一步动作按钮；
- 未知码兜底文案；
- 同源去重（同一错误只出现一次）。

### 6.4 Loading 分层（P2）

- 无内容期：拟人短语轮换 + 20s 后显示真实耗时；
- 有内容后：光标竖条；
- 空闲时任务降级 pending。

### 6.5 进度诚实化（P2）

- TurnStatsBar：本回合工具数 / 累计耗时，流结束冻结；
- 依赖 5.2 每次 LLM 调用计量。

### 6.6 消息树 UI（P2）

- 分支切换 `< n/m >` 导航器；
- regenerate 带"修改建议输入框"；
- hover-reveal 操作钮；
- 后端消息树结构已到位，前端补消费层。

### 6.7 审批暂停续跑升级（P2）

**现状**：HITL 反问已有（`RequestClarificationTool` + `ToolSuspendException` + resume）。

**升级**：SQL 执行类动作前可配置人工确认 → 中间态 + 恢复元数据落库 → 断连后批准仍可持续跑 → 幂等（重复 resolve 返回 409）。

### 6.8 低优先级项（P3）

| 项 | 说明 |
| --- | --- |
| 报告 Artifacts 化 | 右侧 Resizable 面板 + 版本数组，当前报告分页+详情够用 |
| 引用三层呈现 | 二期知识库接入时启用，当前无 RAG 场景 |
| 排队与草稿 | 内部使用人数有限，锦上添花 |
| IME 兼容 | isComposing + Safari 时间窗，用户量小可后补 |

---

## 七、测试与质量基建

| # | 项 | 优先级 | 说明 |
| --- | --- | --- | --- |
| 1 | **ToolSpec 内嵌示例 + CI 全量遍历** | P2 | 每个工具定义 example_input/output/mock，CI 参数化遍历全部工具 |
| 2 | **SSE 事件序列测试脚手架** | P2 | 给定事件序列断言最终渲染状态 |
| 3 | **k6 压测三场景** | P3 | POST /chat/turns 并发提交、SSE 长流稳定性、混合负载；验收线 P95<2s、成功率>95% |

---

## 八、实施路线与工作量

```text
Phase 1（P0，约 4-5 PD）—— "稳 + 看得清"
├── 4.1 提交/执行分离（DB 队列版）
├── 4.2 LLM 单次超时 + 指数退避重试
├── 4.3 结构化输出闭环（随机标签 + 回喂重试）
└── 5.2 每次 LLM 调用独立计量落库

Phase 2（P1，约 4-5 PD）—— "断了能续 + 体验兜底"
├── 4.4 SSE 断线续播（依赖 4.1）
├── 4.5 配置热更新
├── 5.3 Token 预算估算
├── 5.4 应用日志截断器
├── 6.1 SSE 渲染管线（30ms throttle）
├── 6.2 连接状态机
└── 6.3 错误码映射表

Phase 3（P2，按需迭代）—— "体验打磨"
├── 4.6 协作式取消增强
├── 5.5 semantic_ref 关联
├── 6.4-6.6 Loading / 进度 / 消息树 UI
├── 6.7 审批暂停续跑升级
└── 测试基建（ToolSpec 内嵌示例 + SSE 事件序列测试）
```

---

## 九、验收标准（每阶段可证伪）

### Phase 1

- [ ] 注入 500ms 延迟的 mock LLM → 无请求悬挂（4.2 单次超时生效）
- [ ] 构造 500 响应 → 自动重试且 step 表有 ≥2 条尝试记录（4.2 指数退避）
- [ ] 构造非法 JSON 回复 → 自动回喂重试且 step 表有 ≥2 条尝试记录（4.3 结构化闭环）
- [ ] 任意回答可由 step 表重建完整证据链，包含每次 LLM 调用的 tokens/latency（5.2 调用级计量）
- [ ] `POST /chat/turns` 返回 turn_id 后立即结束，后台异步执行（4.1 提交/执行分离）

### Phase 2

- [ ] SSE 断开 60s 内重连续播无重复无丢失（4.4 断线续播）
- [ ] 运行时修改 `llm.timeout` 配置 → 下次推理立即生效，不重启（4.5 热更新）
- [ ] 前端无"卡死在转圈"现象，切标签页回来连接自动恢复（6.2 连接状态机）
- [ ] 所有错误展示为结构化文案 + 动作按钮，无裸 `e.message`（6.3 错误映射）

### Phase 3

- [ ] 同一问题触发需审批动作 → SSE 正常收尾、批准接口续跑、重复批准返回 409（6.7）
- [ ] 消息树分支可导航，regenerate 可带修改建议（6.6）
- [ ] 全程：任意回答可由 step 表重建完整证据链（意图→检索→口径→结果），满足监管级"为什么这么做"

---

## 十、明确不做

1. **不引入 DAG 图引擎/可视化编排** — 那是 AutoGPT 的产品形态，不是我们的；
2. **不上 RabbitMQ/Kafka** — DB 队列版支撑当前规模，出现真实瓶颈再升级；
3. **不做多模型目录/价格管理** — 内部单模型 OpenAI 协议；
4. **不做计费核算** — 内部使用不计费；
5. **不做多租户 org/team** — 现有权限够用；
6. **不做用户上传 Python 插件 exec** — 安全边界问题，扩展走 MCP Server；
7. **Langfuse/Prometheus 等 APM 接入推迟** — 先把计量数据落库，有真实流量后再接入。
