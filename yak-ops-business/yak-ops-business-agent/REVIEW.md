# Agent Review

> 本文件定义**如何 Review**。Reviewer / AI 是裁判，不是需求设计者；不得边 Review 边自行补需求。

## Review 前必读

按顺序读取：

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
ARCHITECTURE.md  -> 子系统、truth ownership 与角色
DEPENDENCIES.md  -> package graph、SDK 白名单与跨子系统 corridor
CODE_STYLE.md    -> 类、方法与 stereotype 的工程规范
REVIEW.md        -> 按什么标准判卷
PR diff / tests  -> 实际改了什么
```

“只是重构”不能成为绕过运行安全、领域规则或 dependency guard 的理由。

## 1. Requirement Alignment

检查代码是否符合 `REQUIREMENTS.md`：是否改变已有业务行为、引入未定义能力或越过模块边界。

出现未定义的新能力或行为变化时报告：

```text
Requirement Gap
```

不要替产品或开发者自行补需求。

## 2. Domain Compliance

重点检查：

- 消息历史 / 会话元数据 / 报告 / 查询证据四个 truth 是否混淆或代持；
- LLM 输出是否未经白名单校验就触达 Gateway；
- OFFLINE 数据集是否存在任何查询路径；
- pending 反问是否可能被伪造 toolCallId 或并发第二反问绕过；
- 断连/取消是否被实现成事实回滚或伪造终态；
- 密钥生命周期是否扩大（入库 / 日志 / SSE 事件 / 异常文本）；
- `run_dataset_query` 失败路径是否漏记 query_log；
- 工具失败是否被错误地升级为整流终止（反之，致命错误是否被吞成工具失败）；
- 是否包含 DataAgent（AGPL）衍生代码痕迹，新依赖许可证是否兼容 Apache-2.0。

违反现有规则：`Domain Violation`。现有模型无法表达真实需求：`Domain Gap`。

## 3. Architecture Alignment

重点检查：

- 新类是否明确归属 conversation / runtime / toolset / catalog / gateway / report / persistence；
- 是否重新创建 `service / common / helper / utils / base` 业务桶；
- Controller 是否只依赖三个稳定 Application Facade；
- `@Service` 是否仅用于 3 个稳定 Facade；
- `conversation.query` 与报告分页是否保持纯 read side；
- AgentEventCodec 是否穷尽事件类型并有未知事件降级策略；
- 工具类是否保持薄壳（无状态、不做格式化决策、不直连 repository）；
- Repository / DAO / Gateway 是否反向依赖 Application；
- Core Domain 是否保持 framework / SDK / persistence free；
- 权限迁移脚本变更是否与权限码契约同步。

明确破坏已声明架构：`Architecture Violation`。真实需求无法由当前架构表达：`Architecture Gap`。

## 4. Dependency Alignment

任何新增 agent 内部 import 都检查 `DEPENDENCIES.md` 与 `AgentDependencyBoundaryTest`。

尤其关注单一子系统白名单：

```text
io.agentscope.*              仅 runtime
reactor.core.*               仅 runtime
io.yak.ops.business.dataset.* 仅 gateway（且仅公共契约类型）
```

以及窄 corridor：

```text
controller -> 三个 stable facades
toolset    -> report.AgentReportService
```

不接受以下修复方式：

- 为了让测试通过直接扩大 dependency whitelist；
- 引入反向依赖后声称“现在只有一个调用点”；
- 用 reflection / service locator 绕过 import guard；
- 把 dataset 内部实现类型（dao/model 等）经 gateway 泄漏到签名上。

Dependency graph 必须保持无环。

## 5. Code Style / Role Alignment

按 `CODE_STYLE.md` 检查：

- Service / Coordinator / Manager / Resolver / Reader / Gateway / Codec / Middleware / Repository 是否名副其实；
- 一个类是否承担多个 truth owner；
- Start / Resume / run_dataset_query 等流程的关键顺序是否直接可读；
- 事务是否只覆盖需要线性化的工作；
- 是否出现泛化 `execute/handle/process` 吞掉关键状态语义；
- 注释是否解释 invariant / why，而非复述代码。

纯格式和个人偏好不要当成阻塞问题。

## 6. Correctness

检查真实错误：

- 流式事件的顺序、终态与错误映射；
- 并发：同一会话并发 Start / Resume、断连后迟到事件、pending 竞态；
- 超时与部分失败：Python 强杀、数据集查询超时后的留痕；
- 边界值：limit 截断、空结果、超长输出截断；
- 归属校验覆盖所有读写入口（含报告删除）；
- 事务边界与幂等。

## 7. Compatibility

检查是否破坏：

- REST API 与 SSE 事件契约（前端依赖事件类型的穷举语义）；
- DB / Flyway 基线；
- 官方 StateStore 表配置兼容性；
- 平台权限码与菜单注册；
- 前端调用。

破坏性变化必须有明确迁移方案，禁止借架构重构做 Big-Bang contract change。

## 8. Safety

重点检查：

- 会话归属校验先于一切读写；
- prompt 注入面：用户输入与数据集元数据拼接进提示词的边界说明；
- Python 执行面：默认关闭、显式开启、信号量、超时强杀、临时目录清理；
- 密钥与敏感信息不泄漏（日志 / 异常 / SSE / 持久化）；
- query_log 全量留痕不被条件分支跳过；
- 断连 dispose 是否真正传播到上游订阅。

## 9. Engine / Persistence Boundary

外部系统边界检查：

- AgentScope / reactor 类型没有出现在 runtime 之外；
- dataset 类型没有出现在 gateway 之外；
- Repository contract 不暴露 DAO model / Mapper / Controller DTO；
- DAO 不调用 Application / Gateway / Repository；
- JSON codec 位于 `repository.support`，未进入 Core Domain。

## 10. Tests / Guardrails

每个 P0 / P1 问题都回答：现有哪个测试应该挡住？没有就指出 Missing Test。

长期 guard：

```text
AgentArchitectureTest
   -> role / stereotype / core-domain / read-side guards

