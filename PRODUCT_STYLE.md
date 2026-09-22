# DataOps Product Style

本文件是 DataOps 仓库级产品研发最高规范之一，与 `CODE_STYLE.md` 并列。

它回答的不是“代码怎么写”，而是“为什么做、为谁做、做完后用户得到什么结果”。

## 1. 权威层级

产品与研发文档按以下顺序解释；下层不得擅自改写上层：

1. `docs/product/PRODUCT_VISION.md`
2. `docs/product/PRODUCT_PRINCIPLES.md`
3. `docs/product/CAPABILITY_MAP.md`
4. `docs/product/USER_JOURNEYS.md`
5. `docs/product/PRODUCT_GLOSSARY.md`
6. Feature Product Spec
7. 业务模块 `DOMAIN.md / REQUIREMENTS.md`
8. `ARCHITECTURE.md / DEPENDENCIES.md`
9. Code / Tests

冲突时先修文档 ownership，不允许多个文件同时声称自己是同一产品事实的唯一真相。

## 2. 成功标准：闭环，不是模块完成

禁止用以下表述作为完成标准：

- “XX 模块完成”
- “CRUD 已完成”
- “页面已完成”
- “接口已完成”

必须描述成用户闭环，例如：

> 用户可以定义指标 → 验证 → 被 Dataset / Dashboard 消费 → 查看使用方 → 上游变化时看到影响。

任何 Feature 都必须至少回答：

- User：谁使用？
- Problem：当前具体问题是什么？
- Journey：属于哪条用户旅程？
- Outcome：用户最终得到什么结果？
- Truth Owner：谁拥有事实？
- Producers / Consumers：谁生产、谁消费？
- Reuse：复用哪些已有能力？
- Entry / Next Step：从哪里进入，完成后去哪？
- Acceptance：什么证据证明闭环？

## 3. Product First

AI 或开发者接到需求后，不得直接从表、Controller、页面或类开始设计。

顺序必须是：

```text
Problem
  -> Product Spec
  -> Product / Domain review
  -> Architecture
  -> Dev plan
  -> Code
  -> E2E acceptance
```

小需求的 Product Spec 可以很短，但不能省略“为什么做”。

## 4. 核心产品纪律

### 4.1 不为模块完整而开发

工程模块可以独立，产品能力不等于 Maven module。

新增模块、一级菜单、状态机、术语、目录、审批流之前，必须证明现有能力无法承载。

### 4.2 一份事实只有一个 Owner

禁止因为消费方便复制第二份业务真相。

引用跨域事实时优先使用稳定 ID + Query API / Gateway / SPI。

### 4.3 产品路径优先于后台菜单

用户应该按任务完成工作，而不是学习仓库模块结构。

跨模块路径必须提供明确的前一步 / 下一步 / 回链。

### 4.4 治理必须进入执行链路

仅“能配置”不等于产品能力成立。

例如安全、质量、审批、资产状态只有真正影响查询、发布、消费、运行时，才算闭环。

### 4.5 默认复用，例外自建

每个 Feature 都要列出已复用的平台能力。自建已有能力必须说明原因。

重点检查：权限、项目空间、审批、审计、告警、血缘、资产、版本、任务执行、数据查询、通知。

### 4.6 不把内部机制暴露成用户心智

Job Runtime、Task Catalog、Trigger Ledger、Adapter、DAO 等属于实现层；除非用户确实需要操作，否则不得自然演化成独立菜单或产品术语。

## 5. 新 Feature 必答十问

1. 服务哪个用户？
2. 用户现在为什么做不成？
3. 属于哪条 User Journey？
4. 完成后用户得到什么业务结果？
5. 哪个域拥有 Truth？
6. 上游与下游分别是谁？
7. 复用哪些已有能力？
8. 是否新增概念、状态机、一级入口或第二份真相？
9. 错误、空状态、权限、审计、可观测如何表现？
10. 用哪条 E2E 场景证明闭环？

任一题无法回答时，不进入实现。

## 6. AI 开发要求

AI 在修改业务代码前应依次阅读：

1. 本文件
2. `docs/product/README.md`
3. 与需求相关的 Product Spec
4. 目标模块 `DOMAIN.md / REQUIREMENTS.md / ARCHITECTURE.md / DEPENDENCIES.md`
5. 根目录 `CODE_STYLE.md`

AI 不得因为“当前模块缺少某能力”自动补齐该模块；必须先判断该能力是否属于其他 Truth Owner 或用户旅程。

## 7. 历史文档

旧的盘点、review、gap backlog、dev-plan、issue 设计仍然是重要 Evidence，但不是自动生效的产品真相。

它们与本规范冲突时，应被视为历史材料，除非重新经过 Product Decision 纳入当前产品基线。
