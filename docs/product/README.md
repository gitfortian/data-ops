# DataOps Product Baseline

这里存放 DataOps 的当前产品真相。

## 阅读顺序

1. [PRODUCT_VISION.md](./PRODUCT_VISION.md) — 产品为什么存在
2. [PRODUCT_PRINCIPLES.md](./PRODUCT_PRINCIPLES.md) — 产品怎么取舍
3. [CAPABILITY_MAP.md](./CAPABILITY_MAP.md) — 用户看到的能力地图
4. [USER_JOURNEYS.md](./USER_JOURNEYS.md) — 产品如何形成闭环
5. [PRODUCT_GLOSSARY.md](./PRODUCT_GLOSSARY.md) — 一词一义
6. [FEATURE_SPEC_TEMPLATE.md](./FEATURE_SPEC_TEMPLATE.md) — 新需求怎么写

仓库级研发规则见根目录 [PRODUCT_STYLE.md](../../PRODUCT_STYLE.md)。

## 文档分层

```text
Product Truth
  -> Feature Product Spec
  -> Domain Contract
  -> Architecture
  -> Code
```

`docs/v1/**`、各类 review、gap backlog、dev-plan、历史 issue 文档是重要分析材料，但默认属于 Evidence / Historical Context，不高于本目录中的产品基线。

## 当前阶段

当前阶段的目标不是继续扩张模块数量，而是：

- 从“模块产品”转向“链路产品”；
- 打通数据生产、治理和消费之间的断点；
- 让 Asset / Dataset / Metric / Semantic 成为跨域连接点；
- 隐藏内部机制，减少用户必须理解的系统概念；
- 用 AI 可读取、可检查的规范约束后续开发。
