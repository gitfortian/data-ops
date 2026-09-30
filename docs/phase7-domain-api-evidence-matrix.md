# Phase7 Domain API / Evidence Matrix

## Asset

入口：Asset Catalog / Asset Detail

API:

- `GET /api/v1/assets`
- `GET /api/v1/assets/{id}`
- `GET /api/v1/assets/{id}/sections/{sectionType}`

返回：

- AssetItem
- AssetDetail
- SectionResponse

Evidence:

- Asset Catalog 截图
- Asset Detail 聚合截图
- Section API Request/Response

## Metadata

入口：Asset Detail -> TECHNICAL_METADATA

API:

- `GET /api/v1/assets/{id}/sections/TECHNICAL_METADATA`

返回：

- Metadata Section
- 字段/结构信息

Evidence:

- Metadata section response

## Quality

入口：Asset Detail -> QUALITY

API:

- `GET /api/v1/assets/{id}/sections/QUALITY`

返回：

- Quality Section
- 纳管状态
- 最近执行摘要

Evidence:

- Quality execution evidence

## Lineage

入口：Asset Detail -> LINEAGE

API:

- `GET /api/v1/assets/{id}/sections/LINEAGE`

返回：

- Lineage Section
- 上下游关系

Evidence:

- Lineage graph screenshot

## Metric

入口：Metric Domain / Asset Related Metric

API:

- Metric provider API
- Asset section aggregation

返回：

- Metric metadata
- Version information

Evidence:

- Metric detail screenshot

## Security

入口：Asset Detail -> SECURITY

API:

- `GET /api/v1/assets/{id}/sections/SECURITY`

返回：

- Classification
- Security metadata

Evidence:

- Security section response

## Lifecycle

入口：Asset Detail -> LIFECYCLE

API:

- `GET /api/v1/assets/{id}/sections/LIFECYCLE`

返回：

- TTL facts
- Lifecycle state

Evidence:

- Lifecycle policy evidence

## Demo Strategy

选择：方案 A

原因：

- 当前各 Domain 已拥有事实模型
- Asset 明确禁止复制源域事实
- 通过现有 fixture/provider 组合治理 Demo 更符合 D1 边界

后续如发现缺少跨域初始化能力，再评估 phase7-demo 模块。