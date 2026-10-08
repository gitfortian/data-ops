# P1 · Consumer 来源对象核对回链与正式确认前置条件（2026-10-08）

> 类型：Code-backed Review / Evidence，**不是新的 Product Truth、不是 PD-008 的 ACCEPTED 决议**。
> 基线：data-ops main `859ff65c8968a56f42f8a68b2ee3d3ab00bbfecc`（PR #401 已合并）。
> 当前约束：PD-002 ACCEPTED / F-004 APPROVED；PD-008 PROPOSED / NOT_STARTED；PD-007 PROPOSED；#180 路线与 #336 Golden E2E。

## 1. 本次代码定位

| 来源 | 真实代码与路由 | 能实施的安全操作 | 不能推断的内容 |
| --- | --- | --- | --- |
| Data Service managed Consumer | `DataServiceUsageEvidenceNormalizer` 的 `ConsumerType.DATA_SERVICE / DATA_SERVICE_CONSUMER / consumerId.toString()`；`DataServiceConsumerManager` 的项目隔离检索；`/data-service/access` 管理页 | 只对上述 type/domain 和可无损转为 JS safe integer 的正整数 ID 生成 `/data-service/access?consumerId=<id>`；专业管理页从其当前 Project Consumer 列表**精确命中**，列表无记录/ID 不安全显示不可核对。入口已有 `data-service:access` 权限 | API Key 不等于人；拥有密钥管理权限不等于有权代表 Consumer 进行正式变更签收；目标 Consumer 可能已经删除或被撤权 |
| Dashboard Consumer | `ConsumerType.DASHBOARD` / sourceDomain `DASHBOARD`；已有 `/dashboard/:id` Viewer → `/dashboard/:id/edit?preview=1` | 只生成正整数安全身份的现有仪表盘 viewer 链接；当前 Viewer 与 Backend 实施实际项目检查 | Dashboard 页面存在不等于其 owner / 代理人已授权，不能发出确认 |
| Job Consumer | Offline/Realtime/Scheduler/Workflow 多种来源及 `/sync/*` 路由，`ConsumerRef.sourceDomain` 未冻结跨这些来源的稳定 route 映射 | 保留完整 `consumerType:sourceDomain:sourceIdentity`，显示“需人工核对”，不根据名字猜任务类型 | 不用 JOB 类型硬映射所有任务详情；不从 Project Owner 猜签字权 |
| USER/TEAM | `ConsumerRef` 只有 stable identity 和可选 displayHint；Project Security `YakSecurityProjectAccessGuard` 仅验证项目成员/Owner 可见性 | 保留源身份，避免从用户名构造 `/system/users/:id` 或无凭据消息地址 | 不能据此证明用户/团队代理权限、电子邮件或授权审计 |

### 数值与鉴权边界

- `DataServiceConsumer` 前端现有 API 使用数值型 `id: number`，对于 Source 的 `Long` 身份如果超过 JS `Number.MAX_SAFE_INTEGER`，**严禁**经 Number 近似后导航到其他 Consumer。暂只生成严格正整数、canonical decimal、无损且安全的链接。
- URL 参数不创建访问许可；原专业页面继续复用 `data-service:access` 路由与来源 Controller/Project Scope；空响应、无权限或删除显示“不能核对”，绝不把相近 ID、显示名称、列表首项当成目标。
- `dashboard/:id` 现有只读预览仍由原始路由/数据接口负责可见权限。导航仅提供 convenience，不生成同意/已联系/已读记录。
- 消费 Impact 的 Usage、Subscription、Lineage Truth 不变；回链只由**精确 ConsumerRef Type + SourceDomain + identity** 推导。

## 2. 本批产品可见变化

- Canonical Consumption 详情的已知消费者清单、Owner 版本变更影响核对表为可靠的 DATA_SERVICE Consumer / DASHBOARD Consumer 增加“核对调用方配置 / 核对仪表盘”操作。
- Data Service 调用方管理页支持 `consumerId` 深链参数，项目内返回列表精确选中目标并打开既有抽屉；关闭抽屉移除参数。未知或超出 JS safe integer 的 ID 显示显式错误，不会自动打开其它 Consumer。
- 这两个操作均为**来源对象核对**而不是“找到真实负责人”或“已完成沟通”；USER/TEAM/JOB 暂不生成不可信回链。
- 不新增后端 Endpoint、Consumer Owner/Notification 数据表、Approval Flow、持久 ACK、发布生命周期变迁。

## 3. PD-008 仍需冻结的正式答复门槛

- 来源域分别给出一条 **ConsumerRef → Console Actor(或允许代理主体) + Project 权限 + 授权证据** 的读取与变更合同，所有类型均需明确支持/不支持/无法解析的状态。
- Reply Truth Owner 与源 Proposal Owner 独立或一体的选择，何时需要新实体/状态机；持久回复必须冻结来源版本/内容指纹、actor、ConsumerRef、时间、审计与幂等。
- 审批仅用现有 Approval SPI 表示正式审批，不用 step 借代任意 Consumer 的人工作答。
- 通知“已发送/已读”必须有交付 Provider 的原始证据；本轮只做页面导航。
- 如涉及废弃、退休、发布阻断，先通过 PD-007（PROPOSED），再补 APPROVED Feature 与两来源 Domain/Requirements。

## 4. 回归与真实 Golden E2E

自动回归：正确 type/domain/id、负例同名/错 sourceDomain、超安全整数/前导零/URI 注入、Data Service 权限、Dashboard Viewer 路由、Job/USER/TEAM 无假链接。

真实 E2E（尚未执行）：已知 Data Service Consumer 的当前 Project owner、`data-service:access` 无权用户、跨 Project、Consumer 已删除/来源不可用、实际 Dashboard 查询权限、超大 LONG ID 不误指到相邻 Consumer、从影响分析到来源核对再浏览器返回、版本来源证据不被修改。记录 deployed SHA/actor/Project/ConsumerRef、UI + API 及结果；继续由 #336 收集，不能拿 Jest/CI 替代。

**真实环境 Golden E2E 仍为 PENDING。**
