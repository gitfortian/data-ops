# Wave 0 · 共享支撑层工单

> 契约依据：设计基线 §三 C1-C5、§四。本 wave 合入前，W1 各工单不开工。
> 落地方式：S1/S2/S3 合为**一个后端 PR**（纯新增 + 逐字复制收敛，不碰任何业务语义）；S4 独立前端 PR。

---

## S1 ｜P0｜共享状态枚举 PublishState 落地

**现状问题**：状态词汇五套并存——DRAFT/PUBLISHED（大屏 `DigitalScreenStatus.java`、modeling `ModelStatus.java:9-13` 另带 DISABLED）、字符串 `"DRAFT/ONLINE/OFFLINE"`（workflow `WorkflowDefinitionManager.java:204,343,368`）、ONLINE/OFFLINE（dataset `DatasetStatus.java:3-6`）、ENABLED/DISABLED（semantic/metric/quality 与发布态混用）。`common/enums/workflow/DefinitionState.java:4-8` 取值恰好是 DRAFT/PUBLISHED/OFFLINE，但全仓零引用（唯一命中是测试方法名），是现成的死枚举。

**改动点**：
- 新建 `data-ops-common` → `io.yak.ops.common.enums.PublishState { DRAFT, PUBLISHED, OFFLINE }`（Q6 建议：不迁 DefinitionState 而是新建，避免 workflow 包语义绑架；DefinitionState 直接删除）。
- PO 的 `status` 列仍为 VARCHAR，模块内以 `PublishState.name()` 读写；提供 `PublishState.of(String)` 宽松解析（存量脏值回退 DRAFT 并 warn）。
- 本工单**只落枚举与工具**，各模块替换字符串字面量在 W1 各工单内做（workflow=W1-4、sync-offline=W1-2、realtime=W2-4）。
- 明确：ENABLED/DISABLED 类开关（metric/semantic/quality）**保留**，语义=可用性开关，与发布态正交（契约 C1）。

**验收**：枚举类 + 单测（round-trip、宽松解析）；全仓 grep `"DRAFT"` 等字面量在 workflow 外的直接引用数不增加；DefinitionState 删除后编译绿。

---

## S2 ｜P0｜版本工具收编（Digest 计算 + nextVersionNo 模板）

**现状问题**：规范化 JSON→SHA-256 至少 3 份独立实现（dev-task `TaskDefinitionDigestCalculator.java:16-21`、quality `QualityTaskPublisher.java:92`、modeling `ModelVersionService.java:63,105-106`），规范化口径未对齐（key 排序/空值处理各自约定）→ 未来 diff/幂等跨模块比对会翻车。版本号分配 4 种手段：SQL `MAX+1`（screen/dev-task/realtime/dataset）、`selectCount+1`（modeling `ModelVersionRepositoryAdapter.java:85-95`，**有删除计数风险**）、内存聚合 counter（workflow）、主表 version 直落（metric/semantic）。

**改动点**：
- `data-ops-common` 新增 `io.yak.ops.common.version` 包（≤2 个类）：
  - `VersionDigests.canonicalJson(Object) / sha256(String)`：统一 Jackson `ORDER_MAP_ENTRIES_BY_KEYS` + 非空序化，收编三处旧实现的**最优口径**（以 dev-task 为准，其有 checksum 幂等索引验证）。
  - `nextVersionNo` SQL 模板约定：mapper 注解统一 `SELECT COALESCE(MAX(version_no),0)+1 FROM <t> WHERE <biz>_id=#{id}`（供 W 系列复制，不做强基类）。
- 旧三处 Digest 实现本 wave 内替换为公共件（调用点行为不变，测试兜底）。

**验收**：三处旧实现的既有测试继续绿 + 公共件单测（字段序不同 JSON → 同 digest；null vs 缺失键策略有定论并写 javadoc）；modeling 的 selectCount+1 留给 W1-3 一并改（避免两个 PR 碰同一文件）。

---

## S3 ｜P0｜AuditTransactions 复制收敛 + 审计 before/after diff 槽位

