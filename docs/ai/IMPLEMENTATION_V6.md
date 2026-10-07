# Agent 第六版交付说明

日期：2026-10-07

实施权威：[F-014](../product/features/F-014-agent-session-continuation.md)，范围与取舍见 [V6 计划](./AGENT_V6_PLAN.md)，交付 [PR #323](https://github.com/gitfortian/data-ops/pull/323)。工程代码完成，真实模型/登录态验收仍 PENDING，Feature 保持 IMPLEMENTING。

## 用户现在能做什么

从原 Agent 侧栏重新打开会话，或刷新带 `sessionId` 的页面，可恢复历史消息、最近提交的资产/质量任务与轮次状态。只有用户明确发送才开始新的推理；继续追问仍携带恢复目标，源读取重新检查当前权限。

等待补充信息的会话会重新显示原问题与选项，回答仍使用原 toolCallId，通过既有服务/SDK 续跑同一轮。排队或推理中的会话显示状态并阻止重复新提问，点击“刷新会话”读取最新结果。新建会话清除任务、待答问题与 URL 定位，最新轮次为普通问数时也不会回扫旧治理目标。

读取失败、损坏任务或缺失反问明确阻止继续，可刷新重试或新建会话；上下文读取失败时保留已成功加载的历史。提交响应不确定时先刷新核对，防止直接重复提交。快速切换会话、新建会话和卸载会使旧读取响应失效。

## 实现与归属

- 兼容新增 `GET /api/v1/agent/sessions/{sessionId}/continuation`，复用 `agent:session:read` 和 PROJECT_REQUIRED；原 history 响应不变。continuation、history、observability、turn trace 读取先检查本人及当前项目。
- 最新轮次按已有自增持久化顺序读取，目标只从 TurnInput 解码，不从标题、消息或证据推断。仅输出任务/状态/反问投影或固定阻止原因，不返回原始 payload。
- 反问帧只重现问题，pending 仍由官方 StateStore 拥有。原 submitResume 的 toolCallId 校验、CAS、原目标和额度恢复保持权威；服务端拒绝伪造与过期应答。
- 页面只消费投影，校验响应身份、状态和任务，规范化服务端 nullable 字段，损坏反问选项不进入渲染。URL 附带资产/监控参数不能覆盖服务器恢复的任务。

沿用 AgentScope Java 2.0.2，无新配置、数据库、业务状态、导航、源 API、写工具或第二份会话真相。业务改动只涉及 Agent；Quality/Asset/Dataset 的事实与授权继续由源域拥有。新增 read model 属于既有 conversation query 走廊。

## 使用与限制

1. 从资产或质量页面原入口开始任务，明确发送后 URL 自动记录会话 ID。
2. 重新打开或刷新，检查显示的任务范围和最近状态；待答时回答原问题，完成/失败等终态可明确继续提问。
3. 活动轮点击刷新等待状态收敛；恢复失败时不要将未确认提交当作失败重发，先刷新核对。
4. 回到原来源页核验事实与候选，历史证据仅供回看，不能据此直接保存业务修改。

本版不自动重放活动轮全文：现有 SDK 历史分组与 trace 顺序配对缺少完整消息/轮次身份关联，自动重放可能重复正文。原连接的游标续播保留，跨刷新恢复采用状态与手动刷新。不承诺进程崩溃后的断点续跑，INTERRUPTED 仍为已中断终态。

历史与 continuation 是并行读取，属于读取时的证据快照；跨标签并发提交/应答仍以服务端单轮互斥、CAS 与 SDK pending 裁决。损坏投影只能阻止继续或新建会话，不能重建 pending。会话 URL 是定位信息，不能授予访问权限。

自动化结果与 SC01～SC08 真实验收要求见 [V6 验证记录](./acceptance/2026-10-07-v6/README.md)。按用户要求先完成代码与 CI，真实模型/登录态、权限撤回及源审计独立待完成；旧 G/T/QP/RA 不因本版合并关闭。跨执行比较、错误分类、Dataset 口径、RAG/团队 Skill 的后续立项门槛继续有效。
