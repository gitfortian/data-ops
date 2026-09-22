# Product Guard V1.1

Product Guard 只自动检查明确、低误伤的不变量；主观产品判断仍由 Product Review 完成。

## 1. Baseline Guard

`scripts/product/check-product-baseline.mjs`

检查：

- 产品治理核心文件完整；
- Markdown 基线不是空文件；
- Product Glossary 跨域术语不重复且有 Owner；
- Product Decision Status / Implementation 枚举合法；
- Feature Spec Status 合法；
- 新 Product Guard 不依赖旧 `scripts/backbone` / `docs/v1` 权威体系。

## 2. PR Product Impact Guard

`scripts/product/check-product-pr.mjs`

修改产品源代码路径时，PR 必须先声明：

- Change Type: PRODUCT / TECHNICAL / DOCS / OPS
- Product Behavior Changed: Yes / No

### Product Behavior Changed = No

必须解释 `If No, Why`，避免为了过 CI 伪造 PRD。

### Product Behavior Changed = Yes

Change Type 必须是 PRODUCT，并填写：

- User
- Capability
- User Journey
- Problem
- Expected Outcome
- Truth Owner
- Acceptance Scenario
- Acceptance Evidence

Acceptance 的模板标签本身不算内容。

## 3. Product Surface Guard

`scripts/product/check-product-surface.mjs`

如果 PR 新增：

- `yak-ops-business-*` Maven module；或
- 一级导航域；

必须提供 Product Surface Change：

- Product Decision
- Capability
- User Journey
- Why existing surface cannot carry this

Product Decision 必须在 **PR base branch 上已经存在且 Status: ACCEPTED**。

也就是说：

~~~text
Decision PR
 -> merged to main
 -> Implementation PR
~~~

不能在同一个实现 PR 里“现批现用”。

## 4. Self-test

`scripts/product/test-product-guard.mjs`

验证：

- 空 PR 模板不会误通过 Acceptance；
- section / field parser 行为稳定；
- 当前 navigation top-level parser 能区分父域和子域；
- business module parser；
- Decision / Feature 状态 parser。

## 5. CI Scope

`.github/workflows/product-guard.yml`

- Pull Request：运行全部检查；
- push to `main`：至少重新验证 baseline + self-test；
- 不使用外部 npm 依赖。

## 6. Product Guard 明确不做

- AI 自动产品评分；
- 判断一个需求“好不好”；
- 用代码路径猜测所有产品语义；
- 强制 TECHNICAL change 写假 PRD；
- 自动把 Review 提升为 Product Truth；
- 自动决定 Asset / Dataset 等产品 Hub。
