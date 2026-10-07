# 第四版工程验证与真实待验收记录

日期：2026-10-07。Feature F-012：IMPLEMENTING。真实模型按用户既有确认单列待完成；自动化/脚本模型不等于自然语言或登录态 E2E 验收。

## 工程覆盖

- 实际 AgentScope、Quality 公共 API 替身和独立执行身份：所选历史证据预读、关键规则结果/实际值/期望值核验、同一 StateStore 消息与当前输出一致。
- 五种规则结果原值保留、源截断标记、无权限/不可用/未装配降级、模型链接移除、固定人工核对提示；普通对话不增加源预读，候选任务不注入历史任务提示。
- 所选历史任务 HITL 挂起扣预算、重装恢复保留原目标/限额/已用数、拒绝换目标与规则候选。完整多轮模型澄清与真实撤权另列待验收。
- 原策略题集 12 例、新质量题集 16 例的真实策略 probe；这是工具准入检查，不能据此通过语义或多步场景。
- 页面真实渲染/交互测试：快捷问题只填输入、不自动调用推理，回源固定 executionNo，新会话清上下文，当前 monitor 不显示 undefined execution。
- Node：原执行器兼容、16 例完整校验/离线 NOT_RUN、四例 manual 零请求/MANUAL_REQUIRED、哈希与脱敏、断流取消、未知 suite 拒绝。

## 工程执行记录

| 检查 | 当前结果 |
|---|---|
| Agent 与 Maven 依赖回归 | 完整 reactor 通过；Agent 213 tests、0 failures/errors、1 skip（本机无 CI MySQL 环境）；新增 SDK/策略定向 20 tests 通过 |
| 前端 Agent 页面/服务 | 8 suites、90 tests 通过 |
| Node 架构/发布入口（包含 AI 执行器） | 14 tests 通过 |
| Java / 前端依赖边界 | 通过 |
| TypeScript 基线 / 生产构建 | 通过；保留 139 项既有诊断，无新增诊断；生产构建成功 |
| Product baseline | 通过，14 Feature Specs |
| CI | 沿用 Architecture 完整后端/前端/distribution、Metric 与 Product Guard；远端执行状态以对应 PR checks 为准，本地检查不替代 CI |

```sh
mvn -B -ntp -pl data-ops-business/data-ops-business-agent -am test -DargLine=-Djdk.net.unixdomain.tmpdir=D:/tianxy/code/data-ops
npm exec jest -- src/pages/ai-agent src/services/agent --runInBand
npm run check:types
npm run build
node --test scripts/architecture/*.test.mjs scripts/release/*.test.mjs
node scripts/ai/run-evaluation.mjs --mode=offline --suite=quality-troubleshooting --out=.task-ai-evaluation/quality.json
node scripts/product/check-product-baseline.mjs
```

npm 命令在 data-ops-ui 执行；Windows Maven 将整个 -DargLine 参数作为单个引号字符串传入。日志位于本地忽略输出，不提交原始日志/凭据。

## 真实验收：PENDING

| 场景 | 当前状态 | 需要的证据 |
|---|---|---|
| QP01～QP10、QP13/14 单请求初步观察 | NOT_RUN | 源条件核对、部署/账号/模型与实际配置、trace、专家事实/引用/澄清/步骤评分；负向调用结合结果与源审计 |
| QP11 HITL | NOT_RUN | 实际挂起/人工回复/同轮预算/同对象及历史一致性 |
| QP12 撤权与跨项目 | NOT_RUN | 无权尝试、中途撤权、跨项目拒绝与源审计；任务策略允许不代表已授权 |
| QP15 回源与新任务 | NOT_RUN | 受控链接/历史重开、独立当前监控任务；源保存/运行继续沿用 G 场景 |
| QP16 取消/超时/切目标 | NOT_RUN | 实际取消与超时终态、无新源调用、晚到结果与新目标隔离、Quality 零写入 |
| 既有 G1～G12 / T1～T9 相关路径 | PENDING（继承） | 原部署/登录态、保存/运行、Skill、重启及授权联调，不能因本批用例替换或关闭 |
| 同题对照、用户有效下一步、耗时/费用 | PENDING | 同条件基线、专家/用户评审、完整尝试分布；缺 usage/版本单价保持 unknown |

四例 manual 不由执行器自动操作，真实报告 MANUAL_REQUIRED 不能计为通过。正式试点前按 F-012 门槛由 Owner 复核；现有测试环境未提供，本次不补造账号、模型效果或源审计证据。
