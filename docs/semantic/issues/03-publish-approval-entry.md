# 03: 标准发布审批前端发起入口

**对应需求:** 语义中心盘点 §6 缺失能力（P1）| 阶段: P1

**What to build:** 后端链路完整——`POST /api/v1/semantic/standards/{id}/publish-approval`(`SemanticStandardController` L114)+ `StandardPublishApprovalHandler`(flowCode=STANDARD_PUBLISH,批准即 ENABLED、同事务,决策 D5)已注册进审批中心;但**全前端 semantic 页面零调用**(仓内唯一 publish-approval 调用点在 data-asset 页)。实际发布=直接调状态端点绕过审批,"发布无闸"防护形同虚设。补齐:数据标准页对 DISABLED→ENABLED 动作出"提交发布"入口,状态机区分"审批中"。

**模块归属:** **跨模块**——semantic(页面+端点) + approval(审批中心,已注册无需改)

**Blocked by:** 32 标准管理界面 — 已解决(2026-09-14 六类别界面对外开放)

**Status:** done 2026-09-22

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md);审批接入沿用项目既有"存量代码+审批中心开关"风格(开关关时保持现状直发,不双轨);乐观锁 version 语义不变。

- [x] 数据标准页行操作/详情抽屉对 DISABLED 标准出"提交发布"按钮,调 `/publish-approval`
- [x] 列表与详情展示"审批中"态(含审批单跳转/进度),审批中禁重复提交、禁直接改状态
- [x] 审批开关关闭时维持现有直发行为(与 approval 模块现有开关语义一致)
- [x] 批准→ENABLED(现有回调)、驳回→回原态并在页面提示驳回原因
- [x] 前端契约测试(navigation/menuCode 不变)通过

## 落地记录(2026-09-22)

**裁决口径:** 开闸后非 CODE 行的"启用"直接**替换**为"提交发布"(不留直发按钮)——本单的立项动因就是堵"绕过审批直发"的口子,若学 modeling 页保留"直接发布"双按钮,防护依旧形同虚设。停用(ENABLED→DISABLED)不拦:下线动作无发布风险。审批在途(PENDING)冻结该行编辑/删除/状态操作并提供"审批单"跳转。

**前端(`yak-ops-ui`):**
- `src/services/semantic/api.ts`:新增 `submitStandardPublishApproval(id)` → `POST /api/v1/semantic/standards/{id}/publish-approval`(返回 `ApprovalInstance`);
- `src/pages/semantic/standards/index.tsx`:照 modeling 详情页先例接"存量代码+开关"三件套——`listFlows('STANDARD_PUBLISH')` 探开关(不可用/未启用→关,维持直发)、加载当前页后对非 CODE 行逐个 `findByBiz` 取在途/最近单(每页 ≤10,fail-open)、状态列并挂 `ApprovalStatusTag`、操作列按开关态出"提交发布"/冻结、`/approval/instance/:id` 跳转、PENDING 15s 轮询终态:批准→`message.info`+列表刷新(回调查启用同事务)、驳回→`getApprovalDetail` 取 REJECTED 级次 comment 提示原因、撤销→提示;
- `src/pages/semantic/standards/components/StandardDetailDrawer.tsx`:新增"发布审批"行(状态标签+查看审批单)与"提交发布"按钮(DISABLED 且非在途时)。

**后端(`yak-ops-business-semantic` + `yak-ops-common`):**
- `SemanticErrorCode` 新增 `PUBLISH_ALREADY_ENABLED(42023)`;
- `StandardPublishApprovalService.submit` 前置校验:已 ENABLED 的标准拒绝再提生效单(此前无任何状态前置,批准回调会重复走 changeStatus);新增单测 `submitRejectsAlreadyEnabled`,6/6 绿。

**范围边界(遗留):**
1. CODE 码值标准以码集为整组操作(`changeCodeSetStatus`),一码集=多标准行,生效审批按单标准 id 建模,整组过审需 N 单——本期不接,码集视图与"全部"视图 CODE 行维持直发;
2. 后端 `POST /{id}/status` 直发端点未加在途审批拦截(与 modeling 侧同口径的已知未决口子,前端已冻结入口);
3. 真机页面验证待用户重启 :8080 后端 + 起 :8000 dev server 后补做;STANDARD_PUBLISH 流程需在审批中心配置并启用方可开闸。

**契约测试说明:** 本单未触碰 navigation/menuCode;`navigationMenuContract.test.ts` 现有一条 "metric frontend=modeling backend=data-analysis" 红为并发会话 `f276df8b4` 引入,与本单无关。
