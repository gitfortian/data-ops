# Issue #285 审批功能现状复核与评审意见

日期：2026-10-04
评审对象：[GitHub issue #285](https://github.com/gitfortian/data-ops/issues/285)
代码基线：本地 main / 本地 origin/main 均为 `b082228b1ae0e0261c87a746f9e8809748098294`。未额外 fetch，不能据此断言远端实时 HEAD 与本地相同。
材料性质：Review / Evidence，不是 Product Decision 或实现授权。

## 1. 评审结论

**建议修订后拆分；不建议直接按原 issue 的“后端完整，下一阶段补前端工作台与企业功能”执行。**

通用审批的基本模型、同步业务回调和审批/审计分域方向合理，现有工作台也已经存在。当前优先项是查询权限、送审事实与业务生效的一致性、回调依赖失效时的处理和失败审计。会签、条件分支、转交、委托、超时、通知等应作为候选需求重新评估，不能由“模块还不完整”直接推导实施。

本报告沿两条轴独立给出意见：工程契约 5 项；需求与产品契约 3 项。数据库并发及回滚证据另列，未把未经实测的锁竞争风险写成已复现缺陷。

## 2. 先明确用户闭环与事实归属

| 问题 | 当前评审口径 |
|---|---|
| User / 用户 | 业务申请人、指定审批人、流程管理员；具体包括主数据治理、标准生效、模型发布、访问授权及资产上架相关用户 |
| Problem / 问题 | 用户需要确认“我审批的内容是什么、是否仍是送审内容、结果是否真的生效、失败后如何恢复”，同时避免无关人员读到审批依据 |
| Capability / 能力 | 复用现有通用审批，完成业务变更的审查与生效闭环 |
| User Journey / 旅程 | 源域提出申请 → 指定审批人待办 → 阅读送审依据 → 通过/拒绝/撤销 → 源域生效或保持不变 → 回看审批与审计证据 |
| Expected Outcome / 结果 | 被批准的业务内容与审批依据一致；拒绝/撤销不产生生效；失败明确可重试；当事人能核对结果 |
| Truth Owner / 事实所有者 | Approval 拥有流程、实例、步骤和审批决定；源业务域拥有对象、业务版本和实际生效事实；Audit 拥有操作留痕 |
| Producer / Consumer | 源域生产申请和依据，流程管理员生产配置；审批人消费依据并生产决定；源域 handler 消费决定；工作台消费查询投影 |
| Existing capabilities / 复用 | ApprovalApi / ApprovalFlowHandler、CurrentProject、RBAC、BusinessAuditService、源域版本与变更能力；未来通知先评估已有 NotificationRouter / NotificationPublisher |
| E2E acceptance evidence / 证据 | 不只看页面和接口存在，还需串联审批实例、步骤、源域生效版本/策略/记录、审计记录及权限失败结果 |

依据：[PRODUCT_STYLE](../../PRODUCT_STYLE.md)、[Approval DOMAIN](../../data-ops-business/data-ops-business-approval/DOMAIN.md)、[F-007](../../docs/product/features/F-007-mdm-record-correctness.md)、[F-008](../../docs/product/features/F-008-task-oriented-navigation.md)。

相关有效基线包括 ACCEPTED PD-001/PD-002，以及 APPROVED F-001/F-005、IMPLEMENTING F-007/F-008。PD-005 仍为 PROPOSED，不能把其质量例外审批设想当成已批准的本期要求。

## 3. 原 issue 哪些判断成立，哪些需要更新

| 原 issue 内容 | 当前代码事实 | 评审意见 |
|---|---|---|
| Flow / Instance / Step，ANY，串行，审批人快照 | 已有实现；当前仅 1～2 级，每级 1～10 人 | 保留评价，但写出真实限制，不暗示任意复杂流程 |
| 同一业务对象同流程在途唯一 | 预检加项目/流程/业务/active_flag 唯一键 | 机制存在；并发有效性仍需真实 DB 验证 |
| 通过、拒绝、撤销与同事务业务回调 | 已有应用服务和 SPI | 保留架构方向，补失效依赖、送审绑定及失败证据 |
| 我的申请、我的待办、详情尚需补齐 | 已有“我的待办、我发起的、我审批过的”及详情、轨迹、意见、通过/拒绝/撤销 | 原描述过时，应改为既有路径的正确性与体验收尾 |
| 快速/批量审批属于 P0 | 当前待办进入详情处理；没有批量实现 | 批量是候选增强，先证明实际频率和价值，定义逐单授权、失败、重试及意见语义 |
| ALL / 分支 / 加签 / 转交 / 委托 / 超时 / 通知为 P1 | 当前 REQUIREMENTS 明确列为范围外池 | 原 issue 不能覆盖当前契约；需求成立后另行决策/Feature |
| 流程版本管理为 P2 | 已有实例流程名、审批人快照；没有完整配置版本治理 | 应区分“在途冻结”和“流程配置版本管理”，不要重复建设已有快照 |
| 审批与审计不合并 | 已通过 BusinessAuditService 和提交后审计机制协作 | 方向成立，不需要由“事件协作”额外推导新事件总线 |
| 业务集成体验后续加强 | 已接入模型、标准、授权、资产、MDM | 应按具体旅程核对，尤其批准内容与生效内容是否一致 |

工作台代码：[三个视图](../../data-ops-ui/src/pages/approval/todo/index.tsx#L307)、[详情与审批依据](../../data-ops-ui/src/pages/approval/detail/index.tsx#L171)。导航：[我的待办与流程配置](../../data-ops-ui/src/config/navigation.ts#L161)。

F-008 已将我的待办设为工作入口、流程配置放在平台设置，不应根据 #285 再新建一级审批中心。F-007 沿用 MDM 变更与 Approval 的现有边界；MDM 变更台账不是要退役的第二个流程引擎。

历史 `docs/approval/config-driven-integration.md` 有新增接入配置表、页面和开关的方案；这些内容不在当前 ApprovalApi 与三表实现中，也不是本次发现的现状能力。它属于历史设计证据，不能以“最终选型”的标题代替当前 APPROVED Feature 授权。同样，资产注释明确允许直上架和审批路径双轨，不能把它擅自解释为所有资产发布已经具有强制审批门禁。

## 4. 工程契约轴（Standards）

### E1 · P1：by-biz 绕过审批依据的当事人可见性

证据：[ApprovalController.byBiz](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/controller/v1/ApprovalController.java#L133)、[ApprovalService.find](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/application/ApprovalService.java#L157)、[detail 可见性检查](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/application/ApprovalService.java#L189)。

`GET /api/v1/approvals/by-biz` 仅通过 read 权限进入，直接调用不接受 operator/manage 参数的 find。返回 ApprovalInstanceView，包含 payloadJson、标题和申请人。detail 则明确校验申请人、任一级审批人或 manage 权限。

**触发场景：** 同项目用户有 data-approval:read，却不参与某单，也没有 manage；已知或枚举其 flowCode/bizType/bizId，通过 by-biz 可读到被 detail 拒绝的审批依据。对于 MDM 或授权申请，这可能包含具体变更和授权范围。

**最小修复边界：** Approval 的对外查询边界统一检查可见性。内部业务 SPI 查询与面向用户 REST 查询可以有不同契约；若业务页面只需要状态，定义受权限约束的状态投影，避免把完整 payload 带出。不能简单地把所有审批人之外的业务状态查询都关闭而破坏现有消费者。

**验收：** 同一单、同一用户通过 detail 和 by-biz 具有一致的依据可见性；覆盖申请人、本级/后级审批人、管理员、无关用户、跨项目。未授权响应不得残留 payload。

### E2 · P1：handler 缺失时仍然提交审批终态

证据：[terminalCallback](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/application/ApprovalService.java#L413)、[registry.require](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/registry/ApprovalFlowRegistry.java#L42)。

提交时检查 handler 注册，终态时却使用 `registry.find(...).ifPresent(...)`，找不到时静默跳过。运行期间注册表缓存不会自行消失；风险来自存在在途单的部署中移除/关闭相关业务 handler，再重启服务。

**影响：** 单据可成为 APPROVED，active_flag 被释放，但源域没有执行 onApproved。终态不可逆，用户无法靠重试原单补齐业务生效，违反 D5/F7 的业务回调闭环。

**建议：** 在提交终态的同一事务中要求 handler 可用；缺失时阻断并回滚，保留在途状态和诊断。拒绝/撤销的 handler 依赖也应由明确契约表达。无需引入异步回调新状态机。

**验收：** 在途单对应 handler 缺失时，批准不产生终态、不释放唯一占用、不产生业务写入；恢复 handler 后可重试一次并生效。

### E3 · P2：回调失败时缺少可靠的审批语义失败审计

证据：[先回调后审计](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/application/ApprovalService.java#L345)、[audit 开账](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/application/ApprovalService.java#L499)、[业务异常转换](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/exception/ApprovalExceptionHandler.java#L44)、[HTTP 审计兜底判断](../../data-ops-boot/src/main/java/io/yak/ops/boot/audit/AuditWebInterceptor.java#L119)。

approve/reject/cancel 的语义审计在 terminalCallback 之后才 start。handler 抛错时，Approval 层尚未建立对应操作的失败账。业务异常被 MVC 转换为普通 Result；若 handler 也没有开业务账，HTTP 兜底仅依据异常和 HTTP status，存在业务失败却记成功的路径。若 handler 已开账，兜底还可能因 ledger 防重复而跳过，不能保证留下 Approval 动作的失败证据。

**证据限制：** 审批开账遗漏可直接从顺序确认；HTTP 兜底结果是静态链路推导，本次未执行 MVC/数据库复现。

**建议：** 在 Approval 动作的拥有边界建立操作账，复用既有事务提交/回滚与 failure 机制，记录 callback 失败和业务定位。不要仅依赖 HTTP 200，也不为本问题重造审计系统。

**验收：** handler 强制失败后，审批与业务事实整体回滚，同时留下准确的失败操作；恢复后重试成功产生成功证据。

### E4 · P2：分页入口可变成无界查询

证据：[分页 DTO](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/controller/v1/dto/ApprovalRequests.java#L47)、[todo 分页参数直传](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/application/ApprovalService.java#L203)、[流程全量 list](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/application/FlowAdminService.java#L42)、[分页插件配置](../../data-ops-boot/src/main/java/io/yak/ops/boot/config/persistence/BusinessDatabaseConfiguration.java#L95)。

pageNo/pageSize 无最小值、最大值约束，服务端直接构造 Page，分页插件没有 maxLimit。项目所用 MyBatis-Plus 3.5.16 的本地源代码确认：负 size 且无 maxLimit 时 beforeQuery 不构造分页 SQL。因此请求 pageSize=-1 能退化为本人的全量列表。流程管理本身也用 selectList 全量返回。

这与 REQUIREMENTS 的“列表全分页”冲突；列表投影还携带 payload，大量审批记录会放大数据库、内存和传输开销。

**建议：** DTO 和应用边界使用有界页参数；流程管理列表按已约定范围分页。业务下拉等消费者确需有限列表时明确数量预算，而不是复用无界后台列表。

**验收：** 负值、零、过大值被拒绝或明确归一；多页结果和总数正确；受支持调用无法取消 LIMIT。

### E5 · P2：合法最大长度 flowCode 无法软删除

证据：[允许最长 64 字编码](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/controller/v1/dto/ApprovalRequests.java#L27)、[删除时拼接后缀](../../data-ops-business/data-ops-business-approval/src/main/java/io/yak/ops/business/approval/application/FlowAdminService.java#L139)、[flow_code VARCHAR(64)](../../data-ops-business/data-ops-business-approval/src/main/resources/db/migration/yak-approval/V1__create_approval_tables.sql#L7)。

合法 64 字编码删除时变成 `code#del#id`，必定超出字段容量；严格数据库模式下删除失败，非严格模式也可能截断，不能保证稳定的编码释放语义。

**建议：** 在 Flow 的持久化拥有边界选择容量内、唯一的删除占位编码，或通过明确迁移调整容量。保持历史审批实例的原编码快照不变。

**验收：** 最大长度编码能创建、删除、重建；重复删建不碰撞，历史实例仍可解释。

## 5. 需求与产品契约轴（Spec）

### S1：issue 的现状、P0 范围和增强优先级需要重写

工作台与详情已存在，不应再次以新建三个页面作为交付成果。快速操作和批量操作并非现有基线的缺陷；尤其批量审批需先证明用户规模、风险和收益。

会签、分支、转交、委托、超时和通知，与 [当前范围外池](../../data-ops-business/data-ops-business-approval/REQUIREMENTS.md#L18) 明确冲突。它们可作为候选需求保留，需求成立后走现有产品治理流程；#285 本身属于 review evidence，不能直接覆盖当前领域契约。

建议明确当前整改目标为“既有审批闭环正确且可解释”，保留现有导航与所有权。不要因本轮审核扩大到表单引擎、外置流程引擎或第二份业务真相。

### S2 · P1：送审内容与实际生效内容未绑定

**访问授权：** [送审展示快照](../../data-ops-business/data-ops-business-security/src/main/java/io/yak/ops/business/security/approval/AccessPolicyApprovalService.java#L62) 保存主体、资源、动作、effect 等；[AccessPolicyService.update](../../data-ops-business/data-ops-business-security/src/main/java/io/yak/ops/business/security/application/AccessPolicyService.java#L66) 仍允许在途对象编辑并保持 PENDING；[授权回调](../../data-ops-business/data-ops-business-security/src/main/java/io/yak/ops/business/security/approval/AccessGrantApprovalHandler.java#L32) 只读取 policyId，然后批准当前策略。

**触发场景：** 已获 UPDATE 权限的用户先把 TABLE 范围策略送审，再直接调用更新 API 将其改为 ALL；审批详情仍展示原 TABLE 范围，批准后当前 ALL 策略变成 APPROVED 并参与裁决。隐藏 PENDING 编辑按钮不能承担服务端一致性约束。

**模型发布：** [模型送审 payload](../../data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/approval/ModelPublishApprovalService.java#L40) 仅含 ID/名称；[模型回调](../../data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/approval/ModelPublishApprovalHandler.java#L35) 调用 publish，[发布时读取当前结构](../../data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/version/ModelVersionService.java#L61)。前端禁用在途保存/回滚，但 [结构保存服务](../../data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/structure/ModelStructureService.java#L79) 没有审批守卫，直接 API 仍能修改。结果是提交 A 后改成 B，批准原单却发布 B。

**最小修复边界：** Security 和 Modeling 各自绑定送审版本、不可变快照或内容指纹，并在业务生效事务内锁定/校验；改变内容后拒绝旧单生效并要求重新送审。Approval 负责携带关联材料，不成为模型结构或访问策略的第二个 Owner。仅增加前端禁用或非原子的“先查询是否在途”不足以保护并发。

可参考已存在的 [Standard 送审版本锁定与校验](../../data-ops-business/data-ops-business-semantic/src/main/java/io/yak/ops/business/semantic/approval/StandardPublishApprovalHandler.java#L33)，但具体版本语义由源域决定。

**验收：** 送审后通过 API 编辑及并发编辑/批准，必须阻断旧依据对应的新内容生效；未改变内容可以正常批准；错误后审批状态与业务状态仍一致。

### S3 · P2：审前依据可理解性和源域回链不足

证据：[业务对象只有类型和 ID](../../data-ops-ui/src/pages/approval/detail/index.tsx#L176)、[payload 原始键值渲染](../../data-ops-ui/src/pages/approval/detail/index.tsx#L183)。

已有详情将任意 payload 用英文键和 JSON 字符串展示，没有源业务对象链接。模型/资产依据主要为 ID/名称；MDM 内容展示也缺少直接帮助判断的旧值/新值差异。用户有“通过”按钮，却未必拥有充分的决策依据。

**建议：** 先围绕 MDM 变更和访问授权两个已有场景补充人可读的依据、送审版本提示、实际生效结果和回链。源域提供事实与受权限约束的稳定链接；既有详情负责承载。不要复制业务对象当前状态到 Approval 当作真相。

撤销还有具体体验差异：UI 提示原因会进入审批记录，后端只把原因传给 callback/audit，detail 的 instance+steps 不返回撤销原因。因此也需明确详情读取已有审计证据或修正文案；不能因 Step 充当历史记录就认为它已覆盖全部操作流水。

**验收：** 审批人能看懂本次改了什么、依据哪个版本、通过后在哪里核对；无源域权限时仍符合送审依据可见性，并对业务深链正确拒绝访问。

## 6. 证据、风险和不应误判的内容

本次运行现有模块测试：

```powershell
mvn -pl data-ops-business/data-ops-business-approval -am test '-Dtest=ApprovalServiceTest,FlowAdminServiceTest,ApprovalStateMachineTest,FlowStepsCodecTest,ApprovalFlowRegistryTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false'
```

结果：**5 类、34 项全部通过，0 failure / 0 error / 0 skipped，BUILD SUCCESS。**

| 测试类 | 数量 |
|---|---:|
| ApprovalServiceTest | 11 |
| FlowAdminServiceTest | 9 |
| ApprovalStateMachineTest | 1 |
| FlowStepsCodecTest | 10 |
| ApprovalFlowRegistryTest | 3 |

这些是现有单元测试。应用服务测试直接 new service，Mapper/handler 使用 Mockito；抛出 49009 的测试证明错误传播，不能证明 Spring 代理、真实事务管理器、业务写入与步骤写入整体回滚。“双击只成一次”也只模拟 update 返回 0，不能证明真实数据库并发。

需要补的真实 DB 证据：

- 同级两个审批人并发 approve，以及 approve/reject 竞争；
- cancel 与 approve/reject 竞争；
- 同业务并发 submit 的唯一键竞争；
- handler 写入业务后抛错的整体回滚；
- handler 缺失以及重试；
- DTO 边界与持久化长度边界。

静态审查发现 decide 先锁自己的 step 再更新同侪，cancel 先更新 instance 再清理 step，存在相反锁序和死锁风险。**本次未运行 MySQL 并发复现，不据此断言两人都能批准或业务回调会重复执行。** 应用事务回滚与数据库锁本身可能维护最终一致性，但错误是否符合约定、恢复体验和吞吐仍须验证。

仓库已有 2026-10-02 的工作台/详情截图及文本，可以支持“已有页面”的判断；它们不是本次新跑的完整审批 E2E。当前 main 包含代码，也不能仅凭此将相关 Feature 标记 SHIPPED。

## 7. 建议的拆票与准入

| 次序 | 范围 | 完成标准 |
|---|---|---|
| 1 | Approval 查询权限、必需 callback 依赖 | 无关用户无法读 payload；handler 缺失不产生虚假终态 |
| 2 | Security/Modeling 送审内容绑定 | 批准与送审内容一致；直接 API 和并发修改无法偷换内容 |
| 3 | 审批失败留痕及 DB 集成证据 | 回滚状态、业务状态、审计结果一致；竞争场景结果可解释 |
| 4 | 分页与软删除边界 | 无界分页不可达；合法最长编码可删可重建 |
| 5 | 已有详情的依据/回链体验 | 在至少一个真实源域闭环中可核对内容与生效结果 |
| 单独候选 | 批量、通知、会签、分支、委托、SLA | 有用户问题和成功信号；产品决策/Feature 批准后再拆实现 |

通知候选先评估仓库已有 NotificationRouter、NotificationPublisher 与渠道能力，同时遵守 Approval 的依赖白名单；如需新增依赖，先评审。不能在批准事务内直接发外部邮件/企业消息，避免业务回滚后仍通知成功或外部调用拉长事务。

## 8. 最小 E2E 验收清单

1. **MDM 正常路径：** 创建变更 → 指定审批人待办可见 → 核对字段变更依据 → 两级 ANY 正确推进 → 批准后主记录/版本实际更新 → 来源重跑保留获批字段 → 审批与源域可回看。复用 F-007 场景。
2. **访问授权正常路径：** PENDING 策略送审 → 审批人核对主体/范围/动作/有效期 → 批准 → 实际裁决只按被批准内容变化。
3. **内容变化：** 送审后直接 API 修改模型/策略，并与批准竞争；旧依据不得授权/发布新内容。
4. **拒绝/撤销：** 单据终态，剩余步骤 SKIPPED；没有业务生效；理由和真实操作者可追溯。
5. **回调失败：** 业务写入后制造异常；审批单、步骤和源域全部回滚，失败留痕；恢复后一次重试生效。
6. **配置快照：** 发起后改审批人/改流程名/停用流程；原单继续使用原审批人和快照，新单按新配置或被停用阻断。
7. **权限隔离：** 无关同项目用户、跨项目用户、仅 read 用户、本级/后级审批人、申请人、manage 用户，分别验证查询与动作。
8. **并发与边界：** 双提交、同级竞争、撤销竞争、负页大小、最大长度编码删建；保留数据库事实与错误结果。

每条证据至少关联 instanceId、stepId、flowCode、bizType/bizId、projectId、源域版本/状态和 audit operation。截图可以辅助说明，不能代替持久化及实际消费/生效检查。

## 9. 可用于 issue 修订的评审意见

建议将 #285 改为“审批既有闭环正确性与使用体验收尾”。已有基础模型、工作台和 SPI 可以保留；先完成非当事人依据访问控制、终态 handler 必需校验、访问策略/模型送审事实绑定、失败审计及数据库竞争/回滚验收。详情体验围绕具体业务依据与回链收敛。会签、转交、通知等移入待产品评估候选池，不由本审核直接授权新增状态机、一级入口或第二份业务真相。

工程轴共 5 项，最高为 P1 的依据越权读取及缺 handler 仍终态；需求轴共 3 项，最高为 P1 的送审内容与生效内容不一致。
