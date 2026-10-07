# V8 验证记录

日期：2026-10-07

工程提交与最终 CI：[PR #325](https://github.com/gitfortian/data-ops/pull/325)。真实模型验收不随 CI 自动变更。

范围：[F-016](../../../product/features/F-016-agent-history-evidence.md)、[V8 计划](../../AGENT_V8_PLAN.md)、[交付说明](../../IMPLEMENTATION_V8.md)。

## 工程结果

| 检查 | 结果 |
|---|---|
| Agent 及上游 Maven test | 通过；Agent 257 项，256 通过、0 失败/错误、1 跳过（AgentToolBudgetMysqlTest，本机无测试数据库，须由 CI 实跑） |
| 前端定向 Jest | 2 suites / 23 tests 通过，含恢复页新场景与已安装组件烟测 |
| 完整前端 Jest | 最终复跑 130 suites / 644 tests 全部通过 |
| TypeScript 债务门禁 | 通过，139 条既有诊断，无新增，未扩大基线 |
| 生产前端构建 | 通过，生成前端制品 manifest |
| Product baseline | 通过，18 份 Feature specs |
| Java / 前端依赖边界 | 通过，77 reactor entries / 3153 production Java files，前端 1 条既有走廊 |
| Node architecture / release tests | 14 项通过，含离线题集与发行制品门禁 |
| 文档链接 / diff 检查 | 通过 |
| PR CI | 合并前必须通过最终 head 的 Product Guard、完整后端/前端/发行及 Architecture Gate；真实场景不随 CI 改为 PASS |

首次前端全量并行运行 Maven/类型/构建时，既有 HeaderDropdown 菜单可见性断言出现 1 次失败（其余 643 项通过）；该套件单独 4 项通过，随后后端结束后的全量复跑 644 项全部通过。没有修改该无关测试或扩大超时，最终 CI 继续验证。

本地 `.task-ai-v8-*.log` 为忽略文件，不是已提交的真实环境证据。SDK 全链路使用本机 HTTP 模型替身，不能证明真实模型语义；本地数据库跳过不算该用例通过。

## HE 场景与证据

| ID | 自动化覆盖 | 真实验收 |
|---|---|---|
| HE01 | 原 Msg metadata 经官方 AgentState JSON 往返；SDK+本机 HTTP 模型替身完成后读取原 USER/回答 turnId | PENDING |
| HE02 | 旧正文、部分失败、乱序完成记录与同文本问题混合，仅精确 ID 读取 trace；独立失败不关联正文 | PENDING |
| HE03 | 官方状态中的工具澄清分组；原全链路 HITL 恢复后仅一个 USER，回答原 turnId | PENDING |
| HE04 | 无引用/未知/非字符串/缺失输入，原文保留、不借前轮引用、不读取其他 trace | PENDING |
| HE05 | 重复消息分组或重复完成记录拒绝关联，不选第一条 | PENDING |
| HE06 | 会话/轮次用户、项目、session 拒绝或保守降级；外来失败正文不泄露 | PENDING |
| HE07 | 无关联提示、不展示无 ID 步骤、只水合确认 ID、独立失败来源说明 | PENDING |
| HE08 | 原 SDK/预算/工具/HITL/恢复/跟随/停止行为及完整工程检查 | PENDING |

自动化只证明关联协议与降级，不评分模型自然语言语义，不把本机替身作为真实模型产品验收。

## 真实验收要求

部署最终 commit 后，以本人/当前项目的固定会话分别执行新两轮、失败后成功、HITL、旧消息混合；记录部署/模型/Skill 哈希、sessionId/turnId、官方消息引用、原 trace 与源审计。核对页面关联与原执行一致，旧消息无关联时不伪造步骤；跨用户/项目场景还要记录拒绝与零源调用。

当前没有可登录的模型环境，HE01～HE08 全部 PENDING，旧 G/T/QP/RA/SC/AF 不因本轮工程通过关闭。历史压缩无法完整回放、旧数据无引用不回填是限制，不记为已支持能力。专家仍需核对模型结论和引用语义，Feature 保持 IMPLEMENTING。
