# P1 · 已知 Consumer 来源核对往返导航与精确版本保持（2026-10-08）

> 代码基线：data-ops `main 5914190d7403338796dd7724d9c94faa270ca5f1`，PR #404 merged。
> 产品授权：PD-002 `ACCEPTED`、F-004 `APPROVED` 的 canonical Consumption Detail / known Consumer 稳定回链。PD-008 `PROPOSED / NOT_STARTED`、PD-007 `PROPOSED`，**不得**借此生成持久 Consumer ACK、Notification、Approval、Source Lifecycle/Publish Gate。

## 真实用户任务 / 代码证据

Owner 在 `VersionChangeImpactReview.tsx` 选中来源精确版本、检查其中某个 Consumer，然后通过 PR #403 的来源对象链接查看 Data Service 调用方授权配置或 Dashboard 报表。当前代码存在两处真实回程断点：

1. `/data-service/access?consumerId=<id>` 由 `DataServiceAccessPage` 找到当前 Project 真实 managed consumer 并打开侧边抽屉，但只有关闭抽屉和普通目录导航，无法返回原来的 Consumption Product/Version。
2. `/dashboard/:id` 由 `DashboardViewerRedirect` 跳转至 `/dashboard/:id/edit?preview=1`，丢失所有业务上下文；Fullscreen `DashboardEditorPage.leaveDashboard` 固定回到仪表盘列表。
3. `VersionChangeImpactReview` 版本选择仅存本地 React state，重新回到 Consumption 会默认选中当前 active/首个观测版本，**可能悄悄核对错版本**，特别是相同显示版本但不同 SourceRevision.identity。

以上读取的真实文件包括：`src/config/consumer-source-navigation.ts`、`src/pages/data-analysis/consumption/{detail.tsx,VersionChangeImpactReview.tsx,version-impact-review.ts}`、`src/pages/data-service/access/index.tsx`、`src/pages/data-analysis/dashboard/{viewer.tsx,editor.tsx}`、`docs/product/features/F-004-governed-data-consumption.md` 及对应 Jest。

## 当前实施

- 唯一规范返回地址：`/data-analysis/consumption/<DATASET%3Aidentity|DATA_SERVICE%3Aidentity>[?reviewVersion=<exactRevisionIdentity>]`。来源版本 ID 始终为原始十进制**字符串**，不转 JS Number。只接受规范十进制的来源 ID、版本 ID；拒绝外部 URL、`//`、上跳路径、额外 query 参数、fragment、重复参数、转义注入。收到入站 `returnTo` 必须严格校验且重建 canonical path，再允许导航。
- Consumer 来源链接可选附带经验证的 `returnTo`；错误 `returnTo` 一律丢弃，既有无上下文入口不受影响。DATA_SERVICE 仍只对 SAFE INTEGER managed-caller ID 生成路由；Dashboard 原本用 number guard 拒绝大于 MAX_SAFE_INTEGER 的**字符串**身份，本次为 Dashboard 解锁无损字符串深链，Source Domain 继续校验自己的 Project/RBAC 和 Dashboard existence。
- Data Service `AccessPage` 在来源管理目录和详情 Drawer 顶部提供显式“返回消费者影响核对”。`consumerId` 仍只按当前 Project 读取的来源列表精确匹配，不因 return URL 改变权限或来源 truth。
- Dashboard Viewer Redirect 继续保持预览标记 `preview=1`，并且只转发合法的 `returnTo` 到 fullscreen editor。Editor 现有返回函数保持 dirty 离开确认，只在带有效来源 return-context 时返回 Consumption；另有显式返回入口。
- Consumption 版本选择改为 URL 中 `reviewVersion` 的**精确身份**（不以 displayVersion/Source.active 的近似值作匹配）。显式版本在最新证据窗口中不可读时显示“指定来源版本已不在本次可核对证据中”，**不自动降级选择另一个版本**；可通过手动 Select 重定向合法、已知版本。未指定版本的常规入口仍默认使用 active / 首个 observed。
- 来源核对后原证据须重新从拥有 Project 权限的 backend 读取，不能把缓存内容假称最新，不能从窗口缺失推断“无消费者”。

## 关键回归

1. Data Service managed caller（SAFE ID）/Dashboard（精确字符串 BIGINT）能携带 Source ProductKey + SourceVersion.identity 往返，Dashboard Viewer 中间重定向不丢参数。
2. 相同 `displayVersion` 但不同 `identity` 不串；返回时来源证据失效/超出 200 条窗口则明确不再可核对，不偷偷选 active。
3. 不可信 `returnTo` 绝不构成开放重定向（外部 URL、`//`、`javascript:`、路径逃逸、编码注入、多余参数或重复版本参数）。
4. JOB/TEAM/USER/未知 sourceDomain 无可靠源对象路由；Data Service consumer ID 超 JS safe integer 不转换到错误账号，Dashboard ID 作为字符串保留。
5. 各目标页保留已有 Project、Access、Dashboard 权限与数据约束；未触发 Consumer 回复、消息发送、审批、发布修改和 Audit。
6. #336 真实 Golden E2E 仍待部署检查：在正确/跨 Project 与受限角色、删除 Consumer、来源故障、同展示版本不同修订情况下的 URL/Network/页面回链/角色和结果。应记录 deployed SHA、actor、Project、精确 ProductKey/Version/ConsumerRef；CI PASS ≠ E2E PASS。

**范围限定：F-004 回链体验；不扩大 PD-008 协作确认模型。**
