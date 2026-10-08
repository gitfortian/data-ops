# F-034 — 两次历史质量执行比较

Status: IMPLEMENTING
Approval: 用户于 2026-10-08 在合并 V23 后授权继续实施 Agent 功能迭代。
Product basis: ACCEPTED PD-002；IMPLEMENTING F-009/F-011/F-012/F-019。

## 用户、问题与结果

User：质量负责人和表维护人员。Problem：用户需跨两次执行手工核对规则、结果及实际值/期望值；规则调整或执行异常容易被误判为质量改善。Capability：现有质量执行解读增加固定历史执行对的只读比较。Journey：原执行详情历史运行中明确选择另一已结束执行 → 进入原 Agent，显示基准与当前执行 → 用户明确发送 → 读取当前授权历史证据 → 核对可见规则差异/缺口 → 通过两次源回链人工排查。Expected Outcome：知道两次记录实际有哪些变化和哪些不能比较；语义质量与节省耗时待真实试点。

Truth Owner：Quality 拥有历史执行、规则结果和目标快照；Security 拥有权限；turn/SDK StateStore 拥有生命周期、消息及 pending。Producer：Quality-owned 有界只读比较 API；Consumer：原治理工具、原对话文本/事实卡/证据卡及原执行详情。Reuse：QualityExecutionReader/Repository、真实用户项目作用域、CHAT_RUN、质量读取权限、原工具预算、HITL/停止/恢复与证据核验。无新一级能力、导航、模块、业务状态机、表或权限。

## 固定边界

- 原 GovernanceTarget 增加可选 qualityBaselineExecutionNo，只能与 qualityExecutionNo 共存，两者合法且不同；旧单执行 JSON 兼容，不新增 purpose。该范围冻结在原 turn 输入；HITL/历史恢复保留两次身份，模型不能更换、扩展或按名称猜测目标。
- 比较任务仅允许固定执行对的读取工具及原核验/日期/反问/只读 Skill 辅助工具；禁止单执行任意读取、当前监控、Dataset/Python/报告/候选/写入。普通对话及旧单执行工具范围保持。
- Quality 先检查 execution:read 与 monitor:read，再分别在当前项目读取两次历史摘要；仅 SUCCESS/FAILED/CANCELED 且有结束时间可比。必须同一正数 monitorId、同一正数 datasourceId、同一历史 database/schema/table 身份，不拿显示名代替身份。缺失身份、活动执行、跨项目/不同目标、相同编号均拒绝。两次读取不承诺跨域原子快照。
- 每次执行按稳定记录 ID 在数据库最多读取21条规则证据，展示前20条并保留截断。使用历史 ruleId/name/templateCode/ruleType/column/result/metricValue/expectedValue，不读当前规则、SQL、异常原文、业务样本或连接配置。唯一正数 ruleId 才能对齐；重复/缺失拒绝。单侧未出现仅标“该侧可见证据未包含”，尤其截断时不能说规则新增/删除或未执行。
- 对齐结果由 Quality 确定性计算，返回两侧索引及 recordedDefinitionMatches（只比较历史 template/type/column/expected）。它不证明完整定义相同；缺少冻结 SQL/参数/样本范围时，禁止确认同口径、脏行数、因果或改善比例。实际值保留原字符串，不自动计算变化率；ERROR/NOT_RUN/NOT_PASSED 不合并。
- 文本字段有界：执行/模板/类型/列标识≤128，名称≤256，实际值/期望值≤512。每侧序列化≤10000，整体≤24000 UTF-16 units；超限拒绝，不能截断公式/期望值后比较。每侧及对齐分别登记原证据，原≤200标量核验范围内可核对；源链接只由服务端固定编号生成。读取/授权失败不发布可信比较，不暴露底层异常。
- 入口仅在当前详情已加载且与路由编号一致、两次已结束且有读取及 Agent 权限时提供。历史选择只导航/填写，不自动生成。切项目/执行、加载失败/撤权/迟到响应不能把旧详情用于新范围；恢复坏目标阻止提交。未找到合适历史时保留单执行排查，不自动选择“最近一次”。
- 使用原文本/事实/证据协议，追加比较范围与人工核对提示；最终消息和历史一致。关键数值/状态须核验本轮字段；引用合法不代表语义正确。需要改规则时返回源页独立操作，不自动修复、运行、通知或确认根因。

## 领域与实现范围

Aggregate：原 Quality Execution/RuleExecution、Session/Turn/Pending；无生命周期变化。原 F-011/F-012 的单执行范围对旧任务继续成立，仅本 Feature 的显式固定执行对允许扩展读取。源域增加最小比较 API 和项目隔离的有界历史规则读取；依赖仍 Agent.gateway → Quality.api，Quality 不反向依赖 Agent。无新 SDK 白名单或跨域依赖边。

历史路线要求 V4 真实试点，当前尚未完成；用户已明确真实模型与登录环境后续统一验收，本批只推进工程交付，不将历史候选清单当作产品授权或标记 SHIPPED。固定场景提示先复用原质量排查底座，不默认注册新 Skill。

## 验收

QE01 权限先行、项目隔离及固定目标；QE02 历史身份/终态与有界读取；QE03 同 ID 对齐、阈值/模板/列变化、单侧缺失及截断；QE04 原值/状态与 SQL/异常排除、载荷超限拒绝；QE05 实际 SDK 预读/核验/同轮 HITL/预算/最终历史一致；QE06 入口明确选择、无自动发送、权限/加载/执行/项目切换与迟到隔离；QE07 旧单执行、V23 澄清、原候选/指标场景及完整工具契约回归。

真实模型、登录态撤权、源审计、完整 J2、专家语义评分及收益 PENDING；不由模拟模型/自动化证明真实改善。
