# F-039 / #498 — 5/5 黄金场景、真实收益与专业页面交接（集中单 PR）

状态：**工程实现/验收契约实施中；真实试点 NOT_RUN，不得标 SHIPPED**。
合同：PD-009 仍 PROPOSED；F-039 仍 DRAFT/PENDING。前四阶段工程代码在 default branch 不等于真实业务闭环已验收。
交付粒度：**5/5 全部收口在一个 PR**（不得把 UI、数据集、门禁、文档再拆多 PR）。

## 真实试点前提（由已授权试点负责人提供，不内置任何伪造来源）

1. 指定真实环境、精确 git SHA、当前项目和授权账号、真实已采集 datasource、Metadata 采集批次/指纹、AI 模型和 Skill 启用版本。测试账号不能借用生产口令给报告工具。
2. 按用户业务范围确定表及字段，建议订单、订单明细、支付、退款、客户，但这是**示例而非已存在事实**。人工黄金定义和来源证据需由独立业务/治理专家先行确认。
3. 覆盖“同义不同名、同名异义、既有 TYPE/UNIT 复用、缺单位/完整 CODE/粒度、同码不同义、标准待生效”正反题。不得用 Schema 字段名/JSON 合法性替代专业判断。
4. 同范围计时：人工查阅、人工填写、人工复核、人工返工分钟；Agent 辅助各项人工耗时；AI 等待分钟和模型 Token 独立计量。记录范围一致性的证明和复核者。不填就不算节约率。
5. 提前获批用于高风险 SI 用例的隔离/可撤销权限账号、故障注入、双节点、多次保存与审批配置；本阶段不自动执行破坏性测试或新增正式业务资产。

## 原业务页面交接的实际实现

- 原 Agent F-039 候选页新增 **正式回执→原 Semantic 域核对→原专业页面**。只有在**当前项目重新读取**正式回执且对目标 ID 调用原 Semantic REST / 关系 API 确认后才跳转：DOMAIN 域树、PROCESS 精确详情、FIELD 精确详情、TYPE/UNIT 原标准详情、PROCESS_FIELD 原绑定、SOURCE_LINK 原绑定。
- 目标业务域已有 `?domainId=`，业务过程已有 `?processId=`；标准字段、标准页补充接收 `?fieldId=`/`?standardId=` 仅作为精确只读回读，并保持原有列表、角色/项目授权校验。对象删除、权限失效、切项目或不存在都显示不可回读，绝不按同名替代。
- 已核验正式 PROCESS 后方可进入原 `/modeling/mainline` 和 `/metric/manage?processId=...`。**不创建模型或指标，不构造虚假的 modelId/metricId、发布版或 Usage 事实**。建模和指标专业目标页继续各自的项目/读取权限过滤。
- 源字段映射不由候选证据直接提升为 Modeling 事实。只有原 Semantic 真实来源关联可回读，且 SOURCE_LINK 在原服务的语义是**过程→来源表**，不能宣称是永久来源列→标准字段映射。
- 工程行为仍受 `yak.agent.source-semantic.enabled` 和 `yak.agent.source-semantic.adoption-enabled`（默认关闭）控制，不在验收阶段绕开源域授权。

## SI01–SI15 原始业务样本验收矩阵

在源环境执行并保存精确 API/数据库/审计、浏览器、Turn/trace、用户动作、专业复核等独立证据。每项结果只能为 `PASS`, `FAIL`, `BLOCKED`, `NOT_RUN`，状态后附真实观察和证据路径。

