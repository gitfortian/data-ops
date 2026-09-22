# Product Guard V1

Product Guard 把少量高价值产品规则变成可执行检查。

目标不是让 CI 替代产品判断，而是用低误伤规则阻止几类高成本回归。

## 1. Product Baseline Guard

脚本：

`scripts/product/check-product-baseline.mjs`

检查：

- Product Truth 基线文件必须存在；
- 基线文档不能是空文件且必须有 H1；
- Product Glossary 不允许重复术语；
- 跨产品术语必须声明 Owner。

## 2. Pull Request Product Impact Guard

脚本：

`scripts/product/check-product-pr.mjs`

当 PR 修改业务 / Runtime 产品行为代码时，必须填写：

- User
- Capability
- User Journey
- Problem
- Expected Outcome
- Truth Owner
- Acceptance

纯文档、测试、产品规范与流程修改不会被强制要求填写详细 Product Impact。

## 3. Product Surface Guard

脚本：

`scripts/product/check-product-surface.mjs`

当 PR 新增：

- `yak-ops-business-*` Maven 业务模块；或
- 一级导航域；

必须在 PR 的 `Product Surface Change` 中说明：

- Capability
- User Journey
- Why existing surface cannot carry this

规则不是禁止扩展，而是提高新增产品面的证明门槛。

## 4. CI

`.github/workflows/product-guard.yml` 在 Pull Request 上运行三个检查。

V1 使用 Node.js 自带能力和 git，不增加 npm / Maven 依赖。

## 5. 本地运行

基线检查可随时执行：

```bash
node scripts/product/check-product-baseline.mjs
```

PR Product Impact / Product Surface 两项依赖 GitHub Pull Request event，主要用于 CI。

## 6. V1 明确不做

Product Guard V1 不做：

- AI 自动给产品打分；
- 判断需求“好不好”；
- 禁止创建所有新模块；
- 自动解析全部历史文档；
- 强制小修复都写完整 PRD；
- 自动修改产品规范。

需要产品判断的地方仍由人工 / AI Review 完成，CI 只守明确不变量。
