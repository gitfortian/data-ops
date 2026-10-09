# AI / Agent Repository Entry

本文件是 AI / Coding Agent 进入仓库时的统一入口。

修改业务行为前，按顺序读取：

1. `PRODUCT_STYLE.md`
2. `docs/product/README.md`
3. 与本次需求相关的 **ACCEPTED Product Decision**
4. 与本次需求相关且状态为 **APPROVED / IMPLEMENTING** 的 Feature Product Spec（如存在）
5. 目标模块 `DOMAIN.md / REQUIREMENTS.md`
6. 目标模块 `ARCHITECTURE.md / DEPENDENCIES.md`
7. 根目录 `CODE_STYLE.md`
8. 仅在需要证据时再读取 Review / Gap / 历史计划；入口分类见 `docs/README.md` 和 `docs/product/LEGACY_DOC_INDEX.md`

## Mandatory behavior

不要从表、Controller、页面、类或 Maven module 出发设计需求。

先回答：

- User
- Problem
- Capability
- User Journey
- Expected Outcome
- Truth Owner
- Producer / Consumer
- Existing capabilities to reuse
- E2E acceptance evidence

## Authority rule

- PROPOSED / REJECTED / SUPERSEDED Product Decision 不是当前 Product Truth。
- DRAFT / SHIPPED / SUPERSEDED Feature Spec 不是当前实现指令。
- Review、gap、dev-plan、历史 issue、`.zcode/plans/**` 只能作为 Evidence；旧 Flyway 合并/历史表改写提案不构成执行授权。
- 如果历史材料与当前 Product / Domain Contract 冲突，必须显式指出冲突，不得静默选择历史材料。

## Scope rule

不要因为“当前模块不完整”就自动补齐它。

任何新增模块、一级导航、状态机、跨域术语或第二份业务真相，都必须先经过现有产品治理流程。
