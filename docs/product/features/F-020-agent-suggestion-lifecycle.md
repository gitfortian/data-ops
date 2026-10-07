# F-020 — 原编辑器 AI 候选轮次核对

Status: IMPLEMENTING
Approval: 用户于 2026-10-07 授权继续规划多个版本并实现，本版 V12。
Product basis: ACCEPTED PD-001/PD-002；IMPLEMENTING F-010/F-013/F-014/F-016/F-019。

## User / Problem / Capability / Expected Outcome

资产维护者、质量治理人员在原编辑器生成候选，需要确认结果是否完成、停止是否生效、反问如何继续。现有面板按 session 取消，SSE 成功帧就呈现可采纳候选，提交/取消/旧回调可能交错。提供原轮精确停止、只读真实状态核对及唯一历史回答恢复，人工带入保持原源域校验。

## Journey / Truth Owner / Producer / Consumer / Reuse

原编辑器 → 明确生成 → 确认 turnId → 进度结束或停止 → continuation 核对 → COMPLETED 的唯一同轮 assistant 历史 → 人工带入 → 原保存。WAITING_INPUT 从原 continuation 恢复 toolCallId，继续同轮同目标同预算。活动/读取失败保留输入并阻止另发，提供刷新和原会话链接。

turn/Executor 拥有生命周期，官方 StateStore 拥有正文/pending，源域拥有定义指纹、授权/校验/保存。原 Query API/StateStore 是 producer，面板是 consumer；复用原精确取消、continuation/history、源编辑器及 Project/权限。不新增 API、表、状态、依赖走廊或框架。

## Behavior / Failure / Scope

- 同步防重复生成/继续；只有原提交回执或本人专用会话读取确认的 ID 才可精确取消。提交未确认时停止不猜轮次；晚回执只清理其自身 ID，不按 session 取消。
- SSE 结束/错误仅触发只读核对；不从传输猜 COMPLETED/FAILED。候选只取 COMPLETED 且目标一致、唯一明确关联的同轮历史回答；缺失/冲突/读取失败不采纳、不猜最后一段。
- WAITING_INPUT 显示原问题，明确输入后原 toolCallId 继续；排队/运行不发新轮。停止请求后重读真实状态，完成/待答竞态按实际；丢应答仍核对，核对失败不声称停止。
- 切对象/定义/卸载使原订阅、读取和采纳回调失效，仅对原已确认活动 ID 做精确清理；不取消后来轮次。取消采纳只使本次校验失效，不触发已完成轮的取消。
- 生成/读取失败文案固定，不展示任意异常或原始服务文本。保留用户背景；未授权不能生成/继续/采纳，源命令继续重验权限。页面已发出的精确清理沿用原服务端权限校验。

不自动推理、保存、运行或回滚；无后台持久化候选库/自动跟随/活动全文重播，旧会话 API 兼容保留。

## Acceptance

SL01 完成先核对且仅唯一原轮历史可采纳；SL02 反问同 ID/目标/预算；SL03 活动/读取失败阻止另发且刷新恢复；SL04 无回执不取消、晚回执只清理自身；SL05 精确停止/丢应答/完成或待答竞态；SL06 双击零重复；SL07 切目标/卸载晚回调零污染；SL08 原对照/去重/人工带入/权限/发行回归。

真实模型、登录态和源审计 SL01～SL08 独立 PENDING；按用户要求先代码与 CI，保持 IMPLEMENTING。

成功信号：真实使用者能辨别生成状态与候选来源，停止或断线后不重复生成/误采纳；试点记录误操作及恢复结果，目前无收益基线。不依赖新增源域契约，无阻断产品问题。
