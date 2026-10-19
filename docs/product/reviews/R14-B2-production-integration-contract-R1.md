# R14-B2 正式发布证据集成合同 R1 — 已有条件批准研发

**状态：ACCEPT_CONDITIONAL_FOR_DEVELOPMENT / T2_PRECONDITION_PENDING / NO_PRODUCTION_WIRING**  
关联：[DR-06 #556](https://github.com/gitfortian/data-ops/issues/556)、[R14-2 #614](https://github.com/gitfortian/data-ops/issues/614)、[PR #635](https://github.com/gitfortian/data-ops/pull/635)、[B2 Feature Spec 与 P01–P18](R14-B2-publication-evidence-feature-spec-candidate.md)。  
治理：一名 Accountable Technical Owner 对 Modeling/Semantic/Security 三份证据作**一份**版本化技术决策，不要求三个人分别点 GitHub 审核按钮。**唯一技术负责人已明确批准 T1–T4 的有条件研发实施**（[#556 正式授权记录](https://github.com/gitfortian/data-ops/issues/556#issuecomment-6261453377)），不代表 T2 共库/同事务/锁顺序已通过，也不代表生产发布授权。

## 1. 为什么这次不直接把 Draft validator 接进 publishLocked

来自 **2026-10-19 当前 main 源码**的确定证据：

| 领域 | 已存在能力 | 尚不足以授权新发布闸门的事实 |
|---|---|---|
| Modeling | `ModelVersionService.publish`、`publishApproved` 和 `publishWithMapping` 进入 `publishLocked`；模型根 `findByIdForUpdate`，结构 `getForUpdate`；`semanticView` 计算稳定结构指纹，版本插入与根指针在业务事务中同步提交 | 幂等分支先按结构/映射/DWS 内容比较后可能直接返回旧版本；当前 `serialiseMeta` 没有冻结标准证据与算法身份；审批 payload 未有标准/权限版本冻结 |
| DWD Mapping | `DwdMappingPublicationService.publishChecked` 校验 MappingRevision，已发布同一修订存在**提前返回历史回执**的路径 | 这条旧成功捷径没有按新标准状态重检；仅在 `publishLocked` 后部增加 validator 无法保证所有路径覆盖 |
| Semantic | `StandardQueryApi.get/labels/codeSetReference` 是只读、项目范围查询；`CodeSetRevision` 为完整码集内容摘要；`StandardRepositoryAdapter.requiredProjectId()` 依赖服务端 `CurrentProject` | 没有一次发布的全字段批量适用标准 SPI、独立可认证观察纪元、批量一致快照/提交 fencing 合同 |
| Semantic 状态修改 | `StandardCatalogService.changeStatus` 使用 `repository.updateStatus`，`update` 走标准版本 CAS；CODE 集合通过 `saveCodeSet` 和状态操作维护 | `StandardRepositoryAdapter.updateStatus` 与 `updateStatusByCodeSetCode` 只更新状态/时间，**不会保证每次业务变更都增加全项目单调 revision**；`StandardField.withStatus` 本身保留 version；不能把各行 `version` 或内容 SHA 当 ABA 证明 |
| Security | Semantic 按当前 `CurrentProject` 限定读，旧审批路径有原有业务校验 | 目前没有可跨审批提交与回调绑定、同时防止撤权再授权 ABA 的安全纪元和真实用户/角色范围签发合同 |

**推论：** 同一摘要、同一个 StandardField.version 或 Draft 候选 `consistencyToken` 均不足以证明“审批期间从未改动”或“最后检查之后到发布提交之间未发生撤权”。B2-S2 仍需明确的真实证据与持久协议。

## 2. 推荐技术选择（待统一负责人审定）

推荐采用**发布前强一致读 + 可验证的项目修订 fence**，而不是仅依靠审批时 SHA 与发布时 SHA 两次相等。

### 2.1 Semantic 项目级事实修订（拟新增；当前不存在）

- Semantic 拥有项目级单调 `semanticRevision: BIGINT`，按项目范围唯一，所有会影响发布字段合法性/适用标准的变更事务都推进它，包括 StandardField 定义/角色/引用/启停/删除、TYPE/UNIT/CALIBER/CODE/SECURITY 标准变更、CODE 成员增删/启停/重排/定义变化、审批启用与导入/批量路径。
- 新修订必须**同一数据库事务**与实际 Semantic 修改一起提交；修改前按全局规定获取项目修订行锁。修改→恢复原值至少推进两次；失败回滚不得消耗已提交 revision。项目没有初始化修订行、未知权限或多源读到不一致 revision 均 `UNKNOWN` / 拒绝。
- 单独的 `contentSha256`、行版本、时间戳和内存计数**不是**这个事实修订。原有历史版本保持其原摘要口径，不反向重算。

### 2.2 Semantic 发布只读批量 SPI（拟新增；不复用旧标准查询当成新合同）

推荐 `PublicationStandardEvidenceApi.evaluate(BatchRequest) -> BatchObservation`，由 **Semantic** 定义在跨模块合法 API 边界（具体接口类名为**提议**而非已有生产 API）：

- **Request**：来自 Modeling 模型根和真实 `StructureView` 的 `projectId / modelId / layer / physicalStructureSha256` 及完整列列表（规范列名、物理类型、`stdFieldId`、角色）；服务端权限上下文是独立真实输入，不由客户端 ID 授权。
- **Observation**：`VALID / BLOCKED / UNKNOWN`、观察 `semanticRevision`、digest 算法版本、完整字段级**安全脱敏**证据及规范总 SHA；每列固定 StandardField ID/版本/角色/状态、适用 TYPE/UNIT/CALIBER/CODE/SECURITY 的引用 ID/版本/类别/状态/摘要及 CODE 所有成员计数/启用数/版本算法/内容 SHA；输出与请求列集一一匹配。无字段或部分响应必须阻断。
- **权限**：Semantic 使用可信当前项目，明确同项目标准读取权限；跨项目不可泄露“存在与否”、成员数量或 SECURITY 策略原文；返回最小安全错误码，不暴露 `maskRule`。
- **三态**：供应方超时、快照无法验证、digest 算法不识别、修订行缺失、授权范围不明，一律 `UNKNOWN`，不能转换成 VALID/空列表。
- Semantic 保证批量快照在所选事务隔离级别下**不混合两次修订之间的字段事实**。读前/读后修订相等只是候选检查；是否采用行锁、MVCC 或完整串行事务需要实现层正式证明与集成测试。

### 2.3 最终发布事务 fence（必须二选一裁决）

**首选 A — 已验证的共享数据库事务 + Semantic 项目行锁**：
1. 先验证 Modeling 与 Semantic **实际使用相同底层数据源与 `yakBusinessTransactionManager` 事务上下文**；两个类使用同名注解本身不能证明共享物理事务。
2. 定义锁顺序、并发互斥与死锁重试策略。Modeling 获取模型根锁和完整 `StructureView`，再在*同一物理事务中*获取 Semantic 项目修订行的共享约定锁，直到新 ModelVersion + 证据快照 + 根指针提交。**所有 Semantic 相关写事务**按同一约定更新该行，才能实现锁式 fencing；未覆盖写入口或不同数据源即不成立。
3. Validator 在锁保护下读取适用标准，返回 `VALID`；历史版本复用判定必须比较 `standardEvidenceSha256 + algorithmId + semanticRevision`，不能只按旧结构/映射复用；新的标准快照与 ModelVersion 在 Modeling 同事务持久。事务任一步失败整体回滚，不产生幽灵发布。
4. 如真实连接/事务、锁顺序或写入口覆盖无法证明，**不能选择 A**。

**备选 B — Semantic 提供可验证的短期 publication fence/lease**：
- Semantic 通过明确的受信租约或条件提交协议验证修订号未变化，并保证 Modeling 提交期间写操作不能越过 fence；超时、租约失效或后端未确认返回 UNKNOWN。不能把简单 “重读两次 SHA 相同” 冒充 lease。
- 必须提交真实并发测试（Semantic 标准状态变更与 Modeling 发布竞态、进程中断、超时、ABA 和死锁），再决定能否启用；没有实现的第三方租约工具不能当作已存在能力。

**不接受 C — 只有最终双 SHA/双 epoch 比较而没有持久 fence**：即使刚检查完，下一毫秒仍可修改 Semantic，不能满足强一致发布保证。

### 2.4 Security 授权的独立决策边界

Semantic 的项目事实修订不能替代 Security 授权修订。Security 至少提供可信服务端 `subject/scopeDigest + authorizationRevision` 和授权回调重检，主体/角色/权限范围变更即撤销原审批资格；不能客户端自行签发或回传自由文本纪元。若权限源不在共享业务事务，Security 必须批准可验证的授权 fence/有效性策略；在缺少该能力时 **UNKNOWN fail-closed**，不可宣称严格消除了提交竞态。日志/ModelVersion 元数据仅存必要安全摘要、判断代码，不保存令牌原文、敏感规则或明文策略。

### R1 T2-A 实测进展及待验收项（2026-10-19）

- **已合并前 CI 验证的事实：** `c786f2c9` 与 `b8871d64` 各自四组 GitHub Checks 全绿；`DatabaseMigrationSmokeTest` 在具备隔离 MySQL 环境的 Architecture job 中实际执行 11 项，包含共用 `yakBusinessDataSource` / 两个 MyBatis Mapper / 同一 `yakBusinessTransactionManager` 的运行时身份核验、T1 修订行迁移/回滚/ABA/并发自增测试。证明的是**CI 部署下共用数据源与 Semantic 写方本身的锁**，不构成生产配置或跨域安全保证。
- **本次新增、独立待 CI 的读写互斥：** `SemanticPublicationRevisionService.lockExistingForReview()` 只在可信项目的真实业务事务中用 `SELECT ... FOR UPDATE` 读取已存在项目 revision，**不补造缺失行、不提供发布资格**；`b2PublicationRevisionObservationBlocksConcurrentMutationUntilCommit` 在真实隔离 MySQL 双事务中持有观察行锁时，断言另一事务不能提交修订写入，释放后才能自增。该测试只能证明同项目修订行的数据库互斥性，并不能证明其他 Semantic 表快照、Modeling 根锁、Security 撤权均已纳入同一 fence。
- **F-039 代码审查：** `SemanticAdoptionItemWriter.apply` 针对 `FIELD` 使用 `fields.create`，针对 `PROCESS_FIELD` 使用 `fields.bindToProcess`，进入当前已接修订推进的 `SemanticFieldService`；已成功回执的幂等重放不执行新字段变更。F-039 的 `SOURCE_LINK` 调用 `SemanticProcessBindingService` 改变来源绑定，属独立业务来源语义，需要明确是否影响 B2 发布适用标准并决定是否加入修订协议。
- **尚未证明 T2 GO：** 依旧缺 Modeling 实际根锁→Semantic 项目修订行锁、Semantic 项目修订行锁→跨模块引用查询的**端到端资源锁关系**证明。特别是 `StandardCatalogService.delete/changeStatus` 在修订锁之后检查 Modeling 等消费者引用，必须证明消费者只读不会获得逆向根锁，或调整统一锁序再提交真实双 Worker 竞态测试。即使本次 MySQL 行锁互斥测试通过也不得自动打开 #635 生产接线、提交 fence 或审批资格。

## 3. 四入口统一执行顺序（正式实现验收要证明）

1. **常规直接**：`ModelVersionService.publish` → root lock → 真实结构快照 → 共同 validator/fence → 原 DWS 等分层校验 → 标准身份参与幂等复用/新版本 → 原子提交。
2. **常规审批**：`ModelPublishApprovalService.submit` 时冻结结构/标准/Semantic revision/授权范围与版本；`ModelPublishApprovalHandler.onApproved` 时对当前设计及 Semantic/Security 重新核验 → `ModelVersionService.publishApproved` 共用发布边界。
3. **DWD Mapping 直接**：`DwdMappingPublicationService.publishChecked` 冻结 MappingSet/Revision/LogicalSnapshot，并确保**已发布同修订提前成功回执**不能绕过标准授权复核；进入共用 ModelVersion 事务。
4. **DWD Mapping 审批**：`submitApproval` 冻结映射 + 标准 + 授权身份，`publishApproved` 回调需同时重核全部身份，进入同一共用门禁。
5. **ODS**：明确贴源例外，不强制业务标准，但仍执行既有权限和结构合同；不得把 ODS 例外泛化为非 ODS 开关。
6. **历史**：旧 ModelVersion `LEGACY_UNBOUND` 不补历史假证据；标准修订后不会改旧快照；恢复到草稿并再次发布必须重新取当前事实。

## 4. 已有条件批准的 T1–T4 与实施验收关口

| 决策编号 | 推荐 | 必需证据/可执行门槛 |
|---|---|---|
| T1 Semantic revision | 采用同项目单调持久修订，覆盖所有 StandardField、TYPE/UNIT/CALIBER/CODE/SECURITY 变更 | 逐变更 API/Repository 写入点清单，MySQL/PostgreSQL 迁移 + 并发/ABA/回滚测试 |
| T2 原子 fence | 优先 A（共享真实事务 + 修订行锁）；不满足则设计 B（可信 lease） | 数据源实际配置及锁顺序审查、线程竞态和死锁测试；否则 NO-GO |
| T3 Security revision | 后端可认证主体/授权范围及权限修订，审批和回调双重检查 | 跨项目、权限撤销→恢复、角色变化、错人审批、日志脱敏测试 |
| T4 ModelVersion 身份 | 四入口共用标准 validator，标准证据参与版本复用，新版不可变快照与根指针同事务 | 旧版、标准漂移、同结构不同证据、审批/Mapping 两路径回归、断电/失败回滚及真实读回 |

**正式决策：`ACCEPT_CONDITIONAL_FOR_DEVELOPMENT`（技术 R1，2026-10-19）。** 用户作为统一技术负责人已明确授权 T1–T4 进入真实研发实施，但 T2 的**真实共库、同事务、锁顺序与全写入口**必须先行验证。验证失败禁止绕过，也不得擅自切换 B 方案；需要重新裁决可信 fence。T2 证据不足时，仍不可从 Draft 提升 Ready、接线生产发布门禁或合并。该批准**不**允许 DataDev、Runtime、SQL/DDL 或目标数据写入。

## 5. 里程碑与验收断言

- **B1-R1 技术定稿门槛**：T1–T4 一次明确版本化裁决，实际 Semantic/Security 上游合同与事务模式有可运行证据。Source review/CI 是评审证据，不是 E2E。
- **B2 研发门槛**：一个纵向 #635 解决可信 SPI、四入口、ModelVersion 不可变元数据、审批冻结、MySQL/PostgreSQL 与工作台错误恢复，不能把零散候选单测当成全部完成。
- **B2 验收**：按 [B2-P01–P18 ↔ DR06-P01–P18 对照](R14-B2-publication-evidence-feature-spec-candidate.md) 分别计数，至少包括：一致性 ABA、撤权/恢复、缺标准技术列、CODE 部分禁用、同结构不同标准摘要、双事务竞态/重试、跨项目/错误身份、安全快照、旧历史/ODS 分支，以及现有 DWS/ADS 专项门禁不回退。
- **DR-07 仍独立**：不启用 DataDev TaskRevision、Runtime、TargetPolicy、SQL/DDL 或目标写入；也不以设计发布宣称目标质量或消费交接完成。
