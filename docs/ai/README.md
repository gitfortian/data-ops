# DataOps AI 规划与选型

日期：2026-10-07
文档类别：规划提案与调研证据（DOCS）  
状态：V9～V11 工程实现与本地回归已完成，合并及最终 CI 以 [PR #326](https://github.com/gitfortian/data-ops/pull/326)、[PR #327](https://github.com/gitfortian/data-ops/pull/327)、[PR #328](https://github.com/gitfortian/data-ops/pull/328) 为准，按版本顺序交付。内容见 [连续路线](./AGENT_V9_V11_ROADMAP.md) 及各版交付说明。AI F-009～[F-019](../product/features/F-019-agent-live-turn-reconciliation.md) 保持 IMPLEMENTING，真实模型验收独立待完成；合并不表示真实验收通过。

调研快照：`main @ 3e966e00`。下列四份规划保留当时的源码分析；首版代码与验证的最新状态见 [首版交付说明](./IMPLEMENTATION.md)。没有执行框架性能对测或真实模型产品验收。

## 建议先做什么

**V9～V11 已补齐终态后的重新提问准备、当前治理任务提问表单、实时精确停止与断线核对。** 继续使用 AgentScope Java 2.0.2。接下来优先按固定真实场景验证任务完成、证据语义、权限和源审计；更后续的问数澄清、质量执行比较、影响说明与团队 Skill 需要先满足 [连续路线](./AGENT_V9_V11_ROADMAP.md) 中的源域契约与试点门槛。

产品形态建议是“现有页面中的智能辅助 + 现有 Agent 中的跨域问答”。用户在资产、质量执行、指标详情和开发工作台完成任务；AI 随上下文进入，不增加一级 AI 治理门户，也不按业务模块各造一套聊天系统。

已交付只读资产理解、质量解读与排查指引，以及规则、描述候选的工程代码；第五版完善原编辑器采纳体验，第六版补齐原会话继续路径，第七版完善活动结果等待与精确停止，第八版修复历史证据关联。Agent → Dataset 的真实授权查询路径仍需登录态验收。跨执行对比、标准映射、敏感分类和自动业务动作另行规划，不作为已交付能力。

这意味着两个方向同时推进：AI 帮助治理；治理为 AI 提供可信数据。每个方向都以可验收的用户结果衡量。

## 阅读顺序

当前用户功能先读 [第八版交付说明](./IMPLEMENTATION_V8.md) 与 [第八版验证记录](./acceptance/2026-10-07-v8/README.md)，活动会话看第七版，会话恢复看第六版，候选采纳看第五版，历史排查看第四版，执行底座看第三版。第八版实施权威为 F-016；真实模型验收按用户要求独立记录待完成。

| 文档 | 回答的问题 |
|---|---|
| [第十一版规划](./AGENT_V11_PLAN.md) | 实时精确停止、断线核对与 LC01～LC06 |
| [第十一版交付说明](./IMPLEMENTATION_V11.md) | 停止/断线后的使用路径与限制 |
| [第十版规划](./AGENT_V10_PLAN.md) | 当前治理任务的提问准备、来源标记与 GP01～GP05 |
| [第十版交付说明](./IMPLEMENTATION_V10.md) | 表单/预览/编辑保护与使用边界 |
| [V9～V11 连续路线](./AGENT_V9_V11_ROADMAP.md) | 三版顺序、产品范围、依赖与后续候选 |
| [第九版交付说明](./IMPLEMENTATION_V9.md) | 终态说明与可核对原问题草稿，真实验收独立待完成 |
| [Agent 第九版规划](./AGENT_V9_PLAN.md) | 终态说明、原问题草稿、手工新轮、陈旧来源保护与 NR01～NR10；以 F-017 为实施权威 |
| [Agent 第八版计划](./AGENT_V8_PLAN.md) | 历史原轮引用、精确匹配、旧消息/冲突降级及 HE01～HE08 |
| [第八版交付说明](./IMPLEMENTATION_V8.md) | 历史证据关联与使用限制 |
| [第八版验证记录](./acceptance/2026-10-07-v8/README.md) | 工程回归与真实 HE 验收待办 |
| [Agent 第七版计划](./AGENT_V7_PLAN.md) | 有界状态跟随、隐藏暂停、按轮次停止、并发/失败及 AF01～AF08 |
| [第七版交付说明](./IMPLEMENTATION_V7.md) | 活动结果等待、精确停止与使用限制 |
| [第七版验证记录](./acceptance/2026-10-07-v7/README.md) | 工程回归与真实 AF 验收待办 |
| [Agent 第六版计划](./AGENT_V6_PLAN.md) | 最新任务/反问恢复、状态与失败、会话 URL、竞态及 SC01～SC08 |
| [第六版交付说明](./IMPLEMENTATION_V6.md) | 原会话继续能力、只读投影归属、使用与限制 |
| [第六版验证记录](./acceptance/2026-10-07-v6/README.md) | 工程回归与真实 SC 验收待办 |
| [产品规划](./AI_PRODUCT_PLAN.md) | 用户在哪些环节需要 AI，优先级、产品边界、事实归属是什么？ |
| [框架调研](./FRAMEWORK_RESEARCH.md) | AgentScope Java 与替代框架有什么可核验差异，为什么暂不迁移？ |
| [技术方案](./AI_TECHNICAL_PLAN.md) | 如何复用现有 Agent，安全地接入上下文、工具、证据和后续动作？ |
| [交付与验收](./AI_DELIVERY_PLAN.md) | 首期具体交付什么，如何评测、灰度，以及何时重新选型？ |
| [首版交付说明](./IMPLEMENTATION.md) | 已实现哪些功能，如何使用，哪些真实环境验证仍待完成？ |
| [首版验证记录](./acceptance/2026-10-05/README.md) | 自动化测试证据与发布前场景清单 |
| [第二版交付说明](./IMPLEMENTATION_V2.md) | F-010 已合并能力与使用/限制 |
| [第二版验证记录](./acceptance/2026-10-05-v2/README.md) | 自动化与 CI 数据库检查、真实模型待验收范围 |
| [Agent 第五版计划](./AGENT_V5_PLAN.md) | 候选与表单对照、重复识别、采纳/保存互斥及 RA01～RA08 |
| [第五版交付说明](./IMPLEMENTATION_V5.md) | 当前规则核对与人工采纳能力、源域边界与限制 |
| [第五版验证记录](./acceptance/2026-10-07-v5/README.md) | 工程回归和真实 RA 验收待办 |
| [Agent 第四版功能规划](./AGENT_V4_PLAN.md) | 第三版后的用户任务、现有证据限制、迭代顺序、后续功能队列 |
| [Agent 第四版实施清单](./AGENT_V4_BACKLOG.md) | V4-0～V4-4 切片、16 个排查场景、验证与试点收口 |
| [第四版交付说明](./IMPLEMENTATION_V4.md) | 历史质量排查表达、人工检查与回源、独立题集及边界 |
| [第四版验证记录](./acceptance/2026-10-07-v4/README.md) | 工程检查与 QP01～QP16 真实待验收 |
| [Agent 第三版历史建设规划](./AGENT_NEXT_STAGE_PLAN.md) | 第二版之后的提案快照；A0～A5 已由 F-011 实施，保留 A6 与框架复评依据 |
| [Agent 第三版历史实施清单](./AGENT_NEXT_STAGE_BACKLOG.md) | 原工具矩阵与 T 验收编号；当前工程结果以第三版交付为准 |
| [第三版交付说明](./IMPLEMENTATION_V3.md) | 任务范围、执行预算、实际配置/Skill 与评测入口 |
| [评测运行说明](./evaluation/README.md) | 离线题集校验、真实环境执行与人工评分 |
| [第二版历史产品规划](./NEXT_PHASE_PLAN.md) | F-010 形成前的提案证据，保留当时范围 |
| [第二版历史技术方案](./NEXT_PHASE_TECHNICAL_PLAN.md) | F-010 的设计输入，不覆盖当前源域契约 |
| [第二版历史交付清单](./NEXT_PHASE_BACKLOG.md) | 保留既有 G1～G12 真实验收编号；开发状态以交付说明为准 |

## 第三版之后的建设建议（2026-10-05）

继续使用 AgentScope Java 2.0.2。第四版当前工程范围已由用户授权形成 F-012，复用现有历史证据改进排查表达、澄清提示与受控回链。真实试点后再决定是否增加错误类别或跨执行对比；规划快照见 [第四版规划](./AGENT_V4_PLAN.md) 和 [实施清单](./AGENT_V4_BACKLOG.md)。当前不承诺自动根因定位。

原 AGENT_NEXT_STAGE 两份保留为 DOCS / PROPOSED 历史提案；A0～A5 已由 [F-011](../product/features/F-011-agent-task-execution-controls.md) 与 PR #320 交付。A6 的当前证据排查工程建设由本次授权形成 F-012，真实试点继续待完成，源读取扩展仍需独立立项。

当前真实模型与登录态 E2E 继续单独记录待完成，环境等待期间可以推进获批的代码与 CI。第二版前的 NEXT_PHASE 三份规划保留为历史 Evidence，不能直接当作新的建设指令，也不能据代码合并关闭真实验收。

## 调研发现与首版进展

1. **查询主体衔接。** 调研时 Agent 未传 `DatasetQuerySubject`；首版已补认证用户与源域角色 code，工具执行线程重新确认用户和项目。主体传递测试已覆盖，真实登录态与 Security 策略联调仍需验收。
2. **发现权限与版本。** 首版发现入口复用 `data-development:read` 与项目范围，字段快照绑定当前版本，查询再核验并冻结版本。目录最多 50 个、字段最多 200 项；目录发现不能替代 Dataset 查询安全裁决，也没有新增对象级授权制度。
3. **现有能力不等于已验收。** Skill、长期记忆已存在源码接线，但部分 Domain / Requirements 仍写规划中或未解决；应核对契约与验收，避免重复开发或默认开放。
4. **治理范围不能偷偷扩大。** 当前质量分区只对物理表适用，Model 的技术元数据分区不适用。PD-005/006/007 仍为 PROPOSED；不得据此启用通用质量发布门禁、新主体安全映射或扩大生命周期自动处置。

原始源码证据见技术方案，实施后的结果见交付说明。历史缺口不是当前生产故障报告，也不代表首版已在生产验收。

## 决策与实施边界

最初调研只产出方案。用户随后授权首版开发，已按 [Product Change Process](../product/PRODUCT_CHANGE_PROCESS.md) 建立 F-009 并更新目标域契约；实现沿用现有产品事实归属。推进流程为：

```text
选一个用户问题与 Golden Scenario
 -> 对新增跨域治理规则形成 Product Decision（需要时）
 -> 在 docs/product/features 建立并批准 Feature Spec
 -> 更新目标 Domain / Requirements / Architecture / Dependencies
 -> 开发与回归
 -> 真实 E2E 证据
 -> 按发布治理收口
```

首版保留 AgentScope Java 2.0.2，增加经 F-009 授权的只读治理工具与入口。第二版经 F-010 授权增加质量规则/描述候选、事实卡与原编辑器人工采纳，Agent 仍无治理写工具。分类候选、自动业务动作与后台自主治理仍是规划；新的建设提案按既有产品流程确认后实施。
