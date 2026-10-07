# V11 验证记录

日期：2026-10-07

工程提交与最终 CI：[PR #328](https://github.com/gitfortian/data-ops/pull/328)，须在 V10 #327 后合并。
范围：[F-019](../../../product/features/F-019-agent-live-turn-reconciliation.md)、[V11 计划](../../AGENT_V11_PLAN.md)。

| 场景 | 自动化证据 | 真实验收 |
|---|---|---|
| LC01 | 实时只取消回执 ID；同步防双击；页面不调用会话 cancel | PENDING |
| LC02 | 丢 ACK 保留未知状态并阻止发送，刷新后才确认 CANCELLED | PENDING |
| LC03 | 停止与完成/待答竞态按实际读取，原 pending ID 继续 | PENDING |
| LC04 | 无回执零取消；回执后明确停止；卸载后不启动弃置订阅 | PENDING |
| LC05 | 停止/切换后事件及完成/错误/重连忽略；断线及刷新失败保留正文 | PENDING |
| LC06 | 原权限/恢复/跟随/HITL/预算及完整前端、类型、产品、边界与发行 CI | PENDING |

工程结果在 PR 收尾记录；没有服务端生产代码变化，完整 CI 仍回归原后端和 MySQL 集成。定向首次通过后出现 Jest 一秒退出提示，另用 detectOpenHandles 核对通过、正常退出，未报告泄漏，不使用 forceExit 隐藏资源问题。类型债务门禁通过（139 条既有诊断无新增）、生产构建和产品/前端边界通过；完整前端 131 suites / 667 tests 通过并正常退出；最终 head CI 待核对。

真实验收需固定部署/模型/Skill、账号项目、原回执/停止 ID、停止/断线前后实际状态与源审计，观察用户能否正确确认结果并继续。当前 LC01～LC06 全部 PENDING，旧 G/T/QP/RA/SC/AF/HE/NR/GP 待办不关闭。工程与真实产品收益分开。
