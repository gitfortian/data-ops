# V6 验证记录

日期：2026-10-07

范围：[F-014](../../../product/features/F-014-agent-session-continuation.md)、[V6 计划](../../AGENT_V6_PLAN.md)、[交付说明](../../IMPLEMENTATION_V6.md)

## 工程检查

| 检查 | 结果 |
|---|---|
| Agent 及上游 Maven test | 通过；Agent 228 项，227 通过、0 失败/错误、1 跳过（AgentToolBudgetMysqlTest，本机没有测试数据库，须由 CI 实跑） |
| 完整前端 Jest | 129 suites / 614 tests 通过；随后增加损坏反问防护，最终相关 3 suites / 36 tests 通过，新增两项反问校验由 CI 再执行完整回归 |
| TypeScript 债务门禁 | 通过，139 条既有诊断，无新增，未扩大基线 |
| 生产前端构建 | 通过，生成制品 manifest |
| Product baseline | 通过，16 份 Feature specs |
| Java / 前端依赖边界 | 通过，77 reactor entries / 3153 production Java files，前端 1 条既有走廊 |
| Node architecture / release | 14 项通过，包括 AI 离线题集与制品门禁 |
| PR CI | 合并前必须通过最终 head 的 Product Guard、后端/前端/发行制品及 Architecture Gate；真实场景不因检查通过改为 PASS |

日志 `.task-ai-v6-*.log` 为本机忽略文件，不提交账号/模型密钥。本机 Maven 覆盖实际 SDK 与本地 HTTP 模型替身，不证明真实 LLM 语义；数据库测试跳过不记为通过，CI 需另确认。

## SC 场景与证据边界

| ID | 自动化证据 | 真实验收 |
|---|---|---|
| SC01 | Query 最新持久化目标；continuation 四种规范化目标/nullable 字段；页面刷新加载与显式追问 payload、无自动推理 | PENDING |
| SC02 | Query 普通最新轮与无轮次会话；页面普通追问不携带 URL 的旧资产目标 | PENDING |
| SC03 | Query 仅 WAITING_INPUT 恢复最新调用；页面原选项/toolResults；实际 SDK 替身 E2E 从恢复投影提交原调用、同 turnId 完成；既有伪造/预算恢复回归 | PENDING |
| SC04 | QUEUED/RUNNING 阻止提交且不订阅重放，刷新终态解除；FAILED/CANCELLED/INTERRUPTED 不显示待答；既有取消回归 | PENDING |
| SC05 | 当前用户/项目、登录与轮次身份不一致先拒绝；runtime/事件仓储未读取；原始损坏 payload 不出现在响应 | PENDING |
| SC06 | Query 损坏输入/缺帧/非法工具；UI 非法目标/状态/待答选项、上下文失败保留历史并禁用发送、刷新恢复；提交不确定先核对 | PENDING |
| SC07 | 页面 A/B 晚响应、新会话失效与 URL 清理；恢复目标优先于额外 URL 参数；卸载清理读取代次/连接 | PENDING |
| SC08 | 既有普通查询、事实卡/回链、候选采纳、事件续播、取消完整前后端回归；不把工程替身当作真实源鉴权 | PENDING |

SC07 的卸载清理由实现与既有组件卸载验证覆盖；新增独立竞态断言覆盖 A/B 和新会话。跨标签并发、模型回答与源权限变化需真实环境验证，不能由 mocked API 替代。

## 真实操作与记录

先准备可登录且配置模型的测试服务、两个隔离项目、普通/资产/质量执行/质量监控会话及可触发澄清的任务。记录部署 commit、账号权限/项目、模型/参数/Skill 哈希、sessionId/turnId、工具预算与源读取审计。密钥只放环境变量。

按 SC01～SC08 分别刷新、侧栏重开、回答同一轮、运行中刷新、停止/中断、A/B 切换、切项目与撤回源权限；覆盖上下文读取故障、缺投影、过期/并发应答和提交响应丢失。对每项保存页面、服务响应、trace 与源审计证据，实际模型输出由业务专家核对，不将网络成功或按钮点击记作完成。

当前未提供真实环境，全部 SC 保持 PENDING。既有 [G 验收](../../NEXT_PHASE_BACKLOG.md)、[T 验收](../../acceptance/2026-10-05-v3/README.md)、[QP 验收](../2026-10-07-v4/README.md)、[RA 验收](../2026-10-07-v5/README.md) 继续保留；F-009～F-014 不标 SHIPPED。
