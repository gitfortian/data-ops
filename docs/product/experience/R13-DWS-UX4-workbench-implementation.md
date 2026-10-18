# R13 UX-4｜DWS 高保真工作台实施与验收合同

> 2026-10-18 · 关联高保真基线 [PR #596](https://github.com/gitfortian/data-ops/pull/596) 与强制门禁 [#593](https://github.com/gitfortian/data-ops/issues/593)。Product Owner 已通过“好的，继续实施”批准两套高保真进入开发；本文件记录 **DWS 单独交付**，不代表 ADS 已开发。

## 本 PR 范围

把独立 DWS 高保真中的六步工作流落入现有 Modeling 领域，不另建孤立菜单、不复刻 SQL 编辑器、不改 Modeling/Semantic/Metric/DataDev 的事实归属：
1. 设计对象与业务过程；
2. 同业务过程 DWD 可信来源与精确发布版本；
3. 统计周期、IANA 时区、真实业务时间字段、周期桶、分组维度；
4. 标准度量与服务端静态合同校验；固定合成订单样本演示明确与真实查询分离；
5. 审核发布设计，复用 Modeling 现有发布/审批流程；
6. 查看最近 50 个保存修订，并将历史内容恢复为新的前向修订。

工作台使用项目既有 Ant Design 组件与视觉规范，按模型身份/版本合同集中导航。物理表结构编辑仍使用 Modeling 已有编辑能力；结构有未保存变更时，禁止保存粒度合同或执行静态校验。

## 新增后端能力

### 1. 服务端只读静态校验

- `POST /api/v1/modeling/models/{modelId}/dws-grain/validate`
- 复用 Modeling 当前项目、模型锁、物理结构指纹、业务过程、DWD 发布版本、业务时间字段类型、标准字段、分组/周期桶、度量聚合函数与 IANA 时区检查。
- 返回 `valid / blockers / structureFingerprint / design`，不写合同、不执行 SQL、不创建 DataDev 任务。
- 现有 `PUT .../dws-grain` 保存仍会独立重新校验；发布入口仍会校验冻结合同与当前上游发布指针，不能依赖前端检查绕过服务端。

### 2. 不可变修订历史

- 新增 MySQL 和 PostgreSQL 配对迁移 `V8__r13_dws_grain_revision_history.sql`，新表 `yak_modeling_dws_grain_revision` 以 `(project_id, model_id, revision)` 唯一约束保留每次不同的保存合同，并把现有 V7 当前合同回填为第一条历史记录。
- 保存新合同与追加历史行位于同一事务；重复保存相同摘要仍幂等，不重复生成修订。
- `GET .../dws-grain/history` 只返回当前项目模型最近 50 条修订及更新时间/操作人。
- `POST .../dws-grain/history/{revision}/restore` 校验历史摘要、当前物理结构指纹、历史引用的精确上游版本仍为当前有效版本；只有全部一致才能恢复。恢复写成新修订，不倒退编号；结构/来源漂移则 fail-closed，要求人工重新审阅。

## 前端交付

- 复用现有 `DwsGrainDesignPanel` 入口，呈现六步流程与右侧完整性证据。
- 来源字段选项读取所选 DWD **精确发布版本**的结构快照，不根据字段名猜测日期类型；如果冻结版本已过期，明确提示并要求显式采用当前版本、重选业务时间、重新校验和保存。
- 证据分层：V0 是服务端真实静态合同校验；V1 目前只是固定合成样本演示（订单明细 3 行、2 笔订单、金额行合计 450），不会读取业务库；V2 运行后对账保持 `NOT_EXECUTED`。
- 变更来源、时间、周期、分组、度量、上游版本或保存修订后，旧验证证据变为 `STALE`，不静默保留旧 PASS。
- 审核按钮调用现有 Modeling 发布对比/审批流程，不自行发布 SQL、数据或指标。
- 历史恢复按钮通过服务端 API 执行，失败时显示明确原因，不通过前端 localStorage 假装恢复成功。

## 验收门槛

- [ ] 后端单元测试：静态校验 PASS/BLOCKED 不写入；历史作用域正确；物理结构漂移拒绝恢复；结构与精确来源仍一致时恢复生成新修订。
- [ ] MySQL/PostgreSQL V8 迁移及唯一约束真实集成测试通过。
- [ ] 前端 Jest：精确来源版本回读、未保存结构禁止保存、合同保存、服务端静态校验、合成样本证据、历史安全恢复。
- [ ] Product Guard、Architecture、全后端 CI、全前端 CI、type-baseline 和 release build 全绿。
- [ ] 合并后检查 `main` 中 V8 两份迁移、controller/service/API/UI/测试全部存在。
- [ ] 浏览器实测：正常 DWS 设计、来源版本变化、撤权/不可用、保存超时回读、静态校验异常、样本风险、修订恢复；确认无假称真实查询。
- [ ] DWS 完成后再独立开发 ADS R13-C，不把本 PR 视为 ADS 交付。

## 不在本 PR 内

- 真实生产数据 V1 只读预演（必须经 DataSource/Security/Query/Runtime Owner 明确 READ、脱敏、限流、超时和审计合同）。
- V2 运行后源目标对账、Quality、Dataset/Service/Usage。
- 真实 SQL/DDL 执行或写入授权。DR-07 #557 不变。
- ADS 真实消费合同及后端持久化，留给独立 ADS R13-C PR。

## 风险与待确认

现有 DWS 粒度合同保存的是周期/时间/维度/度量函数/精确上游引用，尚不持久化每个业务度量的完整退款/折扣/净支付业务定义。UI 明确提示该边界；不以固定合成金额 450 声称已确认实际支付净额。后续若需要将业务 Owner 口径确认作为正式发布门禁，应先扩展 Modeling 合同字段及服务端校验，不得只靠浏览器勾选框。
