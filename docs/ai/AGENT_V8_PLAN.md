# Agent 第八版：历史回答与执行证据准确关联

日期：2026-10-07
实施权威：[F-016](../product/features/F-016-agent-history-evidence.md)。继续使用 AgentScope Java 2.0.2。

## 产品定义

| 必答项 | 本轮定义 |
|---|---|
| User | 返回历史核对治理/问数结论的用户 |
| Problem | 按 assistant 序号关联完成轮次不能证明对应关系，失败/部分回答/压缩会使证据错配 |
| Capability | 原消息携带轮次引用、历史准确关联、无关联时明确降级 |
| User Journey | 原会话 → 授权历史 → 明确关联的回答和原执行详情，或缺少关联说明 → 原恢复继续 |
| Expected Outcome | 不把别轮工具、配置或时间当作本轮证据；缺证据不猜测 |
| Truth Owner | StateStore 消息、turn 生命周期、step/query_log 各保持原 owner |
| Producer / Consumer | Runtime 原消息写入引用；原 read-side/UI 消费、核对 turn |
| Reuse | Session/Project/RBAC、官方 metadata、原仓储/trace/history/continuation/HITL |
| E2E evidence | HE01～HE08；工程自动化与真实模型/登录态分列 |

## 证据、取舍与影响

当前 AgentSessionQueryService.enrichWithTrace 按序号配对 assistant 与 COMPLETED；Runtime.projectHistory 丢弃原消息身份。官方 Msg 已有可序列化 metadata，适合保存原 turnId 引用。消息树虽然已有结构，主链路没有 append，且不是正文 owner，本轮不补齐消息树或改正文来源。

V6/V7 关于“关联不完整所以不重放活动全文”的限制继续成立。本轮只修复以后有引用消息的关联，旧消息、压缩丢失引用的消息明确降级，不宣称已具备完整时间线或可以重放全文。模块 README 的“代码尚未落地”仍与当前有效契约及已合并代码冲突，不据此重复建设模块。

Domain Impact：Session/Turn/Evidence 不变，metadata 只保存原 ID 引用，不拥有状态；Domain Gap = no。历史需求新增准确关联与缺失说明，先更新 Requirements/Domain。

Architecture Impact：runtime 生成原消息引用和只读投影；conversation.query 核对原仓储归属、完成状态与唯一性后组合 trace。无新稳定入口、走廊或依赖环；Controller/API 形状不变。UI 只增加原历史的来源说明。

## 实施切片

1. V8-0：F-016、目标契约与本计划。
2. V8-1：START USER metadata 写入 turnId；官方序列化与分组投影保留引用，HITL 同轮不插入新的 USER。
3. V8-2：移除序号猜测，仅唯一且归属一致的完成引用允许关联 trace；独立失败记录直接持有原 ID，旧正文保留。
4. V8-3：历史未关联提示、失败记录来源标识；页面水合只能使用服务端确认的原 ID。
5. V8-4：SDK/多轮/HITL/旧历史/权限/页面回归，全量 CI、发行校验、PR 合并；真实验收保持待办。

## 验收场景

| ID | 场景 |
|---|---|
| HE01 | 新 START 原 turnId 引用通过 SDK AgentState 序列化保留，正文不改写 |
| HE02 | 多轮/部分回答/失败与完成混合不按顺序错配，只读准确完成轮 trace |
| HE03 | HITL 恢复仍归原 USER/turn；工具中间消息不拆泡、不改变预算/目标 |
| HE04 | 旧历史、未知/缺失/非字符串引用保留正文，无 turnId/trace，不文本匹配 |
| HE05 | 重复引用或同引用多个分组拒绝关联，不偷偷选第一个 |
| HE06 | 轮次/会话当前用户、项目、session 先核对，跨范围不读 trace |
| HE07 | 页面提示缺少关联，独立失败明确来源；只对服务端确认的回答水合 |
| HE08 | 恢复/跟随/停止/HITL 与原工具预算、源域只读及构建发行检查不回归 |

真实记录须含部署 commit、用户/项目、模型/Skill、原会话/轮次、原消息与 trace 核对、源审计。当前无登录模型环境，HE01～HE08 真实验收全部 PENDING，旧 G/T/QP/RA/SC/AF 不关闭。
