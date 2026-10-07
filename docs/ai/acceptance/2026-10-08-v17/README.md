# V17 验收记录

合同 F-025，IMPLEMENTING。代码自动化与真实模型验收分别记录。

| 验证 | 状态 | 证据 |
| --- | --- | --- |
| 授权前零读取、版本/快照白名单与有界失败 | 本地通过 | MetricExplanationQueryAdapterTest |
| 未知/重复引用、超量输出、漂移与源标签重装配 | 本地通过 | MetricExplanationGatewayTest |
| SDK 合成/原生、预算与最终历史 | 本地通过 | StandardMatchRuntimeTest 的第三场景用例 |
| 原编辑器仅带入说明、原 expectedVersion 保存、作用域隔离 | 本地通过 | MetricEditModal.test.tsx、公共面板测试 |
| 完整 CI / 合并 | 待完成 | PR 依赖顺序；GitHub 账户支付/额度限制阻止 gate，未放宽门禁 |

ME-01：真实原子指标核对聚合/筛选/维度/周期与说明，人工带入保存回读；ME-02：派生引用版本及限定条件核对；ME-03：复合组合项及引用版本核对。记录用户/项目/MetricVersion ID/version/digest、Skill version/hash、trace、人工修改与耗时。

ME-04：撤权；ME-05：生成后并发改指标版本，交付或采纳拒绝，保存乐观锁冲突无覆盖；ME-06：缺/畸形/超界快照明确不可用；ME-07：改表单/项目后晚响应不带入；ME-08：停止后重新核对真实轮次状态。真实用例均 PENDING，不以 mock、SDK 假网关或本地自动化代替；当前不标 SHIPPED。

本地收口：31 模块 Maven test 成功，MetricExplanationQueryAdapterTest 5 项、MetricExplanationGatewayTest 4 项、共用 SDK 真实协议测试 12 项（含三个场景的合成/原生与预算）通过；原 Metric 保存版本冲突用例同批通过。原有两项依赖隔离 MySQL 环境的 Agent 用例本地 skipped，不记为通过。

前端完整回归 143 suites / 739 tests 通过；收口后原指标编辑器 5 项针对性测试通过，覆盖业务说明人工保存、改表单/复合公式阻断、项目切换晚响应、完整详情失败。第三场景面板及服务协议 5 项通过。类型检查 139 既有诊断、无新增；最终生产构建与 manifest 成功；77 reactor / 3183 production Java files 架构边界、前端边界与 Product baseline/self-test 通过。没有把本地自动化作为真实模型收益或登录 E2E 证据。

交付顺序：#333 → #334 → [#335](https://github.com/gitfortian/data-ops/pull/335)，逐个合并后下一个 PR 改 base=main，再执行准确 head 的完整 CI。2026-10-08 新 Product Guard 任务同样因账户支付/额度限制未启动；账户恢复后重跑失败任务并核对所有必需检查，不能按历史某一次通过绕过当前 gate。

PR #335 初始代码 head `a663313d824fa630cd18d29f11e13a8f40a04615`，后续仅文档收口。编辑器自动化验证原保存及 onSaved 交接；父页面已有回读路径保留，真实登录后列表回读仍需 ME-01/02/03 证明。