AgentDependencyBoundaryTest
   -> top-level dependency matrix
   -> no-cycle
   -> SDK whitelists (agentscope / reactor / dataset)
   -> @Service allowlist
   -> forbidden broad buckets
```

行为安全测试仍负责归属校验、HITL 状态机、白名单自纠、limit 上限、Python 超时强杀、断连取消等 runtime contract。

## Refactor PR Rules

```text
一个 PR 一个主要边界
package move / class split / behavior change 尽量分开
不顺手改 REST / DB / Flyway / Domain semantics
不长期保留 production 新旧双入口
行为测试与 architecture tests 都必须保留
```

涉及结构调整的 PR 建议包含：

```text
Domain Impact Analysis
- Aggregate(s):
- Invariant/lifecycle impact:
- Domain Gap: yes/no

Architecture Impact Analysis
- Target subsystem:
- Stable entry / gateway:
- Runtime truth owner:
- Dependency direction changed: yes/no
```

如果修改 package dependency，再增加：

```text
Dependency Impact Analysis
- New edge:
- Existing corridor or new corridor:
- Cycle impact:
- DEPENDENCIES.md updated: yes/no
- AgentDependencyBoundaryTest updated: yes/no
```

## 严重级别

```text
P0 Blocker
- 敏感信息泄漏（密钥 / 凭据）
- 越权访问他人会话或数据集
- AGPL 衍生代码进入仓库
- 数据丢失 / 不可恢复破坏
- 明确安全问题（如 Python 执行防护被移除）

P1 Must Fix
- 业务结果错误
- 违反 REQUIREMENTS / DOMAIN
- 明确并发、幂等、事务、兼容性缺陷
- 高概率运行故障
- 打破稳定架构/依赖 corridor、SDK 白名单或引入 cycle
- query_log 留痕缺失

P2 Suggestion
- 有明确收益的可维护性、性能或测试改进
- 非阻塞工程建议
```

## 每个问题必须有证据

有效 Review 问题至少包含：

```text
位置：文件 / 行或方法
级别：P0 / P1 / P2
依据：Requirement / Domain / Architecture / Dependencies / correctness fact
场景：什么输入、依赖关系或并发顺序会触发
风险：会造成什么结果
建议：修复方向
测试：应补或应命中的测试
```

没有可说明的触发场景和风险，就不要凑问题。

## 固定输出格式

```text
# Review Result

Conclusion: PASS | CHANGES_REQUIRED

## P0 Blocker
无 / 问题列表

## P1 Must Fix
无 / 问题列表

## P2 Suggestion
无 / 问题列表

## Requirement Gap
无 / 说明

## Domain Gap
无 / 说明

## Architecture Gap
无 / 说明

## Dependency Gap
无 / 说明

## Missing Tests
无 / 说明
```

有 P0/P1 -> `CHANGES_REQUIRED`；只有 P2 可以 `PASS`。没发现真实问题就直接 `PASS`，不要为了显得有价值硬凑问题。
