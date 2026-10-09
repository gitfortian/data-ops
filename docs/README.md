# Documentation navigation · 资料权威与证据入口

本索引用于**先确定 Owner 再检索具体材料**。仓库历史文档和评审资料很多；不要全量加载 `docs/**`，不要以日期或目录名判断是否为当前需求。

## 当前 Contract 的入口

| 要解决的问题 | 首选来源 | 权威边界 |
|---|---|---|
| 产品目标、Capability 与长期语义 | [Product baseline](product/README.md)、[Document Governance](product/DOCUMENT_GOVERNANCE.md) | 仅生效的 Product Truth / ACCEPTED Decision；不能把 SHIPPED Feature 当作新任务 |
| 当前获准的变更与验收 | [Feature Specs](product/features/README.md)、[Product Change Process](product/PRODUCT_CHANGE_PROCESS.md) | 只有 APPROVED / IMPLEMENTING Feature Spec 是当前变更合同 |
| Domain 不变量与架构依赖 | 各业务模块的 `DOMAIN.md`、`REQUIREMENTS.md`、`ARCHITECTURE.md`、`DEPENDENCIES.md`；根 [CODE_STYLE](../CODE_STYLE.md) | 精确到目标模块读取；避免跨域重复定义 |
| 构建、部署、迁移与恢复 | [Release](release/RELEASING.md)、[Engineering CI 验收](engineering/ci-impact-validation.md) | 操作合同需与当前 workflow/代码及 SQL 历史一致 |
| AI / Coding Agent 读档次序 | [AGENTS.md](../AGENTS.md)、[Legacy Classification](product/LEGACY_DOC_INDEX.md) | 历史计划只做证据，禁止自动提升为合同 |

## 按用途定位证据，不从旧计划自动起开发

| 路径 / 类型 | 当前用途 |
|---|---|
| `product/acceptance/**`、`test/**` | 产品验收和测试证据；真实环境证明须满足对应验收门槛 |
| `reviews/**`、`frontend-review/**`、`architecture-review/**`、`v1/**` | 评审、缺口、历史观察，不能独立声明产品真相 |
| `20261001/**`、`20261003/**`、`20261004/**` | 按日期保存的交付/分析证据，日期不是状态批准 |
| `phase7-*.md`、`phase8*.md`、`phase9*.md` | 阶段性记录，按当前 Contract 和 PR 状态交叉核对 |
| `ai/**`、`agent/**`、`semantic/**`、`metric/**`、`model/**` 等 | 按业务域选择性查看相关研究、回归、设计或操作材料 |
| `prototypes/**`、`INTERACTION_PRINCIPLES.md`、`MENU_REDESIGN.md` | 交互设计参考或历史输入，非自动生效的导航要求 |
| `.zcode/plans/*.md` | **仓库外层 IDE/Agent 历史计划**，不能作为迁移命令、发布步骤或 SQL 删除授权 |

## 高风险旧计划：明确不执行

1. `../.zcode/plans/plan-sess_36a3068e-4352-4d63-8388-d516f3f18f85.md`：提出将 173 个 Flyway 文件压缩成 30 个、移除 baseline/历史迁移和放弃老库升级。**这是历史提案，不是 ACCEPTED Contract；不得据此合并/删除 Flyway SQL、重写历史版本或切换生产库。** 按现行迁移校验和 Release 合同验证任何独立变更。
2. `../.zcode/plans/plan-sess_a18ccd05-9ad6-47e1-9223-c72e865f530c.md`：本体/语义双层架构设计任务草案；原要求目标 `docs/two-layer-modeling-design.md` 在当前树不存在，不等于批准了 Maven 模块重构。
3. `../.zcode/plans/plan-sess_b1fff9ab-dc67-4cd5-822f-1e6dca9c139d.md`：Agent 可观测性改造文档任务草案；是否落地以当前 Agent 模块 Contract 和后续审批为准。

这三份历史文件继续原样保存作 provenance；**不移动、不覆盖、不把文内「执行顺序」解释为当前行动**。

## 按阶段与证据族浏览（Historical / Evidence）

- [历史证据总索引](HISTORICAL_EVIDENCE_INDEX.md)：把按日期的实测报告、前端/架构 Review、阶段报告、旧 V1 资料与测试记录链接到原位置；**不移动或删除任何证据，也不因此确认缺陷已核销**。
- [2026-10-01 报告](20261001/README.md) · [2026-10-03 检查](20261003/README.md) · [2026-10-04 产品审核提案](20261004/README.md)。
- [前端 Review](frontend-review/README.md) · [架构 Review](architecture-review/README.md) · [核销与历史问题 Review](reviews/README.md)。
- [原 V1 阶段资料](v1/README.md) · [测试报告](test/README.md)。

## 工程整洁性收口与后续验收

- [#345 工程整洁性治理阶段证据与剩余工作](engineering/code-cleanliness-p1-closeout.md)：各批 PR、已退役入口、仍需代码级证明的 P2 项。
- [P2 后端整洁性综合审计](engineering/p2-backend-cleanliness-audit.md)：已合并 #477 的机械等价简化与保守可达性审计；没有静态引用不等于死代码。
- [TypeScript type-baseline](../data-ops-ui/scripts/type-baseline.json)：当前机器可验收的 TypeScript 已知诊断基线，不以先前生成的 `tsc-output.txt` 文本替代。
- 历史文档边界细分见 [Legacy Documentation Classification](product/LEGACY_DOC_INDEX.md)。

本索引不修改任一 Product Decision / Feature 状态，不重命名 Flyway 文件，不声明 P2 全仓可达性审计已完成。
