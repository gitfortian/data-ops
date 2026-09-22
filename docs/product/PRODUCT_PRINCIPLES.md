# Product Principles

## P1. 用户闭环优先于模块完整

开发以“用户是否完成目标”为完成标准，而不是“模块功能是否齐全”。

## P2. 一份事实，一个 Owner

Domain Truth 只能有一个 Owner；其它模块引用，不复制。

## P3. 消费必须经过稳定、可治理的契约

Dashboard、API、Agent、分析等消费场景应优先复用已有稳定消费契约，而不是各自重新定义 SQL、口径和权限。

具体由哪个对象承担默认消费契约，必须由 ACCEPTED Product Decision 确认。

## P4. 治理围绕稳定对象身份聚合

Metadata、Standard、Quality、Security、Lineage、Usage、Lifecycle 等治理事实应能围绕同一个业务/数据对象被理解和回链。

具体由哪个产品对象承担统一治理入口，必须由 ACCEPTED Product Decision 确认。

## P5. Semantic / Metric / Model 不重复定义业务含义

业务域、业务过程、标准字段、指标、口径、模型之间必须形成清晰引用关系。

## P6. 治理必须影响真实行为

未上架、无权限、敏感、质量异常等治理状态如果永远不影响发布、查询或消费，就只是展示能力。

## P7. 内部机制尽量隐藏

Task Catalog、Runtime、Trigger Ledger、Adapter 等只在确有用户任务时暴露。

## P8. 跨域必须可回链

跨模块引用要能回到来源，并能继续查看下游影响。

## P9. 首页只读事实

Home 只聚合，不创建第二套推算逻辑和业务真相。

## P10. 新能力先复用

新增能力前先检查已有平台能力；重复建设必须在 Product Spec 说明原因。

## P11. AI 输出不是业务真相

LLM 可以帮助发现、解释、规划，但不能绕过平台已有数据契约、权限、安全和 Evidence 体系直接成为事实源。

## P12. 默认做减法

新增一级菜单、模块、状态机或跨域术语需要比复用现有能力更高的证明标准。
