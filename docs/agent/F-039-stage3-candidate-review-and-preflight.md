# F-039 / #496 — 3/5 单 PR：跨来源候选、集中人工审阅与只读预检

Status: **ENGINEERING_IMPLEMENTATION_IN_PROGRESS / DO NOT CLAIM E2E VERIFIED** (2026-10-10)
Branch: `feature/f-039-phase-3-complete` — 本阶段只维护一个 PR。
Dependency: #495（2/5）尚未整体验收；#493 产品/Owner 合同状态仍应单独批准。

## 业务边界

| 问题 | 3/5 设计 | 证据与限制 |
|---|---|---|
| 可信来源 | 只允许读取 #495 原 AgentTask 的 COMPLETED 状态、每个 chunk 的真实 original-turn 结果摘要/sha、冻结的 Schema 及已审 PLAN.md | 不完整、漂移和当前无项目/DataSource权限均拒绝 |
| 本体候选 | 从已冻结来源表/列构建 DOMAIN、PROCESS、FIELD、PROCESS_FIELD、SOURCE_LINK 候选；TYPE 来源 SQL 类型生成待核对的标准占位；UNIT/CODE 缺业务证据时保留显式问题 | 不把物理表名/SQL type 猜测当作获准业务标准，不读取源数据行 |
| 既有资产 | Semantic-owned `SemanticCandidateCatalogApi` 跨域/过程/标准字段/六类标准做有界只读投影，包含正式ID/版本/ENABLED状态，Agent 没有越权访问 Semantic DAO | 每类完整分页，超过 1200 项一律拒绝声称「不存在」 |
| 同义/异义 | 默认每个列独立；仅人工明确确认后才能 merge；split 单个来源证据并重绑定 PROCESS_FIELD；冲突匹配不自动复用 | 合并清除可能过时的业务角色、类型和选择；原证据不会丢失 |
| 审阅/修订 | SDK 原 StateStore 的 CAS 存有限候选/原证据/答案/选择/修订；同 revision 才能编辑 | 实际 AgentTurn/消息依然由原 Turn owner 持有，候选不进入正式 Semantic |
| 集中依赖预检 | 检查选项与实际传递依赖闭包、环、编码冲突、精确已有ID/版本、ENABLED TYPE、METRIC UNIT、领域/过程/字段角色 | 未选依赖只显示为阻断，不自动加入保存集合 |
| 预检凭据 | 绑定项目、用户、source fingerprint、计划hash、chunk结果sha、目录摘要、修订、所选 candidate payload 的 digest | 是只读摘要，不是自动写入授权；4/5 要在 Semantic 保存事务里重新核验 |
| 原页面 | 已完成分析任务的同一个来源面板追加候选审阅，不加新一级导航 | 候选分页/分类、编辑、重复利用核对、merge/split、答案与预检阻断项可见 |

## 安全契约（不可绕过）

1. **默认关闭**：沿用 `yak.agent.source-semantic.enabled` 以及共享 plan workspace；未启用时 Controller 与服务都不注册。
2. 任意候选操作在读取前调用原 SourceSemanticTaskFacade 的登录、CHAT_RUN、DataSource READ、项目与 session ownership，并重新核验完整 Metadata/计划/所有 original-turn 产物；目录读取再次独立检查 `semantic:read` 与项目。
3. 模型不能把生成的正式 ID、候选 ID、编码、源字段引用当作可信输入；所有候选 ID 由服务端稳定生成，人工编辑不可更改 ID、证据、候选种类和依赖。
4. `CODE` 新标准需要完整人工审核的码值及标签清单，而物理 Schema 无这些事实。此阶段明确阻断；不执行取样行、推测码值或隐藏地创建标准。
5. 新建的 TYPE/UNIT 定义暂不按物理类型直接批准；如引用已有标准，必须是 Semantic 返回的启用对象（及实际版本）。不重复创建同码异义定义。
6. `preflight` 不写任何 Semantic、Metadata 或来源业务表。4/5 不可只凭当前 Ticket 就跳过源域项目/权限/审批/来源/正式版本/幂等回执复核。

## 单 PR 内的验收矩阵

### 工程单测与静态检查

- [x] 不自动选用候选，证据绑定原始 chunk
- [x] type/metric unit 缺失及非法正式 ID 正反测试
- [x] 选择缺失依赖、旧 revision、目录漂移与跨项目拒绝
- [x] 人工 merge/split、证据保存与关联更新
- [x] 集中答案使旧修订和预检失效
- [ ] CI Maven/TS/产品守卫均通过（提交 PR 后读取真实流水线）
- [ ] SDK MySQL StateStore 双任务/重启/并发 CAS 验证

### 真实业务 E2E（必须在授权环境由独立 Agent/业务负责人执行）

- [ ] 一套已采集且授权的真实来源，从原入口完成全部 #495 片段，再打开同一任务候选页，校验真实 turn/trace/hash。
- [ ] 两张同义不同名列：人工确认为同一概念后归并，来源证据不丢；同名不同义保持拆分，preflight 不暗合并。
- [ ] 既有 TYPE/UNIT/字段复用与候选新增区分；正式版本变更/停用/同码异义应拒绝。
- [ ] 数据源权限撤销、Semantic权限撤销、切项目、Schema漂移、模型答案改变、Skill变化后旧候选/预检阻断，不发生业务写入。
- [ ] 目录大于分页上限/分页失败必须显式阻断；不能将「无搜索结果」误判为可新建。
- [ ] 缺粒度/币种/单位/完整CODE码值保留阻断或业务疑问；独立语义专家核查候选正确性。
- [ ] 刷新重登恢复真实已提交的 review revision 与 selected IDs；CAS 并发操作不得互相覆盖。
- [ ] 按真实浏览器 Network/DB 审计确认整个阶段没有 `Semantic CREATE/UPDATE`、数据源业务 SQL 或直接 LLM 业务写工具。

## 尚需在本 PR 内继续收口的阻断项

- 结构化 LLM 跨片概念抽取、可证明的人工问题影响范围与结构化答案重算，目前仅有基于完整 Schema 的保守服务器草稿与人工合并/编辑。没有真实的业务语义质量证据。
- TYPE/UNIT/CODE 新候选当前采用「待核对、不自动批准」占位；尚缺人工确认过的类别专有属性与 CODE 全量值的正向预检合同，不能视为正式可保存的标准定义。
- 目录快照没有提供专用多轮稳定游标，因此在大量并发目录更新时可能得到分页中间态；下一阶段保存前必须在 Semantic 原 owner 下再次验证，不能将单次扫描证明为可提交。
- 预检只有只读绑定 hash，未实现供 #497 使用的持久可核销票据和服务端信任链；必须与 4/5 采用同一获准合同，不能让 Agent Ticket 直接变成写权限。
- 前述 SDK 2.0.3 官方 PlanMode HITL、多节点 CAS 与 #495 服务器长任务完整闭环的真实运营阻断仍然存在。

**验收决策：** PR 是实现载体，不等于产品合同批准或真实 E2E 通过。待 #495 及本阶段阻断项核销后，再将 #496 标记完成；本阶段禁止创建第 2 个组件 PR。
