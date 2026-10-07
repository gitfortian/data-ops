# Agent 第七版：活动会话状态跟随与停止

日期：2026-10-07

实施权威：[F-015](../product/features/F-015-agent-session-follow.md)。延续仓库 AgentScope Java 2.0.2，复用第六版恢复投影。

## 产品定义

| 必答项 | 本轮定义 |
|---|---|
| User | 返回活动会话的质量、资产治理及问数用户 |
| Problem | V6 重开后需反复手动刷新才能看到结果/澄清，且不能从恢复页面停止本轮 |
| Capability | 有界只读状态跟随、完成/待答恢复、按所见轮次停止 |
| Journey | 原会话 → 恢复 → 自动检查状态 → 结果/反问恢复 → 明确追问/应答；或停止本轮 → 真实状态核对 |
| Outcome | 能及时继续原任务或明确停止，不重复推理、不误停后来轮次 |
| Truth Owner | Agent turn 生命周期、SDK 消息/pending、源域事实与 Security 权限各保持原 owner |
| Producer / Consumer | 原 continuation/历史生产证据，原页面消费；原命令与 CAS 执行取消 |
| Reuse | Session/Project/RBAC、TurnInput、只读 Query、Registry/Executor、HITL、trace、现有终态 |
| E2E evidence | AF01～AF08 工程与真实验收分列，旧 G/T/QP/RA/SC 不关闭 |

## 取舍与影响

证据是 V6 页面活动轮的手动刷新和禁用 Sender，以及原 cancel(sessionId) 会选择当前执行轮。新增恢复页停止若直接复用会话级接口，晚请求可能误停后来轮次，因此命令冻结 turnId。此为静态旅程断点，不是已证明的用户收益。

模块 README 仍写“代码尚未落地”，与当前 Domain/Requirements、F-014 和已合并代码冲突；这是过时的实现描述，本轮以有效契约及代码证据为准，不据该句重复建设整套 Agent。

V6 计划仅手动刷新，属于当时交付范围；本轮经用户授权由 F-015 扩展为只读状态跟随。历史消息与 turn 的完整关联仍不足，所以继续不自动重放活动全文，也不承诺崩溃断点续跑。错误分类、跨执行对比、Dataset 口径、RAG/团队 Skill 仍需原契约/真实试点门槛。

Domain Impact：仅 Session/Turn 既有 QUEUED/RUNNING → CANCELLED 转移；WAITING_INPUT 不增加取消转移，pending 不变化，Domain Gap = no。停止请求成功仅代表命令处理，展示状态始终来自读取。

Architecture / Dependency Impact：conversation 稳定 AgentChatService 接收精确停止，复用 Registry 与 Repository；页面增加局部只读调度 hook，无新 Service/包走廊/依赖环/DB。现有结构可承载，不扩大白名单。

## 实施切片

1. V7-0：F-015 与目标 Domain/Requirements，明确有限读取、身份冻结、失败核对。
2. V7-1：turnId 停止 API、用户/项目校验、精确 QUEUED CAS、与 claimAndRegister 串行；终态幂等、HITL 拒绝、旧轮不能影响新轮。
3. V7-2：局部状态跟随 hook，3 秒间隔、最多 20 次、串行、隐藏暂停不重置预算；结果/反问/身份变化时读原历史，不重放全文。
4. V7-3：恢复页停止与权限、响应不确定后核对；切换/卸载/新建/主动操作使晚响应失效，失败保留历史和手动刷新。
5. V7-4：行为与架构回归、全量前端/后端 CI/发行制品、PR 合并；真实 AF 单列 PENDING。

## 验收场景

| ID | 场景 |
|---|---|
| AF01 | 重开活动会话只检查状态；排队→运行不重置预算，无模型提交/工具/SSE 全文重放 |
| AF02 | 完成/失败/中断或新轮身份出现时重新读取历史/目标；待答时显示原问题，回答保持原恢复通道 |
| AF03 | 同时最多一个读取，最多 20 次；隐藏暂停、可见恢复但预算不重置，耗尽可手动刷新 |
| AF04 | 网络/权限/投影故障停止跟随，保留历史并锁定发送，不伪造终态 |
| AF05 | 切会话、新建、卸载、开始推理/停止后旧请求无效，不覆盖当前目标/消息/澄清 |
| AF06 | 原 turnId 停止排队/运行；旧轮请求不能影响同会话新轮，重复终态幂等，WAITING 拒绝 |
| AF07 | 轮次/会话的本人和项目先校验；取消与认领并发保持既有 CAS/收尾，不靠界面隐藏授权 |
| AF08 | 停止未确认或状态竞态重新核对，不假报停止；普通查询、回链、候选、预算、取消/续播不回归 |

真实记录需部署 commit、账号/项目、模型/Skill、session/turn、源审计与前后状态；当前无可登录模型环境，全部真实 AF PENDING。成功信号与工程符合性分开，不宣称生产收益。