**现状问题**：`AuditTransactions.java` 被逐字复制 **8 份**（approval/asset/lifecycle/mdm/metric/modeling/security/semantic 各自 `support/audit/`），实现同一"事务提交后落审计"规则；审计写入口 `AuditOperationRequest` 现约定传 before/after，但全项目大量传 `Map.of()`（`TtlPolicyService.java:200`、`MdmChangeEffectService.java:71-72`、`MdmEntityService.java:113,120`）。`AuditCarrier.java:4-17` 无 diff 字段（该由调用方塞 payload，不动 carrier）。

**改动点**：
- 公共实现落 `data-ops-common`（依赖 audit SPI 的最小接口，注意 common 不能反向依赖 business-audit——如存在循环，落 `data-ops-core`，实现时以依赖图为准）。
- 8 份复制删除，import 替换，行为不变。
- 提供 `AuditDiffs.of(before, after)`：对两个 Map/PO 做浅层字段 diff 并按密钥字段脱敏（password/secret/token 类键名掩码），供 W 系列直接使用。

**验收**：审计单测迁移绿；`AuditDiffs` 单测（变更字段集、脱敏命中）；全仓 `AuditTransactions.java` 仅存 1 份。

---

## S4 ｜P0｜前端公共组件 VersionHistoryPanel + JsonDiffView

**现状问题**：5 处版本 UI 各自手写（`ModelVersionPanel.tsx`、dashboard `version-history-drawer.tsx`、digital-screen `VersionHistoryDrawer.tsx`、workflow `WorkflowToolbar.tsx:213-280` 只读 Popover、semantic `StandardVersionsDrawer.tsx` 裸 `JSON.stringify`）；**全库零 diff 组件、无第三方 diff 依赖**；`components/ui` 仅 5 个 Yak* 件。唯一 diff 实践：mdm `ChangeHistoryTab.tsx:67-83` 页面内自实现属性级 v(n-1)/v(n) 对比。请求层主流 `services/<模块>/api.ts` + HttpUtils（workflow 直用 umi request、digital-screen 走 repository 为两处例外）。

**改动点**：
- `src/components/version/VersionHistoryPanel.tsx`：props 驱动（`listVersions/getVersion/onRollback` 异步函数注入），能力=版本倒序列表 + 单版查看（children 渲染槽）+ **diff 相邻版本/任选两版** + 回滚 Popconfirm（文案模板：「将以 v{n} 内容追加新版本 v{n+1} 并置为已发布，当前草稿未发布内容将被丢弃」）+ 空态 YakEmpty。视觉基准取 dashboard 抽屉（左列表右详情）与 modeling 卡片之长。
- `src/components/version/JsonDiffView.tsx`：解析两个 payload_json → 字段级增删改三色表（不引第三方库，行级 diff 用简单 LCS 即可），mdm 的本地 buildDiff 逻辑上浮为首个消费样例。
- 约定接口形状与契约 C3 六端点对齐（`/{id}/versions`、`/{id}/versions/{no}`、`/{id}/versions/{no}/rollback`）。
- **本工单不强制替换存量**（替换在 W3-1），W1/W2 新页面直接消费。

**验收**：story/临时页挂载两组件走通 mock 数据；tsc 绿；回滚确认文案含影响提示（对齐交互底线「明示后果」）。

---

## S5 ｜P1｜新对象版本化脚手架 checklist

**现状问题**：设计基线 §四 决定不做 codegen/基类，但逐模块口口相传契约必漂移。

**改动点**：把「给一个新业务对象加多版本」写成一页 checklist 落 `docs/multi_version/scaffold-checklist.md`：DDL 模板（C2 原文）、主表五字段（status/published_version_id/latest_version_no/draft_revision + created_by）、六端点签名（C3）、Service 三件套命名（`*Publisher`/`*VersionWriter`/`nextVersionNo` mapper 模板）、草稿分离要求（C4）、审计接入（S3 件）、前端接 S4 组件、验收底线（README 统一条款）。参照实现指到文件：digital-screen（后端）+ modeling V16 迁移（DDL）+ S4 面板（前端）。

**验收**：W1 内任一工单实施中发现 checklist 缺失项，回补本文件视为该工单验收的一部分。
