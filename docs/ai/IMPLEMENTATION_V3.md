# 第三版：任务执行约束与回归

日期：2026-10-05。当前实施契约 [F-011](../product/features/F-011-agent-task-execution-controls.md) 为 IMPLEMENTING，授权来自用户“继续实施”。沿用 AgentScope Java 2.0.2，没有迁移框架。代码与自动化建设覆盖 A0～A5；真实模型/账号和专家评审待完成，A6 排查深化仍等待试点立项。

## 用户结果与事实归属

资产维护、质量治理用户从原入口选择对象，再对话获取授权证据/候选，最终在原编辑器人工保存；普通 Dataset 问数继续走原查询安全裁决。服务器在真实工具执行之前限定任务与对象，管理员能看到实际生效配置和 Skill 版本证据。

业务事实属于 Asset/Quality/Dataset，授权属于 Security；消息和辅助预算仍使用官方 StateStore，轮次生命周期仍属于 yak_agent_turn。沿用 ToolAudit、SSE、HITL、取消、UserExecutionScope、源候选校验和原保存命令，不新增导航、业务状态机或 Agent 治理写工具。

## 已实现范围

| 切片 | 实现与限制 |
|---|---|
| A0 / A1 任务边界 | 实际执行守卫与模型工具过滤；显式资产任务只读所选资产/分区，历史质量只读 execution，规则只读所选 monitor；仅对应 purpose 可生成候选。首批不自动读关联对象。未知工具拒绝，普通问数保留现有工具/权限路径 |
| A2 有界执行 | 原子预占每轮调用预算、单工具 ERROR/异常累计上限；入口预读、澄清、Skill 计入。调用前持久化失败即拒绝；HITL 恢复冻结原额度，旧挂起轮首次初始化记录。取消/超时停止新调用，晚到的失败回调不能写回旧预算覆盖新轮 |
| A3 评测资产 | 12 例合成题集与 Java 策略回归、Node 执行器/测试、CI 离线校验；真实执行只做提交/SSE/trace，不自动回答澄清或源保存/运行；语义/源审计始终待人工核验 |
| A4 实际配置 | execution-contract 记录实际过滤工具与 schema 哈希、冻结额度、实际 Skill 片段/最后成功加载正文哈希及来源；UI 展示启动/动态来源，预留未接入键不可调整。保留原 LLM 调用级实际超时 trace |
| A5 Skill 稳定性 | 启用目录按 DB 现读，helper 只读当前启用 SKILL.md；停用/删除/冷启动回归；展示名不覆盖 SDK name、稳定框架标识；乐观锁使用编辑版本并推进持久/响应版本，过时更新或并发删除不能静默覆盖/复活；三份治理 Skill 候选材料尚未默认注册 |

SDK ToolBase 适配器保留当前业务工具名称、参数与执行属性，真实结果由 SDK 写入历史，避免伪造“拒绝事件”而丢失 toolCallId。澄清 external tool 在挂起前扣预算，方法体仍不执行。Skill helper 的描述/schema 被收窄为当前实际只读能力，不自动激活工具组或执行脚本。共享 Toolkit 不按会话全局切换激活状态。

适配当前注册工具与 DB Skill helper；MCP/新工具/额外资源文件尚未开放。当前工具无 preset/扩展 metadata；SDK 注册替换不能泛化保证任意 preset/MCP 权限兼容，新增适配要单独契约及测试。

## 配置与生效时点

所有新增启动项位于 application-ai.yaml 的 yak.agent.execution：

| 环境变量 | 默认 | 生效规则 |
|---|---:|---|
| YAK_AGENT_EXECUTION_MAX_TOOL_CALLS | 32 | 重启后新轮冻结；HITL 使用原轮额度 |
| YAK_AGENT_EXECUTION_MAX_FAILURES_PER_TOOL | 3 | 重启后新轮冻结；累计 ERROR/异常，成功不清零 |
| YAK_AGENT_EXECUTION_MAX_MODEL_INPUT_CHARS | 120000 | 模型调用前检查实际 messages JSON 字符数 |

字符预算含 system、历史/工具结果和参数；不含工具 schema/生成选项，不等于 token 或计费。SDK 执行前拒绝的未知工具/非法 schema 不进入真正调用扣费；其模型循环继续受现有 maxIters/轮次超时限制。五态证据 UNAVAILABLE 不是 SDK ERROR，计调用但不计 ERROR 失败次数。当前预算只记录工具名计数，未做同参数聚类或精准费用闸门。

已接入热配置仅 memory.enabled、observability.enabled、llm.timeout（每次尝试现读，1s 微缓存）。LLM provider/name/base-url/key、maxIters 与工具预算由启动配置装配。DB 的 llm.max-iters / approval.query-execution 是未接入预留键，旧行仍可查看，但页面/API 拒绝调整，重启也不会消费这些旧行。

Skill expectedVersion 对新 UI 必填传入；API 保留旧客户端可省略语义，省略只能拦截读后并发，不能识别早先打开的旧编辑页。当前 Skill 为全局管理员维护，无项目私有权限范围。停用/删除不能撤回历史消息中的旧正文；运行时 helper 读取最新启用目录，历史不能成为本轮授权或当前证据。

## 验证与后续

自动化证据见 [第三版验证记录](./acceptance/2026-10-05-v3/README.md)。评测命令、绑定、脱敏和评分见 [评测说明](./evaluation/README.md)，治理 Skill 材料见 [技能说明](./skills/README.md)。固定题集 12 例是工程基线，此前 70 例目标、三次真实对照与专家标注尚未完成。

真实 G1～G12 / T1～T9、模型协议异常联调、真实撤权/重启/HITL、原保存/运行审计及模型耗时成本对照仍待完成，不能据 CI 全绿宣称生产效果或 SHIPPED。下一步先绑定固定测试项目/账号并执行只读试点，达到验收条件后再立项 A6 历史质量排查指引；继续保留原领域契约与人工保存。
