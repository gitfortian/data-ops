# Wave 3 · P2 范式收敛工单

> 前置：W1/W2 全部合入。本 wave 是"还债+统一门面"，不新增版本能力。

---

## W3-1 ｜P2｜既有版本 UI 全量替换为 VersionHistoryPanel + diff 铺开

**现状**：modeling `ModelVersionPanel.tsx`、dashboard `version-history-drawer.tsx`、digital-screen `VersionHistoryDrawer.tsx`、dev-task `TaskVersionsPanel.tsx` 四处已实现但互不复用、全部无 diff；semantic/workflow/dataset 等 W1/W2 后已有可操作面板（直接用的 S4 件）。

**改动点**：四处替换为 S4 `VersionHistoryPanel`，各模块自定义渲染经 children/slot 保留（dashboard 缩略预览、modeling 结构 Table、dev-task 代码查看）；diff 一律走 `JsonDiffView`；替换后删除被替代的旧组件文件。dashboard 结构化分行快照保留（Q2 豁免），其 versions/{no} 端点出「快照视图」适配 JSON 供 diff。

**验收**：四处版本入口视觉/交互一致；任选两处 diff 可用；旧组件 grep 零引用后删除。

---

## W3-2 ｜P2｜端点别名收敛（rollback 统一命名，旧名 @Deprecated）

**现状**：同义端点四种命名——`rollback`（screen、modeling）、`activate/{revisionNo}`（dev-task 发布中心）、`restore/{versionNo}`+已弃用 `activate`（dashboard `DashboardController.java:91,99`）、`apply-published-version`（realtime，语义实为"收敛到最新"非回切，W2-4 后与真 rollback 并存）。

**改动点**：C3 规范名 `POST /{id}/versions/{no}/rollback` 在 dev-task/dashboard 补别名路由（内部同实现）；旧名标 @Deprecated 并在 swagger 注明退役版本；**存量不物理删除**。回切型 vs 追加型实现差异保留（Q1 豁免），但响应体统一含 `rolledBackFromVersionNo` + `newVersionNo`（指针型该值可空），前端文案据此区分。

**验收**：新旧名同请求异路径行为一致；tsc/编译绿；Q1 豁免清单落本文档。

---

## W3-3 ｜P2｜版本 PO 归属约定落地（按 Q7）

**现状**：PO 一半在 `yak-ops-common/bean/po/{模块}`（dev-task/modeling/workflow/semantic/quality/metric/mdm），一半在模块内 `dao/model`（digital-screen/dashboard/realtime-sync）。

**改动点**：按 Q7 拍板结果执行——推荐「新对象一律模块内 dao/model，存量不搬家」；在 scaffold-checklist.md 固化；若反向拍板（全部进 common），只迁 W1/W2 新建的 4-5 张版本表 PO，不碰历史 PO。

**验收**：checklist 更新 + 本次新增 PO 全部符合约定（评审点）。

---

## W3-4 ｜P2｜C5 审计 diff 全模块扫尾 + C 域"不做版本化"判定归档

**现状**：`Map.of()` 空审计散点残留（datasource PUT、asset 部分路径、S3 收编后各模块未接 AuditDiffs 的写口）；C 域对象（datasource 连接参数/资源文件/系统环境变量）判定理由只存在于评审文档 §六.6，未回写各模块 docs。

**改动点**：grep 审计调用传 `Map.of()`/null 的写口清零（密钥类字段经 S3 `AuditDiffs` 掩码）；datasource 补「改密强制重测连通」拦截（若尚无）；C 域判定+理由分别回写 `docs/data-metadata`、`docs/mdm`、`docs/semantic` 相关文档对应小节，引用设计基线 §一。

**验收**：全仓 `AuditOperationRequest` 构造点空 before/after 零命中（除创建类操作天然无 before，白名单注释）；三处判定归档可检索。
