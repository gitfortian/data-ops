# DataOps Product Style

本文件与 `CODE_STYLE.md` 并列，定义仓库级产品研发规则。

它回答的不是“代码怎么写”，而是“为什么做、为谁做、做完后用户得到什么结果”。

## 1. Authority

当前产品事实由以下材料共同组成：

1. Product Vision / Principles / Capability Map / User Journeys / Product Glossary
2. `docs/product/decisions/**` 中 **Status: ACCEPTED** 的 Product Decision
3. 当前变更范围内 **APPROVED / IMPLEMENTING** 的 Feature Product Spec
4. 业务模块 `DOMAIN.md / REQUIREMENTS.md`
5. `ARCHITECTURE.md / DEPENDENCIES.md`
6. Code / Tests 作为“当前实现事实”

PROPOSED Decision、Review、Gap、Dev Plan、历史 Issue 不得直接指导实现。

详细规则见 `docs/product/DOCUMENT_GOVERNANCE.md`。

## 2. 成功标准：闭环，不是模块完成

禁止把以下表述单独当作完成标准：

- “XX 模块完成”
- “CRUD 已完成”
- “页面已完成”
- “接口已完成”

Feature 必须回答：

- User
- Problem
- Objective / Expected Outcome
- Journey
- Success Metrics / Success Signal
- Assumptions / Open Questions
- Truth Owner
- Producer / Consumer
- Reuse
- Entry / Next Step
- Acceptance Evidence

Acceptance 与 Success Metrics 必须分开：前者证明交付符合约定，后者证明产品结果真的改善。

## 3. Product First

~~~text
Problem
 -> Product Shape
 -> Decision（需要时）
 -> Feature Spec（需要时）
 -> Domain / Architecture Review
 -> Dev Plan
 -> Code
 -> E2E Acceptance
~~~

小变更可以很轻，但不能省略“为什么做”。

## 4. 核心产品纪律

### 4.1 不为模块完整而开发

工程模块不等于产品能力。

新增模块、一级菜单、状态机、术语、目录或审批流之前，先证明现有能力无法承载。

### 4.2 一份事实只有一个 Owner

禁止因为消费方便复制第二份业务真相。

跨域优先通过稳定 ID + Query API / Gateway / SPI 引用。

### 4.3 产品路径优先于后台菜单

用户按任务完成工作，不应该学习 Maven module 结构。

跨模块路径必须有前一步、下一步和回链。

### 4.4 治理必须进入真实行为

“能配置”不等于产品闭环。

安全、质量、审批、资产状态等只有真正影响发布、查询、消费或运行，才形成用户价值。

### 4.5 默认复用，例外自建

优先检查：权限、Project Space、Approval、Audit、Alert、Lineage、Asset、Dataset、Version、Task、Notification。

### 4.6 不把内部机制暴露成用户心智

Job Runtime、Task Catalog、Trigger Ledger、Adapter、DAO 等默认属于实现层。

## 5. 新 Feature 必答十问

1. 服务哪个用户？
2. 用户当前为什么做不成？
3. 属于哪条 Journey？
4. 最终用户结果是什么？
5. 哪个域拥有 Truth？
6. 上游 / 下游是谁？
7. 复用哪些已有能力？
8. 是否新增概念、状态机、一级入口或第二份真相？
9. 空状态、失败、权限、审计、可观测如何表现？
10. 哪条 E2E Scenario + Evidence 证明闭环？

## 6. AI 开发要求

AI 修改业务代码前读取：

1. 本文件
2. `docs/product/README.md`
3. 相关 ACCEPTED Decisions
4. 当前 APPROVED / IMPLEMENTING Feature Spec
5. 目标模块 Domain / Requirements / Architecture / Dependencies
6. `CODE_STYLE.md`

不得默认递归读取全部 `docs/**`。

## 7. SHIPPED 定义

PR 显示 Merged 不等于变更已进入产品基线。

只有 default branch（当前为 `main`）包含该变更，并且要求的 CI / Acceptance 通过，才可以标记为 SHIPPED。
