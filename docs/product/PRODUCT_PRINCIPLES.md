# Product Principles

## P1. 用户闭环优先于模块完整

开发以“用户是否完成目标”为完成标准，而不是“模块功能是否齐全”。

## P2. 一份事实，一个 Owner

Domain Truth 只能有一个 Owner。其它模块引用，不复制。

## P3. Dataset 是默认消费契约

Dashboard、API、Agent、分析等消费场景优先基于 Dataset。

允许高级场景直接 SQL / DataSource，但必须显式标识为高级或非治理路径。

## P4. Asset 是治理聚合入口

Metadata、Standard、Quality、Security、Lineage、Usage、Lifecycle 等治理事实应汇聚到 Asset 视图。

这些模块可以独立拥有规则，但不应要求用户在多个后台自行拼接同一资产的全貌。

## P5. Semantic / Metric / Model 是业务语义主链

业务标准、业务过程、指标和数仓模型必须形成一致引用关系。

禁止同一个“业务域”“口径”“指标”等概念在多个模块各自定义。

## P6. 治理必须影响真实行为

未上架、无权限、敏感、质量异常等治理状态如果永远不影响发布、查询或消费，就只是展示能力，不算产品闭环。

## P7. 内部机制尽量隐藏

Task Catalog、Runtime、Trigger Ledger、Adapter 等只在确有用户任务时暴露。

用户菜单应围绕工作目标组织，而不是围绕后端模块组织。

## P8. 跨域必须可回链

任何跨模块引用都应允许用户从当前对象回到来源，并能继续查看下游影响。

## P9. 首页只读事实

Home 只聚合，不创建第二套推算逻辑和业务真相。

## P10. 新能力先复用

新增能力前先检查已有：

权限 / Project Space / Audit / Approval / Alert / Asset / Lineage / Dataset / Task / Notification / Version。

重复建设需要在 Product Spec 明确说明原因。

## P11. AI 输出不是业务真相

LLM 可以帮助发现、解释、规划，但不能绕过 Dataset、Metric、Semantic、权限、安全和 Evidence 体系直接成为事实源。

## P12. 默认做减法

新增一级菜单、模块、状态机或术语需要比复用现有能力更高的证明标准。
