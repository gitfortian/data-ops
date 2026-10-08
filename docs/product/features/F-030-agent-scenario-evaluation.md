# F-030 — 场景 Skill 回归与 J2 验收证据

Status: IMPLEMENTING
Approval: 用户于 2026-10-08 手动合并 #338 后指令“你可以继续了”，授权继续此前提出的 V19 评测、验收工具与质量/成本基线；沿用新分支开发并提交 PR 的授权。
Product basis: ACCEPTED PD-002/PD-003；IMPLEMENTING F-011、F-023–F-029。

User：治理、建模、指标与消费验收人员。Problem：既有回归只有资产/质量样本，四个场景与 J2 缺少可重复执行的证据采集，容易把模型生成、保存、声明引用和真实执行混同。
Capability：扩展现有 Node 评测脚本、固定样本、脱敏报告和只读 J2 核验。Journey：准备专用账号与原域样本 → 单轮回归/多步人工场景 → 原页面逐项采纳保存、验证发布与消费查询 → 公开 API 只读核验 → 专家评分与审计复核。Expected Outcome：能复跑、定位证据缺口；没有实测时明确 NOT_RUN/PENDING，不声称模型准确率、收益或全程通过。

Truth Owner：Agent 原轮次/历史/trace 持有运行和交付；Semantic、Modeling、Metric、Consumption、Dataset 持有原定义、发布和运行事实，Security 持有授权。Producer 是原公开 API 与人工审计；Consumer 是本地一次性评测报告。复用现有四个 Skill、submit/SSE/continuation/history/trace、原页面、验证与消费 API；不创建新业务模块、导航、状态机或事实库。

- 固定覆盖四个场景的正常、信息不足、权限不足及版本/依赖漂移；原子/派生/复合草稿、历史快照、批量部分失败与未知提交另列明确步骤。
- 多步场景不得用一次请求冒充复现。real 模式需要显式环境参数，不自动启用 Skill，不读取本机配置中的凭据，不替用户回答 HITL、不自动保存/发布/查询业务数据。
- 场景交付观测只来自完成轮唯一关联历史，并核对 continuation 与目标。回执结构/指纹观测不等于候选有效、事实正确或已经采纳。原 source validate 与保存审计由验收人员复核。
- J2 工具只 GET 原定义/精确版本验证/发布/引用/消费投影，固定稳定身份。不把已有对象当作本次操作审计，不把成功使用聚合当作本次查询证据；人工旅程与审计仍 PENDING。
- 汇总保留全部结果分母、缺失评分与 usage，分别报告候选有效性、事实支持、人工改动和完成度；耗时/token 为观测，不提供未知计价的费用或虚构人工收益。

E2E acceptance evidence：离线样本校验、HTTP/SSE stub 与只读证据一致性回归进入现有架构检查；真实模型、授权账号、原域审计、精确消费 queryId 与人工对照需另行执行。工程测试不能把 F-023–F-029 提升为 SHIPPED。
