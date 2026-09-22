# Agent Code Style

本文件定义 Agent 的长期工程风格。它参考大型 Java 数据系统常见实践与 Yak Ops 各模块既有规范，但不复制任何单一项目的格式规则。目标是让代码长期保持：**简单、显式、角色清楚、可测试、可治理**。

需求看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，总体架构看 `ARCHITECTURE.md`，包依赖看 `DEPENDENCIES.md`。

## 1. 优先级

```text
Correctness / Safety
    > Explicit ownership
    > Simple control flow
    > Testability
    > Reuse
    > Brevity
```

不要为了少几行代码而隐藏归属校验、pending 状态机、白名单校验顺序、密钥生命周期或断连取消语义。

## 2. Package is architecture

业务职责优先进入明确子系统：

```text
controller
 conversation (+ query)
 runtime
 toolset
 catalog
 gateway
 report
 repository
 dao
 domain
 config
```

production 不创建 `service / common / helper / utils / base` 这类模糊业务桶。

一个类不知道放哪里时，先回答：它拥有哪个 truth？是什么角色？谁调用它？是否跨子系统？跨边界应该经过哪个 Facade / Gateway / Repository？

## 3. Role vocabulary

### Service

只表示稳定 Application use-case facade：

```text
AgentChatService
AgentSessionQueryService
AgentReportService
```

不要把所有 Spring Bean 都命名为 Service。

### Coordinator

负责多个内部角色的顶层编排。例如 `AgentStreamCoordinator`（SSE 生命周期）。

### Manager

拥有明确状态约束或生命周期。例如 `AgentInvocationManager`（归属校验 + pending 反问状态机）。

### Resolver / Validator / Formatter

根据已知事实解析确定结果，不偷偷改变业务状态。例如 `FieldWhitelistValidator`、`DatasetViewFormatter`。

### Reader / Query

只做 read model 聚合，不拥有 command transition。

### Gateway / Client

位于外部系统边界。dataset 契约、Python 进程等实现细节停在这里，不进入 Core Domain。

### Codec / Mapper / Adapter

用于边界模型和格式转换。例如 `AgentEventCodec`（runtime 事件 -> domain 事件）、controller transport mapper。

### Middleware

推理链路的横切能力（traceId、动态提示词），不承载业务决策。

### Repository

表示业务持久化 contract；实现可以适配 DAO，但不向 Application 暴露 DAO model / Mapper。

## 4. Spring stereotype

- `@Service`：仅三个稳定 Application Facade；
- `@Component`：内部专业角色；
- `@Repository`：Persistence adapter；
- `@Configuration`：wiring/config only（runtime 内的 StateStore 装配类除外，其存在理由见 ARCHITECTURE Runtime Subsystem）;
- Core Domain：不使用 Spring annotation。

默认 constructor injection。不要用 static locator、手工 ApplicationContext lookup、reflection 或全局可变状态绕过显式依赖图。

## 5. Method design

高风险流程要让顺序直接可读。例如 Start：

```text
validate ownership
  -> build invocation context
  -> open SSE + heartbeat
  -> subscribe runtime event stream
  -> publish events
  -> complete / dispose on disconnect
```

例如 run_dataset_query 工具：

```text
validate field whitelist
  -> clamp limit
  -> execute via gateway
  -> record query_log (success/failure)
  -> format evidence for LLM
```

不要把关键步骤隐藏进泛化的 `execute / handle / process`。推荐 guard clause、early return；事务内只做需要线性化的持久化工作，外部调用（模型、数据集查询、Python 进程）不得进入长事务。

## 6. State and lifecycle

必须持续保持：

```text
Session != ReasoningTurn != Evidence != Report
LLM Output != Trusted Input
pending(反问) != error(失败)
断连取消 != 会话删除
报告独立生命周期 != 会话级联删除
```

如果现有模型表达不了需求，先标记 **Domain Gap**。不要用新的 mode/type/flag 字段绕过领域模型。

## 7. External uncertainty

对 LLM、dataset 查询、Python 进程等外部系统：

- 无证据就不猜：模型结论必须能对应到 Evidence；
- 模型输出格式异常按结构化错误回喂自纠，不静默重试无限循环；
- Python 进程超时强杀后返回明确超时事实，不伪造分析结果；
- 断连/取消后不猜测执行结果，已落库的事实为准；
- 不能为了 UI 好看而伪造终态。

错误处理要保护证据链（query_log），而不是只追求快速返回。

## 8. Sensitive configuration

模型 API 密钥只在 runtime 模型装配边界短暂存在：不入库、不写入日志、不出现在 SSE 事件与异常文本。日志与诊断输出涉及用户问题时注意脱敏边界；数据集名、字段名属于业务元数据可以出现。

## 9. Logging

使用参数化日志，并携带稳定业务定位信息：sessionId、userId、datasetId、queryId、toolName（已知时）。日志不能成为业务状态 truth。

流式链路中每个事件的 debug 日志不得包含完整提示词与密钥；thinking/text 增量只在 debug 级别输出截断摘要。

## 10. Null / Optional / collections

- `Optional` 主要用于返回值；
- collection 返回空集合，不返回 null；
- nullable 字段必须有单一明确语义；
- pending 反问用明确的领域状态表达，不用 null 偷偷表达。

## 11. Types and generics

- 不使用 raw type；避免 wildcard import；
- 跨边界优先明确 record/value object；
- 不用 `Map<String, Object>` 承载长期业务 contract（持久化投影 JSON 与 SSE 帧 payload 属于显式声明的例外，必须有专用 codec）；
- 外部动态响应可以临时使用 JsonNode，但业务判断尽快转为明确语义。

## 12. Comments

注释解释 **why / invariant / danger**，不要复述代码。校验顺序、pending 状态机、取消传播点、兼容债务、非显然 fallback 必须解释原因。

## 13. Tests

### Behavior safety tests

保护：会话归属校验、HITL pending 单飞与 toolCallId 匹配、字段白名单校验与自纠回喂、limit 上限截断、query_log 成功失败都留痕、Python 并发信号量与超时强杀、断连 dispose 上游、OFFLINE 数据集拒绝。

### Architecture tests

保护：stable `@Service` facade、internal role stereotype、package dependency matrix、no cycle、SDK 白名单（agentscope/reactor/dataset）、Core Domain purity、no broad business bucket、Repository/DAO/Gateway boundary。

重构 PR 不能只保证行为测试，也必须保证 architecture guards。

## 14. Change size

优先一个 PR 一个主要边界或一个行为关注点。不要把 package move、DB schema、REST breaking change、prompt 语义变化和新工具功能混成一次“顺手重构”。

## 15. Review questions

提交前至少回答：

1. 这个类属于哪个 subsystem，角色名准确吗？
2. 它拥有哪个 truth，是否出现第二个 owner？
3. 新 dependency 是否符合 `DEPENDENCIES.md`？是否触碰 SDK 白名单？
4. 有没有把内部角色暴露成新的 Application API？
5. 归属校验和白名单校验的先后顺序有没有被“优化”掉？
6. 有没有把 pending 反问误当失败、把断连误当会话终止？
7. 有没有让 Gateway/DAO/Repository 反向依赖 Application？
8. 有没有扩大敏感配置的生命周期或输出范围？
9. 哪个 behavior test 和 architecture test 保护这次修改？

如果答案依赖“大家约定不要这么用”，说明规则还不够可执行。
