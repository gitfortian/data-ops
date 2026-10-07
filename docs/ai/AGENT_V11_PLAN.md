# Agent 第十一版：实时轮次核对

日期：2026-10-07
状态：IMPLEMENTING；实施权威 [F-019](../product/features/F-019-agent-live-turn-reconciliation.md)，按 [连续路线](./AGENT_V9_V11_ROADMAP.md) 在 V9/V10 后交付。

1. 原提交回执冻结实时 turnId；提交未确认不能调用会话 cancel 猜选。
2. 原 Sender 停止进入精确 cancelTurn，订阅先失效、断开，再按原 session 读取实际状态；ACK 丢失可手动核对。
3. 订阅回调及水合检查原 generation/abort，断线保留正文并读取真实 continuation。活动继续原有界跟随，pending 回答原问题。
4. LC01～LC06 页面竞态测试，完整前端、类型、产品/依赖/发行 CI；三版 PR 顺序合并。

生命周期、源权限、预算与事实 owner 均不变，不宣称断点续跑、回滚或自动重新执行。真实验收需记录部署/模型/Skill、本人项目、停止前所见 turnId、实际 cancel 请求、停止后 turn/pending 状态与源审计，全部独立待执行。
