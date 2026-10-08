# Agent 评测入口

F-011 工程回归与真实模型验收分别记录。固定题集目前 12 例，采用合成对象及证据，不代表此前规划的 70 例已经标注完成。题集不注册运行时 Skill，也不把合成 fixture 写入源域。真实服务读取环境映射的测试对象，专家必须先核验源对象是否符合本例 fixture 的事实条件。

V23 [问数澄清试点清单](query-clarification.md)提供8个待人工执行场景，尚未注册为自动执行套件，全部 PENDING，不计入下列48例题集或真实通过数。

第四版新增独立 `quality-troubleshooting` 题集：QP01～QP16 共 16 例，版本 F-012-v1，原 12 例保留。12 例可作单请求初步观察，QP11/12/15/16 为多步人工场景，真实模式返回 MANUAL_REQUIRED 且零请求；所有场景的语义/源审计最终仍由人工核验。新增题集不意味着 28 例真实通过或 70 例目标已完成。

V19 新增独立 `skill-scenarios` 题集：20 例，版本 F-030-v1，覆盖 STANDARD_MATCH、MODEL_MAPPING、METRIC_EXPLANATION、METRIC_DRAFT；其中 15 例单轮初步观察，5 例多步人工执行。三份题集共 48 例合成样本，真实模型验收均未执行。具体步骤见 [场景与 J2 执行说明](./scenario-j2.md)。

## 离线与确定性回归

```sh
node --test scripts/ai/*.test.mjs
node scripts/ai/run-evaluation.mjs --mode=offline --out=.task-ai-evaluation/report.json
node scripts/ai/run-evaluation.mjs --mode=offline --suite=quality-troubleshooting --out=.task-ai-evaluation/quality.json
node scripts/ai/run-evaluation.mjs --mode=offline --suite=skill-scenarios --out=.task-ai-evaluation/scenarios.json
node scripts/ai/summarize-evaluation.mjs --report=.task-ai-evaluation/scenarios.json
node scripts/ai/read-j2-evidence.mjs --mode=offline
```

离线只验证题集和报告，所有模型用例均为 NOT_RUN。Java `AgentTaskExecutionTest` 同时读取这份题集验证实际任务策略，另用真实 AgentScope 和脚本化模型验证拒绝结果进入框架历史、并发隔离、预算/HITL、输入字符上限。工具模板/授权/候选校验复用已有回归。脚本模型没有语义评分、真实账号授权或源审计证明。

## 真实环境执行

只在已配置模型的测试环境手动运行，不接入 CI 自动模型调用。环境变量：

| 名称 | 用途 |
|---|---|
| AI_EVAL_BASE_URL | 服务 origin，例如 https://test.example.com，不能含鉴权、路径或查询参数 |
| AI_EVAL_AUTH_HEADERS | JSON，仅允许 Authorization/Cookie；本地环境注入，不提交仓库 |
| AI_EVAL_MAPPING_FILE | 本地 JSON，按 caseId 绑定 account、projectId、target；target 的字段/purpose 与题集一致 |
| AI_EVAL_COMMIT | 部署 commit，用于后续核对 |
| AI_EVAL_MODEL | 操作者报告的模型标识，需与应用实际 effective-config 核验 |

映射示例（合成 ID；普通问数 target 为 null；每次运行使用一个账号，按账号能力分批筛选用例）：

```json
{"asset-read":{"account":"authorized","projectId":1,"target":{"assetId":77}}}
```

```sh
node scripts/ai/run-evaluation.mjs --mode=real --cases=asset-read --repeats=1 --out=docs/ai/acceptance/pilot/report.json
node scripts/ai/run-evaluation.mjs --mode=real --suite=quality-troubleshooting --cases=qp01,qp02,qp03,qp04 --repeats=1 --out=docs/ai/acceptance/pilot/quality.json
```

`--cases` 默认全部，`--repeats` 限 1～3；开始前明确调用次数与模型成本。脚本调用现有提交/SSE/trace 接口，不自动答复澄清或调用源域保存/运行命令。问数仍可能通过已授权 Dataset 执行只读查询，使用固定测试数据。传输中断显式取消脚本已确认的精确 turnId；挂起轮返回 AWAITING_HITL_REVIEW，由操作者在原页面处理。完整轮返回 AWAITING_EXPERT_REVIEW，绝不自动标记模型验收 PASS。服务端错误或传输故障返回非零退出码；权限负向题的拒绝仍需人工结合原权限/审计确认，错误退出不能自动证明负向通过。

`--suite` 只接受 task-scope（默认）、quality-troubleshooting 和 skill-scenarios，不接受任意文件路径。质量单请求题的映射仍以 caseId 绑定账号、projectId 和 qualityExecutionNo；先按每例 `fixture.sourceConditions` 核验真实源条件。QP10 需先在源页面构造旧执行与修改后的当前定义，执行器不会修改监控；QP09 需实际触发源/Gateway/核验范围上限。manual 题按 JSON 的 manualSteps 在原应用完成，不把 probeTool 的策略允许误当 live 授权或多步通过。真实模式仍要求文档中的环境配置，即使筛选出的全部是 manual 题；不会因此发起自动操作。

第四版对照建议每方案 16 例各一次（含 4 例人工操作），选 QP04/11/12/14 各追加两次，共最多 24 次场景尝试；其中脚本只执行可自动的单请求部分。人工实际模型调用与后续步骤另记，不据题目次数推算费用。报告的 scenario/executionMode 保留场景类型，NOT_RUN/MANUAL_REQUIRED/待专家状态均不能计通过。

报告仅保存题集哈希、轮/会话 ID、尝试工具与可获得的工具状态、结果与 trace 哈希、耗时和 usage 状态；不复制答案、工具参数、鉴权或原始 trace。HTTP redirect 禁止，错误详情脱敏。未知 usage 为 null，不能当作 0 或计算成本。部署 commit/model 是操作者输入而非服务器证明；实际策略/Skill/模型哈希与版本在原应用 trace 核对，关闭观测时需单列证据缺失。

TOOL_CALL_START 表示尝试，不能作为源域已执行证明；禁止调用必须结合工具结果、Query Gateway / 原业务审计核验。账号标签也不是实际权限证明。G1～G12、T1～T9 的并发、撤权、恢复、重启、协议异常等场景仍按原验收清单操作；本执行器覆盖基础提交及观察，不能代替这些完整操作。人工完成后在 run README 中逐例附原页面/审计引用与评分，不改写源业务事实。

当前状态：确定性工程回归已建立；真实模型、专家评审、70 例扩展标注、采纳/源保存审计及耗时成本对照 **待完成**。
