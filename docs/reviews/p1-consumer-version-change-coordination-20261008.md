# P1 · 消费者版本变更协同 — 现状、切片与决策门禁（2026-10-08）

> 文件类型：Productization / Implementation Review（**非正式 Product Decision，不替代 F-004 或源域合同**）
> 当前基线：data-ops main `ffb9d8eff1afba16a6aa81721d8d3dad5dd5918a`（#393 已合并）。
> 上位路线：[总路线 #180](https://github.com/gitfortian/data-ops/issues/180) P1 E3/E4；Golden 验收 [#336](https://github.com/gitfortian/data-ops/issues/336)。
> 有效产品依据：PD-002（ACCEPTED）及 F-004（APPROVED）。PD-005/006/007 当前均为 PROPOSED，不构成实施授权。

## 一、场景与真实基线

Owner 准备变更 DatasetVersion 或 Data Service SourceRevision，希望知道“哪些人明确用过将被改变的版本，哪些人只是声明了依赖，我如何通知并让他们核对？”，而不是从全局总次数猜测影响。
- #386 已提供 Data Service 来源持久调用审计的 Project/API/Invocation ID 精确回链。
- #393 已在 Canonical Consumption Detail 用 SourceVersion.identity 精确聚合本次窗口成功使用，区分 ACTIVE 订阅（不绑定版本）与实际调用，失联明确 PARTIAL，支持复制非持久的影响核对快照。
- `ConsumerImpactService` 使用 `CurrentProject` 与已有 Usage/Subscription；`observedVersions` 来自 Source-owned Dataset/Service 冻结版本；最多取本次 200 条，**不能视为全历史或全部消费者**。
- 已读取 `data-ops-business-approval/DOMAIN.md`、`REQUIREMENTS.md`、`ARCHITECTURE.md`、`DEPENDENCIES.md`：Approval 是 1～2 级、串行、同事务业务回调、按 Project 区分的一套正式审批，要求合法 flow/handler 才能发起；不提供“给每个 Consumer 任意发送消息并自动得出确认”的能力。
- Audit 的 `/api/v1/audit/operations` 提供既有审计操作读侧，不意味着页面剪贴板动作会产生可信审计。ConsumerRef 仅有 type/domain/identity/displayHint，**不是可自动送达的联系方式**。

## 二、冻结本轮可做范围（P1 E3，复用既有合同）

**用户结果：** 不需要跨多个页面手工拼文字；Owner 在核对具体版本时按已知 Consumer 一键生成沟通草稿，带上准确的 Product / Project / SourceVersion / 证据范围；自行找到沟通渠道联系对方。

1. 在既有“版本变更前 · 已知消费者影响核对”卡片中录入本次**拟变更内容**（仅浏览器暂存，不属于已批准变更），并对每位已知 Consumer 生成独立纯文本草稿。
2. 文案保持基于 ConsumerRef 的稳定身份，精确版本 ID 必须用字符串；只引用该 Consumer 对该版本的成功 Usage Evidence，不能混入其它版本/主体的日志。
3. ACTIVE 声明依赖且目标版本无成功 Usage 的 Consumer 应表达为“已知声明依赖、未观察到该版本使用”，不能直接说“正在使用此版本”。
4. 每份草稿必须包含窗口边界及 PARTIAL / FORBIDDEN / UNAVAILABLE 说明；目标版本无证据或没有已知 Consumer 时，不创建假名单或宣称无影响。
5. 用户勾选已看过证据覆盖缺口后才允许复制；刷新或改变版本/Impact 清除勾选。复制失败明确提示，不承诺已联系/已发送/已确认。除本地 React 状态和剪贴板外无服务端写入。
6. 该切片不新增：数据库、REST、Consumer 联系信息映射、邮件/站内通知、持久计划、Approval、Audit、发布门禁、生命周期 transition。

**复用：** `reviewVersionImpact`、`VersionImpactRow`、`ConsumptionImpact`、`SourceVersionRef`、既有 Source Evidence Link；不修改 Consumer/Usage Truth。

## 三、后续拟议阶段与必须先决策的边界

| 阶段 | 用户要完成的任务 | 当前结论 | 决策 / 开发前置 |
| --- | --- | --- | --- |
| E3-a（#393，已合 main） | 看清版本级 known impact | 已实现，真实 E2E 待补 | PD-002 / F-004 |
| **E3-b（本 PR）** | 按已知 Consumer 准备人工沟通草稿 | 可做，只读/复制，不声称联系完成 | PD-002 / F-004 |
| E3-c（待评审） | 由消费者本人确认“兼容/需迁移/无法判断”并保留可复查轨迹 | **禁止先建第二套确认 Truth** | 明确 Consumer 主体身份/Owner、Project 鉴权、Confirmation 归属、幂等、版本 CAS/失效规则、撤回、retention、Audit 义务、通知渠道 |
| E4（待评审） | Owner 给出弃用/退役、替代方案、时间与发布/执行阻断 | PD-007 仍 PROPOSED | PD-007 ACCEPTED + source-owned Dataset / Data Service Domain & Feature 规则；如引入正式批准需复用 Approval 的 Flow/Handler 和精确版本快照 |

### E3-c 关键未决项（不可由 UI 勾选替代）

- 谁有权代表 DATA_SERVICE_CONSUMER、JOB、DASHBOARD、USER 或 TEAM 确认？项目管理员能否代确认，如何留痕？
- 确认绑定何种不可变变更输入（来源对象 ID、old/new exact version、变更字段指纹、计划窗口、actor、当时已知影响和 evidence watermark）？发布定义再次变化如何自动使旧确认失效？
- “已通知”“已读”“已回复”“无影响”“接受兼容风险”“已迁移”“Approval APPROVED”是不同事实，分别由哪个域拥有？哪些是产品必需，哪些只是沟通便利？
- 消费者无法定位 owner 或证据 provider 不可用时如何显示 UNKNOWN/PARTIAL，而不是误为拒绝或通过？
- 通知是否使用现有 Alert/渠道？允许的收件人/联系方式来源是什么？有无审计义务、期限和隐私要求？
- 强制发布拦截、DEPRECATED/RETIRED/迁移是 PD-007 关联的跨源域行为，不应由 Consumption 单独写发布状态。

## 四、本轮代码/验证清单

- [ ] 仅前端生成按 Consumer 的人工沟通草稿：无 Source Version number coercion，无不同消费者证据串用。
- [ ] 同一显示版本号但不同不可变 identity 是不同草稿依据。
- [ ] 只有声明依赖、只有真实使用、两者都有、无 Consumer、Provider UNAVAILABLE/FORBIDDEN、剪贴板失败都有清晰语义。
- [ ] 产品信息与目标版本在沟通稿中不可丢；变更内容由 Owner 显式填入，空值标注“待明确”，绝不伪造日期/兼容承诺。
- [ ] CI 实际结果使用对应 PR head 的 Product Guard、Consumption Checks 与 Architecture Checks；失败不当 PASS。
- [ ] #336 的真实 E2E 保持 PENDING：登录用户、Project 边界、角色权限、BIGINT 来源、来源故障、手动沟通稿复制、目标 Version 切换和回链；不用 Mock/Jest 代替。
- [ ] 发布批准、持久确认、已发送状态保持未实施，直到正式产品决策完成。

## 五、验收与成效

功能验收关注：正确人/正确版本/正确来源证据/正确缺口语义、可复制且不冒充已发送，项目间信息不得泄露。

用户成效关注：Owner 为一个版本准备人工沟通材料需要的操作次数、需要额外查阅的页面次数、草稿是否能被真实 Consumer 理解；首轮实测后建立基线，不虚构改善百分比。

本 Review 不修改 PD-002/PD-007 的正式状态，不关闭 #180/#336。
