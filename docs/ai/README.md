# DataOps AI 规划与选型

日期：2026-10-05  
文档类别：规划提案与调研证据（DOCS）  
状态：首版与第二版代码已完成；第二版 [PR #319](https://github.com/gitfortian/data-ops/pull/319) 已合并。产品范围见 AI [F-009](../product/features/F-009-ai-governance-assistance.md) / [F-010](../product/features/F-010-ai-governance-suggestions.md)（IMPLEMENTING），真实模型验收待完成。F-011 任务执行约束正在实施，交付与证据见 [第三版交付说明](./IMPLEMENTATION_V3.md)。

调研快照：`main @ 3e966e00`。下列四份规划保留当时的源码分析；首版代码与验证的最新状态见 [首版交付说明](./IMPLEMENTATION.md)。没有执行框架性能对测或真实模型产品验收。

## 建议先做什么

**继续使用现有 AgentScope Java，下一阶段优先完善任务工具边界、评测回归与配置/Skill 可追溯。** 资产解读、质量解释和规则/描述候选已有代码，真实任务效果与发布范围需通过后续验收确认。

产品形态建议是“现有页面中的智能辅助 + 现有 Agent 中的跨域问答”。用户在资产、质量执行、指标详情和开发工作台完成任务；AI 随上下文进入，不增加一级 AI 治理门户，也不按业务模块各造一套聊天系统。

已交付只读的资产理解与质量结果解读，以及规则、描述候选和原编辑器人工采纳；Agent → Dataset 的真实授权查询路径仍需登录态验收。下一阶段先完善任务工具边界、评测与配置/Skill 可追溯；标准映射、敏感分类和自动业务动作另行规划，不作为已交付能力。

这意味着两个方向同时推进：AI 帮助治理；治理为 AI 提供可信数据。每个方向都以可验收的用户结果衡量。

## 阅读顺序

当前 F-010 代码交付与待验收范围见 [第二版交付说明](./IMPLEMENTATION_V2.md)，实施契约见 [F-010](../product/features/F-010-ai-governance-suggestions.md)。真实模型验收按用户要求独立记录待完成。

| 文档 | 回答的问题 |
|---|---|
| [产品规划](./AI_PRODUCT_PLAN.md) | 用户在哪些环节需要 AI，优先级、产品边界、事实归属是什么？ |
| [框架调研](./FRAMEWORK_RESEARCH.md) | AgentScope Java 与替代框架有什么可核验差异，为什么暂不迁移？ |
| [技术方案](./AI_TECHNICAL_PLAN.md) | 如何复用现有 Agent，安全地接入上下文、工具、证据和后续动作？ |
| [交付与验收](./AI_DELIVERY_PLAN.md) | 首期具体交付什么，如何评测、灰度，以及何时重新选型？ |
| [首版交付说明](./IMPLEMENTATION.md) | 已实现哪些功能，如何使用，哪些真实环境验证仍待完成？ |
| [首版验证记录](./acceptance/2026-10-05/README.md) | 自动化测试证据与发布前场景清单 |
| [第二版交付说明](./IMPLEMENTATION_V2.md) | F-010 已合并能力与使用/限制 |
| [第二版验证记录](./acceptance/2026-10-05-v2/README.md) | 自动化与 CI 数据库检查、真实模型待验收范围 |
| [Agent 下一阶段建设规划](./AGENT_NEXT_STAGE_PLAN.md) | 第二版之后的优先级、用户结果、复用、暂缓条件与框架复评门槛 |
| [Agent 下一阶段实施清单](./AGENT_NEXT_STAGE_BACKLOG.md) | 任务工具矩阵、独立 PR 切片、评测结构、T1～T10 验收与投入估算 |
| [第三版交付说明](./IMPLEMENTATION_V3.md) | 任务范围、执行预算、实际配置/Skill 与评测入口 |
| [评测运行说明](./evaluation/README.md) | 离线题集校验、真实环境执行与人工评分 |
| [第二版历史产品规划](./NEXT_PHASE_PLAN.md) | F-010 形成前的提案证据，保留当时范围 |
| [第二版历史技术方案](./NEXT_PHASE_TECHNICAL_PLAN.md) | F-010 的设计输入，不覆盖当前源域契约 |
| [第二版历史交付清单](./NEXT_PHASE_BACKLOG.md) | 保留既有 G1～G12 真实验收编号；开发状态以交付说明为准 |

## 第二版之后的建设建议（2026-10-05）

继续使用 AgentScope Java 2.0.2。先完成任务工具范围与有界执行，再建立离线 CI / 真实模型两层评测、实际配置与治理 Skill 的版本证据；试点后选择质量问题排查指引作为单一深化任务。建议第一批为契约/回归基线 A0 与工具策略 A1，详见 [建设规划](./AGENT_NEXT_STAGE_PLAN.md) 和 [实施清单](./AGENT_NEXT_STAGE_BACKLOG.md)。两份文档保留为 DOCS / PROPOSED 历史提案；用户随后授权 A0～A5 工程建设，当前实施以 [F-011](../product/features/F-011-agent-task-execution-controls.md) 为准。A6 仍等待真实试点后立项。

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
