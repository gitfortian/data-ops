# Product Capability Map

本文件描述用户心智中的产品能力，不等同于 Maven module。

## 1. 数据接入与集成

用户目标：把外部数据可靠带进平台。

包含能力：

- 数据源
- 文件资源
- 离线同步
- 实时同步
- 元数据采集

内部实现可以分模块，但用户不需要理解底层引擎边界。

## 2. 标准、指标与建模

用户目标：定义数据应该长什么样、代表什么、怎么算。

包含能力：

- 数据标准
- 业务域 / 业务过程
- 标准字段
- 数仓分层
- 指标
- 模型工作台
- 数据开发

核心关系：

```text
Semantic
  -> Model
  <-> Metric
  -> Development
```

## 3. 开发与运行

用户目标：把设计持续变成稳定运行的数据生产过程。

包含能力：

- 数据开发任务
- 发布版本
- 工作流
- 调度
- 实例运维
- 补数 / 重跑

Task Catalog、Job Runtime 等属于平台实现能力，不作为独立产品域。

## 4. 数据资产与治理

用户目标：知道“有什么、是否可信、谁负责、来自哪里、被谁使用”。

产品入口：

- 资产目录
- Asset 360
- 血缘
- 元数据采集与对账

治理事实来源：

- Semantic
- Quality
- Security
- Approval
- Audit
- Lifecycle
- Metadata
- Usage

Asset 聚合事实，但不复制源域业务内容。

## 5. 数据消费与服务

用户目标：稳定、安全地使用数据。

默认主链：

```text
Production
  -> Dataset
      -> Analysis / Dashboard
      -> Data Service
      -> Agent
      -> Export / downstream
```

Dataset 是推荐消费契约。

## 6. 专业治理解决方案

### MDM

主数据属于完整专业解决方案，可以复用同步、质量、审批、数据服务、资产、血缘等平台能力。

MDM 不应自行复制平台已有能力，也不决定 DataOps Core 的主产品边界。

## 7. 横切平台能力

这些能力重要，但不等于一级用户产品域：

- Project Space
- RBAC
- Approval Engine
- Audit
- Alert / Notification
- Task Runtime
- Plugin / SPI
- Storage
- Scheduler

它们通过其它产品能力被用户感知。

## 8. 能力准入规则

新增 Capability 前必须明确：

- 是否能归入现有 6 个产品域；
- 是否真的产生新的用户目标；
- Truth Owner 是否已有；
- 是否只是技术实现的新模块；
- 是否必须新增一级导航。

无法证明新的独立用户目标时，不新增产品域。
