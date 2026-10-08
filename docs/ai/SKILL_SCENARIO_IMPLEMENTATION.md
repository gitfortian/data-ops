# 场景 Skill 建设执行记录

日期：2026-10-07。依据：[固定版本复用调研](./AGENTSCOPE_2_0_3_REUSE_RESEARCH.md)第 11 节。用户已授权逐项实施；本记录不代替活动 Feature。

| 顺序 | 交付 | 当前状态 |
| --- | --- | --- |
| 1 | AgentScope 2.0.3 升级、依赖收敛、旧表回归 | 已合并 #332；真实环境待办见升级验收 |
| 2 | 首个标准匹配场景合同与源域接口 | F-023 IMPLEMENTING，首期单字段 TYPE 工程实现/本地验收完成，PR #333 实际后端/前端/发行 CI 通过；账户计费阻塞汇总 gate，合并待完成 |
| 3 | SDK SkillFilter / Repository / Middleware，范围与版本证据 | 随 F-023 工程实现并验收 |
| 4 | SDK 结构化交付、任务守卫及源域校验、原页面采纳 | 随 F-023 工程实现并验收 |
| 5 | 复用到模型映射和指标解释/草稿 | V16 模型映射 F-024 已提交 #334，本地验收通过；V17 指标版本解释与业务说明草稿 F-025 已提交 #335，代码/本地验收完成，复用同一骨架，CI/合并待账户恢复；标准批量/完整定义草稿另行切片 |
| 6 | 文件资源、Harness、子 Agent 与更自动化执行 | 按真实任务证据决定，不默认启用；尚无必须新增的证据 |

首个切片的 Domain Impact：扩展明确的工作台辅助目标，保持既有轮次状态机；Semantic/Modeling 仍拥有定义，AI 不写业务。Domain Gap：缺少授权有界标准候选 API 和结构条件保存，限定补齐这两个当前闭环必要接口。

Architecture / Dependency Impact：Agent gateway → semantic.api / modeling.api，runtime → toolset → gateway；源域不依赖 Agent，无新 Maven module 或全局运行引擎。相应依赖合同与自动护栏同步更新。

交付标准：每个切片需要代码、行为/边界验证、PR/CI、独立真实验收待办；不以框架装配成功宣称业务收益。真实模型环境尚未配置，沿用户确认保留待完成并继续代码与 CI。


执行顺序收口：框架升级 PR #332 已合并；V15 标准 PR #333、V16 映射 PR #334、V17 指标按依赖提交。账户额度限制阻止 CI gate 启动，保留 CI/合并及真实模型验收待办；资源/Harness/多 Agent 后置为按场景证据触发，不新增空能力。


## PR 与继续交付顺序（2026-10-08）

| 切片 | PR | 代码与本地验收 | CI / 合并 |
| --- | --- | --- | --- |
| 2.0.3 升级基线 | [#332](https://github.com/gitfortian/data-ops/pull/332) | 完成 | 已合并 |
| V15 标准匹配、scoped Skill、SDK 结构化交付 | [#333](https://github.com/gitfortian/data-ops/pull/333) | 完成 | 后端/前端/发行曾实际通过；最新 gate/Product Guard 因账户限制未启动，尚未合并 |
| V16 模型来源映射 | [#334](https://github.com/gitfortian/data-ops/pull/334) | 完成 | Product Guard 账户限制未启动；依赖 #333，完整架构 CI 待 retarget main |
| V17 指标口径解释与说明草稿 | [#335](https://github.com/gitfortian/data-ops/pull/335) | 完成 | 依赖 #334，完整 CI/合并待完成 |

账户恢复后：重跑 #333 最新失败检查并核对准确 head；全绿后合并，#334 改 base=main 再完整 CI/合并，最后 #335 同样处理。不能因底层任务曾通过就跳过汇总 gate。真实模型/登录 E2E 和业务收益仍按各 acceptance 记录 PENDING。Skill 方法包需管理员在测试项目通过原管理面登记，不随部署自动启用。

## V18 与分支状态补记（2026-10-08）

以上 PR 状态为创建时证据；本次重新查询：#333 已合并到 main（4a051f5a），#334 合并到 codex/ai-standard-match-skills，#335 合并到 codex/ai-model-mapping-skill。main 仍缺少 V16/V17 实现。V18 基于已验收的 46931cd9 继续，面向 main 的 PR 将携带必要 V16/V17 提交；不把“PR 已合并到中间分支”视作主线交付完成。最新 CI 结果以 V18 PR 的实际 head 为准。

用户本次明确完整标准→指标→消费链路纳入；F-026/F-027/F-028/F-029 为当前 IMPLEMENTING 合同，[V18 实施](IMPLEMENTATION_V18.md)与[真实验收待办](../product/acceptance/agent-batch-j2-2026-10-08.md)覆盖该范围。资源/Harness/子 Agent 仍无新增必要证据，不启用。
