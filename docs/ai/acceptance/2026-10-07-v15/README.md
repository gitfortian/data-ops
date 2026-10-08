# V15 验证记录

日期：2026-10-07。Feature：F-023 IMPLEMENTING。真实验收仍待完成。

| 检查 | 工程证据 | 状态 |
| --- | --- | --- |
| 源读取授权与有界目录 | StandardSuggestionQueryAdapterTest / StandardMatchCandidateQueryTest，授权前零读取、可信项目条件、TYPE/ENABLED/SQL LIMIT 21，交付 20 与截断 | PASS |
| 候选合法性与源漂移 | StandardMatchGatewayTest，未知/重复/超量 ID、版本和源模型变化、无候选仍授权 | PASS |
| Skill 范围与活版本 | ScenarioSkillScopeTest，唯一目录、只读加载、正文/版本变化、停用/删除/故障 | PASS |
| SDK 原生与合成输出 | StandardMatchRuntimeTest，真实 SDK+模拟 HTTP；历史回写、预算、未知工具；不是实际模型质量验收 | PASS（6 项 SDK 协议/并发/回退反例） |
| 草稿带入与停止 | StandardMatchPanel.test.tsx，源复核、目标切换晚结果、撤权、失败无候选、准确停止回读 | PASS |
| 保存前置条件及兼容 | ModelStructureServiceTest，过期定义在写入和审计前拒绝；原结构 JSON 可读且不增加指纹字段 | PASS |
| 前端全量 | 135 suites / 717 tests | PASS |
| 后端本地 | Agent 上游 30 reactor 完整 test 通过；新增保存回执/网关回退后再跑相关 35 项通过 | PASS |
| 类型与发行前端 | TypeScript 保持 139 既有诊断，无新增；前端 build 成功；回执相关 8 项补充通过 | PASS |
| 依赖与架构 | 77 reactor / 3165 Java files，前端 1 个既有 corridor；13 项架构/评测脚本 | PASS |
| 完整 CI / 隔离 MySQL / 发行 | PR #333 head `8b152d8a5737d1d35f2092e328274325e37242d7`：[run 37646079681](https://github.com/gitfortian/data-ops/actions/runs/37646079681)，后端/前端/发行全部 PASS，Product Guard/Metric Checks PASS | 实际检查 PASS；汇总 gate / 合并待完成 |

## 真实环境待办（PENDING）

- SM01：实际登录用户在现有模型字段生成候选，记录模型配置、Skill 版本/hash、标准版本、turn/trace。
- SM02：人工复核业务含义，带入、保存、回读 stdTypeId，核对保存审计。
- SM03：无匹配、业务说明不足、目录截断，确认界面表达缺口而不制造来源。
- SM04：撤销 Agent/Semantic/Modeling 权限、跨项目与停用标准，确认不能交付或带入。
- SM05：生成途中及带入前修改/停用 Skill，确认旧候选拒绝。
- SM06：另一用户修改模型后带入/保存，确认拒绝覆盖、原草稿仍可整理。
- SM07：停止、断线与字段切换，核对最终状态和原字段未被晚结果误改。
- SM08：同题人工基线及不同 Skill/模型版本比较，记录耗时、修改率、返工、token/成本；目标值未采集，不宣称已达成。

隔离 MySQL 与完整发行验证由 CI 执行；本地 Windows JDK 的 Unix socket 临时路径采用此前已确认的短目录测试参数，不修改生产配置。

2026-10-08 CI 阻塞证据：Architecture gate 首次及重跑均无执行步骤、runner_id=0。GitHub annotation：`The job was not started because recent account payments have failed or your spending limit needs to be increased.` 账户限制需用户处理；没有放宽 gate 或据此宣称 CI 全绿。
