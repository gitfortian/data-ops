# Product Capability Map

本文件描述用户心智中的产品能力，不等同于 Maven module。

## 1. 数据接入与集成

用户目标：把外部数据可靠带进平台。

包含：

- 数据源
- 文件资源
- 离线同步
- 实时同步
- Metadata Harvest（发现外部物理结构）

## 2. 标准、指标与建模

用户目标：定义数据应该代表什么、如何标准化、如何建模和度量。

包含：

- 数据标准
- 业务域 / 业务过程
- 标准字段
- 数仓分层
- 指标
- 模型工作台

Data Development 不归属本域；它消费这里产生的标准、模型和指标定义。

## 3. 开发与运行

用户目标：把设计变成可发布、可编排、可运行、可恢复的数据生产过程。

包含：

- 数据开发
- 发布版本
- 工作流
- 调度
- 实例运维
- 补数 / 重跑

Task Catalog、Job Runtime 等属于实现能力，不作为独立产品域。

## 4. 数据资产与治理

用户目标：知道“有什么、是什么、是否可信、谁负责、来自哪里、被谁使用”。

包含：

- 资产目录 / 资产详情
- Metadata Catalog / Reconciliation（管理已采集元数据事实）
- 血缘
- 数据质量
- 数据安全
- 生命周期
- 使用 / 影响分析

Asset、Metadata、Lineage、Quality 等谁承担统一入口，属于待正式 Product Decision 的产品设计问题，本文件不提前决定。

## 5. 数据消费与服务

用户目标：稳定、安全、可追溯地使用数据。

包含：

- Dataset
- Analysis
- Dashboard
- Digital Screen
- Data Service
- Agent
- Export / downstream consumption

默认消费契约尚需正式 Product Decision；本文件只声明这些能力属于同一消费产品域。

## 6. 专业治理解决方案

### MDM

主数据属于专业解决方案，可以复用同步、质量、审批、服务、资产、血缘等平台能力。

是否作为独立 Solution Pack 长期呈现，需由正式 Product Decision 确认。

## 7. 横切平台能力

- Project Space
- RBAC
- Approval Engine
- Audit
- Alert / Notification
- Task Runtime
- Plugin / SPI
- Storage
- Scheduler

它们通过其它产品能力被用户感知，不自然等于一级产品域。

## 8. 能力准入规则

新增 / 合并 / 拆分一级 Product Capability 前必须：

1. 有明确独立用户目标；
2. 明确 Truth Owner；
3. 证明现有能力无法承载；
4. 有 ACCEPTED Product Decision；
5. 再进入 Feature Spec 和实现。