| ID | 需要真实证明的情形 | 最低证据类别 | 断言 |
| --- | --- | --- | --- |
| SI01 | 来源→Plan→原 Turn→候选→人工保存→正式回读 | api/db/browser | 正式 ID/状态对齐源域及审计，不把候选当成功 |
| SI02 | 未采集、缺注释/外键、分页/覆盖不足 | api/browser | 明确阻断或问题，无猜测、无漏报 |
| SI03 | 同义不同名跨片归并 | trace/expert | 有证据及业务专家同义意见，合并不吞列 |
| SI04 | 同名异义拆分、TYPE/UNIT/CODE/粒度缺口 | browser/expert | 不推测业务单位、码值、粒度；精确复用/冲突 |
| SI05 | Plan 确认/拒绝/修订、workspace 材料回读 | trace/api | 拒绝不执行，修订旧摘要作废 |
| SI06 | 跨用户/项目、两个并发任务 | api/security | scope 与 Task/Turn/产物隔离 |
| SI07 | 总预算/单 Turn 限额、工具和暂停计费 | trace/api | 拒绝新调用，额度不因重试恢复重置 |
| SI08 | 浏览器刷新、断线、停止、迟到回调和重启 | api/browser | 真原状态复核、无重复 Turn |
| SI09 | Schema 采集批次/字段指纹、Skill 变化 | api/db | 旧预检和保存阻断 |
| SI10 | Datasource/Metadata/Semantic 撤权 | security/api | 不发生跨项目读取或写入 |
| SI11 | 幂等同键、跨节点竞争、同码不同义 | db/api | 原 Semantic 唯一键/回执仲裁，无重复对象 |
| SI12 | 丢回执、部分提交、网络未知和人工继续 | db/api | 成功项不可重复；不确定时明确待核对 |
| SI13 | 启停标准与发布审批正/反配置 | approval/db | 沿原审批，未启用时字段不可引用 |
| SI14 | 压缩、消息/材料清理、不可变出处 | trace/api | 没有伪造成功的候选或失去来源的产物 |
| SI15 | F-023/F-026/F-028/F-029 旧助手回归 | regression/api | 旧入口/范围、权限、预算、生命周期、交接无退化 |

**重要缺口**：4/5 的新 TYPE/UNIT/CODE 标准专有属性、CODE 完整码集整体事务、标准待生效继续链路，目前仍有 `NOT_EXECUTED` 保护性结果；这类 SI01/SI04/SI13 不能因测试经过前端或 CI PASS 就被虚标业务 PASS。专业专家必须分别给出正反例复核结论。

## 机器可读黄金报告与防伪门禁

输入模板：`docs/agent/acceptance/f039-golden-pilot-NOT_RUN.template.json`。该文件 15 项全部真实标 **NOT_RUN**，不得修改为假通过报告。每次真实试点将模板复制到独立受控目录后填写：

```bash
# 纯离线合同测试（CI 自动执行）
node --test scripts/ai/f039-golden-acceptance.test.mjs

# 复核仓库提供的“尚未执行”示例：只检查格式，仍明确 NOT_ACCEPTED
node scripts/ai/f039-golden-acceptance.mjs \
  --input docs/agent/acceptance/f039-golden-pilot-NOT_RUN.template.json \
  --allow-pending

# 在真实环境经授权采集好 UI/API/DB/审计/trace、专家及耗时证据后
node scripts/ai/f039-golden-acceptance.mjs \
  --input /secure/f039/real-pilot.json \
  --evidence-root /secure/f039/evidence \
  --output /secure/f039/summary.json
```

- 每条 `PASS` 必须有该场景最低证据种类、非空 operator 和观察结论，引用存在且 SHA-256 一致的现场证据文件（建议敏感数据脱敏）。不得提供绝对路径、目录越界、空文件；报告本身只保留证据哈希/路径，不应包含原始密码/连接密钥/业务数据行。
- 必须有环境/构建/项目/采集/Model/Skill/原 Turn 真实身份，独立业务专家对 SI03、SI04、SI13 的署名复核，人工与 AI 同范围实测时长及独立 Token/等待统计。
- 阻断、失败、未执行保留原貌，默认命令退出码 2；只有 `--allow-pending` 允许 CI 检查模板时不失败。即使汇总输出 `EVIDENCE_COMPLETE_REQUIRES_SIGNOFF` 也**只是内容、哈希及签字字段齐备，不表示独立事实审计已通过**。
- 禁止提交生产账号、凭据、敏感源内容、原始数据行或真实业务私有证据至公开仓库。由负责人提供限权证据存储、完成事实复核后仅将脱敏结论与 SHA、阻断清单回填 #498 与 #493。

## 本轮工程与真实验收状态（2026-10-10）

| 项目 | 状态 |
| --- | --- |
| 4/5 #508 合并到 main | 已核对 |
| 5/5 正式 ID 回链和目标页再核验 | 已提交到 5/5 单 PR，等待其 CI |
| SI01–SI15 的机器可读结果与必需证据结构校验 | 已提交到 5/5 单 PR，等待 CI |
| 授权真实业务试点/真实模型/真实多节点/审批/专家意见 | **NOT_RUN**（当前无真实试点访问证据） |
| 真实同范围人工基线、模型用量及收益 | **NOT_RUN**（不得编造节省百分比） |
| #498 最终验收及 F-039 SHIPPED/APPROVED | **BLOCKED/PENDING**，不提前关单 |

后续只有真实 SI01–SI15、独立专家和原 Model/Metric 专业回读、前置 #494–#497 合同与最终默认分支检查均具证据后，才申请正式产品发布/关闭 #498 并回填父任务 #493。
