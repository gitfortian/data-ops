# 页面覆盖与证据索引

日期：2026-10-02；环境：http://localhost:8000；工作空间：默认空间。

本索引记录浏览器观察，不代表全部业务状态验收通过。navigation.ts 共登记 104 个路由定义；79 个路由定义有对应观察证据，另含目录实体视图、表单抽屉与响应式截图。当前 feature gate 下 62 个可见导航入口均已走查。

## 注册路由

| 页面 | 路由 | 覆盖 | 证据 |
|---|---|---|---|
| 首页 | `/home` | 已观察 | [01-home-desktop](screenshots/01-home-desktop.png)、[86-home-mobile](screenshots/86-home-mobile.png) |
| 快速创建 | `/create` | 已观察 | [65-create](screenshots/65-create.png) |
| 智能助手 | `/ai-agent` | 已观察 | [66-ai-agent](screenshots/66-ai-agent.png) |
| 数据源 | `/data-source` | 已观察 | [02-data-source](screenshots/02-data-source.png)、[80-datasource-form](screenshots/80-datasource-form.png)、[81-datasource-fields](screenshots/81-datasource-fields.png)、[88-drawer-mobile](screenshots/88-drawer-mobile.png)、[89-datasource-list](screenshots/89-datasource-list.png)、[87-datasource-mobile](screenshots/87-datasource-mobile.png) |
| SQL 执行记录 | `/sql-execution-audit` | 已观察 | [06-sql-audit](screenshots/06-sql-audit.png) |
| 仪表盘 | `/dashboard` | 已观察 | [56-dashboards](screenshots/56-dashboards.png) |
| 新建仪表盘 | `/dashboard/new` | 已观察 | [70-dashboard-editor](screenshots/70-dashboard-editor.png) |
| 仪表盘编辑 | `/dashboard/:id/edit` | 未形成独立浏览器证据，逐批补查 |  |
| 仪表盘查看 | `/dashboard/:id` | 未形成独立浏览器证据，逐批补查 |  |
| 数据产品目录 | `/data-analysis/consumption` | 已观察 | [49-consumption](screenshots/49-consumption.png) |
| 数据产品详情 | `/data-analysis/consumption/:productKey` | 已观察 | [90-consumption-detail](screenshots/90-consumption-detail.png) |
| 数据集管理 | `/dataset` | 已观察 | [50-datasets](screenshots/50-datasets.png) |
| 数据集详情 | `/dataset/:id` | 未形成独立浏览器证据，逐批补查 |  |
| 数据目录 | `/data-analysis/data-catalog` | 未形成独立浏览器证据，逐批补查 |  |
| 数据血缘 | `/data-analysis/lineage` | 已观察 | [30-lineage](screenshots/30-lineage.png) |
| 数字大屏 | `/digital-screen` | 已观察 | [57-digital-screens](screenshots/57-digital-screens.png) |
| 新建数字化大屏 | `/digital-screen/new` | 已观察 | [69-screen-create](screenshots/69-screen-create.png) |
| 数字化大屏编辑 | `/digital-screen/:id/edit` | 未形成独立浏览器证据，逐批补查 |  |
| 数字化大屏预览 | `/digital-screen/:id` | 未形成独立浏览器证据，逐批补查 |  |
| 图表分析 | `/data-analysis/chart-analysis` | 未形成独立浏览器证据，逐批补查 |  |
| API 服务目录 | `/data-service` | 已观察 | [51-api-services](screenshots/51-api-services.png) |
| API 详情 | `/data-service/api/:id` | 已观察 | [71-api-detail](screenshots/71-api-detail.png) |
| 调用方与密钥 | `/data-service/access` | 已观察 | [52-api-access](screenshots/52-api-access.png) |
| API 调试 | `/data-service/debug` | 已观察 | [53-api-debug](screenshots/53-api-debug.png) |
| 运行概览 | `/data-service/overview` | 已观察 | [54-api-overview](screenshots/54-api-overview.png) |
| 调用记录 | `/data-service/logs` | 已观察 | [55-api-logs](screenshots/55-api-logs.png) |
| 设置 | `/settings` | 已观察 | [64-settings](screenshots/64-settings.png) |
| 离线同步 | `/sync/batch-link-up` | 已观察 | [03-batch-sync](screenshots/03-batch-sync.png) |
| 实时同步配置 | `/sync/realtime/:id/detail` | 未形成独立浏览器证据，逐批补查 |  |
| 实时同步 | `/sync/realtime` | 已观察 | [04-realtime-sync](screenshots/04-realtime-sync.png) |
| 离线同步详情 | `/sync/batch-link-up/:id/detail` | 未形成独立浏览器证据，逐批补查 |  |
| 单表同步配置 | `/sync/batch-link-up/:id/config/single` | 未形成独立浏览器证据，逐批补查 |  |
| 多表同步配置 | `/sync/batch-link-up/:id/config/multi` | 未形成独立浏览器证据，逐批补查 |  |
| 脚本同步配置 | `/sync/batch-link-up/:id/config/script` | 未形成独立浏览器证据，逐批补查 |  |
| 开发工作台 | `/data-development` | 已观察 | [17-development](screenshots/17-development.png)、[79-development-editor](screenshots/79-development-editor.png) |
| 发布中心 | `/data-development/releases` | 已观察 | [18-releases](screenshots/18-releases.png) |
| 开发运行记录 | `/data-development/executions` | 已观察 | [19-development-runs](screenshots/19-development-runs.png) |
| 新建开发任务 | `/data-development/task/new` | 未形成独立浏览器证据，逐批补查 |  |
| 开发任务配置 | `/data-development/task/:id` | 未形成独立浏览器证据，逐批补查 |  |
| 工作流定义 | `/workflow/definitions` | 已观察 | [20-workflows](screenshots/20-workflows.png) |
| 工作流配置 | `/workflow/definition/:id` | 已观察 | [77-workflow-editor](screenshots/77-workflow-editor.png)、[78-workflow-node](screenshots/78-workflow-node.png) |
| 调度配置 | `/workflow/definition/:id/schedule` | 未形成独立浏览器证据，逐批补查 |  |
| 工作流实例 | `/workflow/instances` | 已观察 | [21-workflow-runs](screenshots/21-workflow-runs.png) |
| 工作流实例详情 | `/workflow/instances/:executionId` | 未形成独立浏览器证据，逐批补查 |  |
| 模型工作台 | `/modeling` | 已观察 | [12-modeling](screenshots/12-modeling.png) |
| 数据标准 | `/semantic/standards` | 已观察 | [09-data-standards](screenshots/09-data-standards.png) |
| 业务域 | `/semantic/domains` | 已观察 | [07-business-domains](screenshots/07-business-domains.png) |
| 业务过程 | `/semantic/processes` | 已观察 | [08-business-processes](screenshots/08-business-processes.png) |
| 业务过程编辑 | `/semantic/processes/:id/edit` | 未形成独立浏览器证据，逐批补查 |  |
| 标准字段 | `/semantic/fields` | 已观察 | [10-standard-fields](screenshots/10-standard-fields.png) |
| 数仓分层 | `/semantic/layers` | 已观察 | [11-warehouse-layers](screenshots/11-warehouse-layers.png) |
| 主数据总览 | `/mdm/overview` | 已观察 | [44-mdm-overview](screenshots/44-mdm-overview.png) |
| 主数据建模 | `/mdm/modeling` | 已观察 | [45-mdm-modeling](screenshots/45-mdm-modeling.png) |
| 主数据识别 | `/mdm/identification` | 已观察 | [46-mdm-identification](screenshots/46-mdm-identification.png) |
| 主数据清洗 | `/mdm/cleansing` | 已观察 | [47-mdm-cleansing](screenshots/47-mdm-cleansing.png) |
| 主数据变更 | `/mdm/approval` | 已观察 | [48-mdm-approval](screenshots/48-mdm-approval.png) |
| 主数据实体详情 | `/mdm/modeling/:id` | 已观察 | [72-mdm-detail](screenshots/72-mdm-detail.png) |
| 指标管理 | `/metric/manage` | 已观察 | [13-metrics](screenshots/13-metrics.png) |
| 指标详情 | `/metric/manage/:id` | 已观察 | [92-metric-detail](screenshots/92-metric-detail.png) |
| 指标血缘 | `/metric/lineage` | 已观察 | [14-metric-lineage](screenshots/14-metric-lineage.png) |
| 影响分析 | `/metric/impact` | 已观察 | [15-metric-impact](screenshots/15-metric-impact.png) |
| 指标标签与统计 | `/metric/service` | 已观察 | [16-metric-statistics](screenshots/16-metric-statistics.png) |
| 生命周期策略 | `/data-lifecycle/policy` | 已观察 | [41-lifecycle-policy](screenshots/41-lifecycle-policy.png) |
| TTL 运行监控 | `/data-lifecycle/monitor` | 已观察 | [42-lifecycle-monitor](screenshots/42-lifecycle-monitor.png) |
| 存储统计 | `/data-lifecycle/storage` | 已观察 | [43-lifecycle-storage](screenshots/43-lifecycle-storage.png) |
| 数据安全总览 | `/data-security/overview` | 已观察 | [35-security-overview](screenshots/35-security-overview.png) |
| 分级分类 | `/data-security/classification` | 已观察 | [36-classification](screenshots/36-classification.png) |
| 数据访问策略 | `/data-security/access` | 已观察 | [37-security-access](screenshots/37-security-access.png) |
| 数据脱敏 | `/data-security/masking` | 已观察 | [38-masking](screenshots/38-masking.png) |
| 访问审计 | `/data-security/audit` | 已观察 | [39-security-audit](screenshots/39-security-audit.png) |
| 合规管理 | `/data-security/compliance` | 已观察 | [40-compliance](screenshots/40-compliance.png) |
| 我的待办 | `/approval/todo` | 已观察 | [22-approvals](screenshots/22-approvals.png) |
| 审批流程配置 | `/approval/flows` | 已观察 | [23-approval-flows](screenshots/23-approval-flows.png) |
| 审批单详情 | `/approval/instance/:id` | 已观察 | [91-approval-detail](screenshots/91-approval-detail.png) |
| 元数据概览 | `/data-metadata/overview` | 已观察 | [28-metadata-overview](screenshots/28-metadata-overview.png) |
| 采集与对账 | `/data-metadata/collect` | 已观察 | [29-metadata-collection](screenshots/29-metadata-collection.png) |
| 资产概览 | `/data-asset/overview` | 已观察 | [24-asset-overview](screenshots/24-asset-overview.png) |
| 资产目录 | `/data-asset/catalog` | 已观察 | [25-asset-catalog](screenshots/25-asset-catalog.png)、[75-metadata-entities](screenshots/75-metadata-entities.png)、[76-metadata-detail](screenshots/76-metadata-detail.png)、[82-asset-1280](screenshots/82-asset-1280.png)、[83-asset-1024](screenshots/83-asset-1024.png)、[84-asset-768](screenshots/84-asset-768.png)、[85-asset-mobile](screenshots/85-asset-mobile.png) |
| 盘点上架 | `/data-asset/inventory` | 已观察 | [26-asset-inventory](screenshots/26-asset-inventory.png) |
| 目录与标签 | `/data-asset/taxonomy` | 已观察 | [27-taxonomy](screenshots/27-taxonomy.png) |
| 资产详情 | `/data-asset/detail/:id` | 已观察 | [73-asset-drawer](screenshots/73-asset-drawer.png)、[74-asset-sections](screenshots/74-asset-sections.png) |
| 模型详情 | `/modeling/models/:id` | 未形成独立浏览器证据，逐批补查 |  |
| 来源映射 | `/modeling/models/:id/mapping` | 未形成独立浏览器证据，逐批补查 |  |
| 字段分层映射 | `/modeling/models/:id/layer-mapping` | 未形成独立浏览器证据，逐批补查 |  |
| 业务过程主线视图 | `/modeling/mainline` | 已观察 | [67-modeling-mainline](screenshots/67-modeling-mainline.png) |
| 变更影响分析 | `/modeling/impact` | 已观察 | [68-modeling-impact](screenshots/68-modeling-impact.png) |
| 文件资源 | `/resource-management` | 已观察 | [05-resources](screenshots/05-resources.png) |
| 质量总览 | `/data-quality/overview` | 已观察 | [31-quality-overview](screenshots/31-quality-overview.png) |
| 数据表监控 | `/data-quality/table-config` | 已观察 | [32-quality-tables](screenshots/32-quality-tables.png) |
| 新增监控 | `/data-quality/monitor/create` | 已观察 | [94-quality-monitor-form](screenshots/94-quality-monitor-form.png) |
| 规则管理 | `/data-quality/monitor/:id` | 未形成独立浏览器证据，逐批补查 |  |
| 编辑监控 | `/data-quality/monitor/:id/edit` | 未形成独立浏览器证据，逐批补查 |  |
| 质量检查记录 | `/data-quality/execution` | 已观察 | [33-quality-runs](screenshots/33-quality-runs.png) |
| 质量检查详情 | `/data-quality/execution/:executionNo` | 已观察 | [93-quality-detail](screenshots/93-quality-detail.png) |
| 规则模板 | `/data-quality/rule-template` | 已观察 | [34-rule-templates](screenshots/34-rule-templates.png) |
| 用户管理 | `/system/users` | 已观察 | [59-users](screenshots/59-users.png) |
| 部门管理 | `/system/departments` | 已观察 | [60-departments](screenshots/60-departments.png) |
| 角色与权限 | `/system/roles` | 已观察 | [61-roles](screenshots/61-roles.png) |
| 权限管理 | `/system/permissions` | 未形成独立浏览器证据，逐批补查 |  |
| 项目空间 | `/system/projects` | 已观察 | [58-projects](screenshots/58-projects.png) |
| 资源授权 | `/system/resource-permissions` | 功能开关关闭，未访问 |  |
| 系统配置 | `/system/configs` | 功能开关关闭，未访问 |  |
| 操作日志 | `/system/oplogs` | 已观察 | [62-operation-logs](screenshots/62-operation-logs.png) |
| 消息中心 | `/system/messages` | 已观察 | [63-messages](screenshots/63-messages.png) |

## 其他状态

- [01-home-desktop](screenshots/01-home-desktop.png)：`/home`
- [82-asset-1280](screenshots/82-asset-1280.png)：`/data-asset/catalog`
- [83-asset-1024](screenshots/83-asset-1024.png)：`/data-asset/catalog`
- [84-asset-768](screenshots/84-asset-768.png)：`/data-asset/catalog`
- [85-asset-mobile](screenshots/85-asset-mobile.png)：`/data-asset/catalog`
- [86-home-mobile](screenshots/86-home-mobile.png)：`/home`
- [87-datasource-mobile](screenshots/87-datasource-mobile.png)：`/data-source`
- [88-drawer-mobile](screenshots/88-drawer-mobile.png)：`/data-source`

登录页仅完成登录进入，未完成独立商业化视觉复核；403/404、普通角色权限、各隐藏详情全部标签、运行中的图谱、长文本/大数据量、实际保存/发布/运行/删除、API 调用与键盘全链路留待各批实施验收。无样本的 Dataset / 仪表盘 / 大屏详情和编辑态不判为已通过。

截图中暂时加载态或截图渲染模糊不能作为视觉缺陷；判断以稳定页面 DOM、对应组件与重复观察为准。
