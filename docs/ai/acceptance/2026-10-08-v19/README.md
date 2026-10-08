# V19 工程验证与真实验收边界

日期：2026-10-08。合同：F-030；基线 main 306563f4（#338 已合并）。本记录仅为 Evidence。

## 已执行工程验证

- `node --test scripts/architecture/*.test.mjs scripts/release/*.test.mjs`：25 项通过，其中 19 项 Agent 评测/J2 回归；包含原题集兼容、四场景身份与回执、精确轮次清理、有界响应、UTF-8 分片、J2 版本/digest/引用/权限缺口与人工评分绑定。
- `node scripts/architecture/check-boundaries.mjs`：77 reactor entries、3191 production Java files 通过。
- `node scripts/architecture/check-frontend-boundaries.mjs`：通过，保留既有 1 条声明走廊。
- Product Guard 自测、产品治理基线、PR 分类与产品表面检查通过；没有新增业务 module 或一级导航。
- 离线运行 `skill-scenarios`：20 例均 NOT_RUN；汇总全部评分缺失、usage 未知、费用 UNKNOWN、收益 NOT_MEASURED。
- J2 离线运行：NOT_RUN，人工旅程、审计和精确查询证据 PENDING。

脚本模型/HTTP stub 测试不访问真实模型或业务源，不形成源保存/发布/查询审计。此次未变更生产 Java/前端代码，没有把此前 Maven/UI 全量结果冒充本批重新执行的验证；完整检查由现有 PR CI 继续执行。

## 待完成真实验收

20 例真实模型输出、15 例单轮专家评分、5 例多步演练，及既有 BS/MV/MD/J2A 全程验收均 PENDING。没有在此工作区配置并运行真实账号/模型验收；不读取 application-mysql.yml 中的用户本地配置寻找凭据。

执行 [场景与 J2 说明](../../evaluation/scenario-j2.md)，逐例核对实际项目、Skill 版本/hash、部署模型配置、源 validate、采纳/保存审计、精确版本发布以及真实 Dataset queryId。J2 只读工具的 SOURCE_MATCHED 和 ConsumerImpact READY 不能代替本次人工步骤/运行证据。既有 F-023–F-029 与本批 F-030 保持 IMPLEMENTING。
