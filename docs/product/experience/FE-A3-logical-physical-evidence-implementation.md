# FE-A3 第一条真实纵向切片：业务逻辑版本 → 物理模型只读审阅

日期：2026-10-14。产品基线：#513 全域 75 条故事、#550 v0.8.2 逻辑先行/ODS 例外、#559 FE-A，架构决策边界 #556 DR-06、#557 DR-07。

## 用户故事与已存在的 Owner

业务设计人员从订单业务过程进入逻辑草稿（逻辑实体/属性/关系/标准引用由 Modeling + Semantic Owner 管理），冻结一个**真实的逻辑草稿版本**，选择该业务过程的 DWD/DIM 物理目标；现有 `logical.tsx` 已能对比 `previewLogicalPhysical` 和会话级 `reviewLogicalPhysical`。但过去物理模型详情的默认内容完全围绕技术表结构，未提供逻辑设计证据和返回同一草稿的安全通路。用户难以确认“这个物理表依据哪个逻辑冻结版本在审阅”，容易误读“只读候选比对”为正式映射。

本轮只使用现成接口：

- `listLogicalDrafts`：当前 Project Modeling Owner 的有权逻辑目录。
- `getLogicalDraft(id)`：明确验证 ID、业务过程归属，不能凭同名字段猜身份。
- `listLogicalVersions(id)`：该草稿实际冻结版本；缺失、旧版本、伪造 URL 不自动降级到最新。
- `previewLogicalPhysical(logicalId, versionNo, physicalId)`：已有只读预检，前端再次核对回执三重身份及双 SHA256 指纹；展示阻断、候选字段对照，**不修改物理模型、逻辑模型或正式映射**。

## 产品交互

1. 已有逻辑工作台中的合法只读预检结果新增“打开此物理模型 · 核对逻辑依据”。导航仅携带 `physicalId/processId/logicalId/logicalVersion`，由目标页独立重新查询对应 Project Owner。
2. 物理模型详情新增“业务逻辑依据”专业 Tab，只对 DWD/DIM 开放版本预检；ODS 明确走精确源表直建例外，不强制逻辑先行。不存在过程关联的目标不自动选第一份逻辑草稿。
3. 用户必须明确选择同业务过程的逻辑草稿和实际冻结版本；带精确 hint 进入时先以 Modeling Owner 列表、单条草稿和版本列表三层验证；任何不一致均阻止预检或显示拒绝，不回退同名模型。
4. 预检展示冻结逻辑快照指纹、当前物理结构指纹、阻断与候选字段对照；`readyForReview` 仅代表“可人工继续审阅”，**不是 LogicalPhysicalMapping 正式持久化、审批、发布或 DataDev TaskRevision 创建**。
5. “返回此逻辑草稿”附带 `processId/draftId/review=physical`；逻辑工作台收到此入口强制选择精确草稿，不静默落到另一份。
6. 从物理表结构切换到任何 Tab（包括新逻辑审阅）及返回列表/来源/逻辑页时使用原有 `dirty/saving/loading` 真实 Editor Owner 状态：未保存修改必须明确放弃，保存/加载中禁止误跳。
7. Project/权限/目标变化时重新读取，旧结果通过作用域键隔离；没有 Mapping Write 的“保存确认”按钮。所有原专业建模 Tab 保持。

## 范围与真实验收

- 本 PR 不增加后端、Flyway、RBAC、审批、模型生成/自动批量继承、非 ODS 硬发布门禁。DR-06 对于 LogicalVersion↔PhysicalModel 正式持久绑定仍**待 Product+Owner 决策**；DR-07 对于物理模型版本→DataDev 任务来源仍未获准。
- 代码回归：`logicalPhysicalJourney.test.ts`、`LogicalPhysicalEvidencePanel.test.tsx`、`verifiedSourceReturn.test.tsx`（未保存物理编辑守卫）。
- **本 PR 只实现可复用后端支持的 read-only 纵向审阅；不等于最终 v0.8.2 全部物理分层 UX、批量字段推导或全产品重构。**
- 尚须实际环境以 `trade_order` → 逻辑 Order/OrderDetail 独立粒度 → 冻结版本 → DWD 物理目标 → 精确预检回链做双 Project/撤权/版本漂移/键盘/320/390/768/1440px 浏览器 E2E。CI 以最终 HEAD 对应的 GitHub Checks 为准，不把隔离单测当真实业务验收。
