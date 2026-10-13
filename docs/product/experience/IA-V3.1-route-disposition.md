# IA V3.1｜106/106 Route 去向与 4/4 旧地址兼容台账

状态：DESIGN REVISED / 不调整现有路由、菜单码、权限继承或 API。基线 main@1935906b33934d3e63a432f9d428b17abdabb17b。
来源：data-ops-ui/src/config/navigation.ts、config/routes.ts、productFeatures.ts、securityMenuCodes.ts。此表的目标工作台是**设计归属**，不表示目标 URL 已存在。

## 当前口径

- 106 appRoutes / 62 当前开关下非隐藏 / 44 隐藏；非隐藏为 54 业务叶子 + 6 设置 + 2 工作入口；另有 4 条业务旧地址。默认任务工作台的既有代表锚点共 20 个，仍需按角色检验，不等于 20 个新的 route。
- 实际用户可见性还要看 menuCodes、permissionCodes、Project。隐藏于侧栏≠关闭授权，parentId 当前兼有权限继承、高亮，视觉归属不能强行修改权限父链。
- 作用域为导航规划维度，不能替代实际 API 证据。query 键名不能凭静态 path 编造；旧 query 和参数保持，实施前逐页补齐真实 URL 入参合同。
- PRIMARY=候选默认入口；COMPOSE=组合视图；TOOL=项目级专业工具必须可发现；DETAIL=对象详情/编辑；COMPAT=旧路由；GATED=功能开关关闭；SETTING=委派管理；WORK=首页/待办。

## 完整矩阵

### 接入

| # | ID / 现有路径 | 显隐 | 当前权限 / menuCode | 范围 | V3.1 归属 | 决策 | 回链与约束 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 4 | data-source<br/>/data-source | 非隐藏 | resource:data-source:read<br/>data-source | 项目/集合 | 接入 / 数据源 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 5 | sql-execution-audit<br/>/sql-execution-audit | 非隐藏 | resource:sql-execution:read<br/>sql-execution-audit | 项目/集合 | 接入 / SQL 审计 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 28 | batch-link-up<br/>/sync/batch-link-up | 非隐藏 | task:batch:read<br/>batch-link-up | 项目/集合 | 接入 / 同步任务 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 29 | realtime-sync-detail<br/>/sync/realtime/:id/detail | 隐藏 | public/继承<br/>realtime-sync | 对象ID/版本 | 接入 / 同步任务 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 30 | realtime-sync<br/>/sync/realtime | 非隐藏 | task:realtime:read<br/>realtime-sync | 项目/集合 | 接入 / 同步任务 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 31 | batch-link-up-detail<br/>/sync/batch-link-up/:id/detail | 隐藏 | public/继承<br/>batch-link-up | 对象ID/版本 | 接入 / 同步任务 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 32 | batch-link-up-single<br/>/sync/batch-link-up/:id/config/single | 隐藏 | public/继承<br/>batch-link-up | 对象ID/版本 | 接入 / 同步任务 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 33 | batch-link-up-multi<br/>/sync/batch-link-up/:id/config/multi | 隐藏 | public/继承<br/>batch-link-up | 对象ID/版本 | 接入 / 同步任务 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 34 | batch-link-up-script<br/>/sync/batch-link-up/:id/config/script | 隐藏 | public/继承<br/>batch-link-up | 对象ID/版本 | 接入 / 同步任务 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 89 | resource-management<br/>/resource-management | 非隐藏 | resource:view<br/>resource-management | 项目/集合 | 接入 / 文件资源 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |

### 定义

| # | ID / 现有路径 | 显隐 | 当前权限 / menuCode | 范围 | V3.1 归属 | 决策 | 回链与约束 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 45 | modeling-workspace<br/>/modeling | 非隐藏 | modeling:read<br/>modeling-workspace | 项目/集合 | 定义 / 模型工作台 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 46 | semantic-standards<br/>/semantic/standards | 非隐藏 | semantic:read<br/>semantic-standard | 项目/集合 | 定义 / 数据标准 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 47 | semantic-domains<br/>/semantic/domains | 非隐藏 | semantic:read<br/>semantic-domain | 项目/集合 | 定义 / 业务语义 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 48 | semantic-processes<br/>/semantic/processes | 非隐藏 | semantic:read<br/>semantic-process | 项目/集合 | 定义 / 业务语义 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 49 | semantic-process-edit<br/>/semantic/processes/:id/edit | 隐藏 | public/继承<br/>semantic-process | 对象ID/版本 | 定义 / 业务语义 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 50 | semantic-fields<br/>/semantic/fields | 非隐藏 | semantic:read<br/>semantic-field | 项目/集合 | 定义 / 数据标准 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 51 | semantic-layers<br/>/semantic/layers | 非隐藏 | semantic:read<br/>semantic-layer | 项目/集合 | 定义 / 分层配置 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 58 | metric-manage<br/>/metric/manage | 非隐藏 | metric:read<br/>metric-manage | 项目/集合 | 定义 / 指标中心 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 59 | metric-detail<br/>/metric/manage/:id | 隐藏 | public/继承<br/>metric-manage | 对象ID/版本 | 定义 / 指标中心 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 60 | metric-lineage<br/>/metric/lineage | 非隐藏 | metric:read<br/>metric-lineage | 项目/集合 | 定义 / 指标中心 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 61 | metric-impact<br/>/metric/impact | 非隐藏 | metric:read<br/>metric-impact | 反向查询 | 定义 / 反向指标影响 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 62 | metric-service<br/>/metric/service | 非隐藏 | metric:read<br/>metric-service | 项目/集合 | 定义 / 指标中心 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 83 | modeling-logical-draft<br/>/modeling/logical | 隐藏 | public/继承<br/>modeling-workspace | 项目/集合 | 定义 / 模型工作台 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 84 | modeling-model-detail<br/>/modeling/models/:id | 隐藏 | public/继承<br/>modeling-workspace | 对象ID/版本 | 定义 / 模型工作台 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 85 | modeling-model-mapping<br/>/modeling/models/:id/mapping | 隐藏 | public/继承<br/>modeling-workspace | 对象ID/版本 | 定义 / 模型工作台 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 86 | modeling-model-layer-mapping<br/>/modeling/models/:id/layer-mapping | 隐藏 | public/继承<br/>modeling-workspace | 对象ID/版本 | 定义 / 模型工作台 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 87 | modeling-mainline<br/>/modeling/mainline | 隐藏 | public/继承<br/>modeling-workspace | 项目/集合 | 定义 / 模型工作台 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 88 | modeling-impact<br/>/modeling/impact | 隐藏 | public/继承<br/>modeling-workspace | 项目/集合 | 定义 / 模型工作台 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |

### 生产

| # | ID / 现有路径 | 显隐 | 当前权限 / menuCode | 范围 | V3.1 归属 | 决策 | 回链与约束 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 35 | data-development<br/>/data-development | 非隐藏 | data-development:read<br/>data-development | 项目/集合 | 生产 / 开发工作台 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 36 | data-development-release<br/>/data-development/releases | 非隐藏 | data-development:read<br/>data-development-release | 项目/集合 | 生产 / 发布与运行 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 37 | data-development-execution<br/>/data-development/executions | 非隐藏 | data-development:read<br/>data-development-execution | 项目/集合 | 生产 / 发布与运行 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 38 | data-development-task-new<br/>/data-development/task/new | 隐藏 | public/继承<br/>data-development | 项目/集合 | 生产 / 开发工作台 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 39 | data-development-task<br/>/data-development/task/:id | 隐藏 | public/继承<br/>data-development | 对象ID/版本 | 生产 / 开发工作台 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 40 | workflow-definition<br/>/workflow/definitions | 非隐藏 | public/继承<br/>workflow-definition | 项目/集合 | 生产 / 工作流 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 41 | workflow-definition-editor<br/>/workflow/definition/:id | 隐藏 | public/继承<br/>workflow-definition | 对象ID/版本 | 生产 / 工作流 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 42 | workflow-schedules<br/>/workflow/definition/:id/schedule | 隐藏 | public/继承<br/>workflow-definition | 对象ID/版本 | 生产 / 工作流 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 43 | workflow-instances<br/>/workflow/instances | 非隐藏 | public/继承<br/>workflow-instances | 项目/集合 | 生产 / 工作流 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 44 | workflow-instance-detail<br/>/workflow/instances/:executionId | 隐藏 | public/继承<br/>workflow-instances | Run/Execution | 生产 / 工作流 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |

### 治理

| # | ID / 现有路径 | 显隐 | 当前权限 / menuCode | 范围 | V3.1 归属 | 决策 | 回链与约束 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 15 | data-analysis-lineage<br/>/data-analysis/lineage | 非隐藏 | public/继承<br/>data-analysis-lineage | 反向查询 | 治理 / 血缘影响 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 52 | mdm-overview<br/>/mdm/overview | 非隐藏 | mdm:read<br/>mdm-overview | 项目/集合 | 治理 / 主数据专业工作台 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 53 | mdm-modeling<br/>/mdm/modeling | 非隐藏 | mdm:read<br/>mdm-modeling | 项目/集合 | 治理 / 主数据专业工作台 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 54 | mdm-identification<br/>/mdm/identification | 非隐藏 | mdm:read<br/>mdm-identification | 项目/集合 | 治理 / 主数据专业工作台 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 55 | mdm-cleansing<br/>/mdm/cleansing | 非隐藏 | mdm:read<br/>mdm-cleansing | 项目/集合 | 治理 / 主数据专业工作台 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 56 | mdm-approval<br/>/mdm/approval | 非隐藏 | mdm:read<br/>mdm-approval | 项目/集合 | 治理 / 主数据专业工作台 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 57 | mdm-entity-detail<br/>/mdm/modeling/:id | 隐藏 | public/继承<br/>mdm-modeling | 对象ID/版本 | 治理 / 主数据专业工作台 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 63 | data-lifecycle-policy<br/>/data-lifecycle/policy | 非隐藏 | data-lifecycle:read<br/>data-lifecycle-policy | 项目/集合 | 治理 / 生命周期 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 64 | data-lifecycle-monitor<br/>/data-lifecycle/monitor | 非隐藏 | data-lifecycle:read<br/>data-lifecycle-monitor | 项目/集合 | 治理 / 生命周期运行 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 65 | data-lifecycle-storage<br/>/data-lifecycle/storage | 非隐藏 | data-lifecycle:read<br/>data-lifecycle-storage | 项目/集合 | 治理 / 生命周期统计 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 66 | data-security-overview<br/>/data-security/overview | 非隐藏 | data-security:read<br/>data-security-overview | 项目/集合 | 治理 / 数据安全 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 67 | data-security-classification<br/>/data-security/classification | 非隐藏 | data-security:read<br/>data-security-classification | 项目/集合 | 治理 / 数据安全 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 68 | data-security-access<br/>/data-security/access | 非隐藏 | data-security:read<br/>data-security-access | 项目/集合 | 治理 / 数据安全 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 69 | data-security-masking<br/>/data-security/masking | 非隐藏 | data-security:read<br/>data-security-masking | 项目/集合 | 治理 / 数据安全 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 70 | data-security-audit<br/>/data-security/audit | 非隐藏 | data-security:read<br/>data-security-audit | 项目/集合 | 治理 / 安全审计 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 71 | data-security-compliance<br/>/data-security/compliance | 非隐藏 | data-security:read<br/>data-security-compliance | 项目/集合 | 治理 / 合规管理 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 75 | data-metadata-overview<br/>/data-metadata/overview | 非隐藏 | data-metadata:read<br/>data-metadata-overview | 项目/集合 | 接入 / 元数据专业工具 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 76 | data-metadata-collect<br/>/data-metadata/collect | 非隐藏 | data-metadata:read<br/>data-metadata-collect | 项目/集合 | 接入 / 采集对账专业工具 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 77 | data-metadata-collect-detail<br/>/data-metadata/collect/:id | 隐藏 | public/继承<br/>data-metadata-collect | 对象ID/版本 | 接入 / 采集对账专业工具 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 78 | data-asset-overview<br/>/data-asset/overview | 非隐藏 | data-asset:read<br/>data-asset-overview | 项目/集合 | 治理 / 资产目录 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 79 | data-asset-catalog<br/>/data-asset/catalog | 非隐藏 | data-asset:read<br/>data-asset-catalog | 项目/集合 | 治理 / 资产目录 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 80 | data-asset-inventory<br/>/data-asset/inventory | 非隐藏 | data-asset:read<br/>data-asset-inventory | 项目/集合 | 治理 / 治理队列 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 81 | data-asset-taxonomy<br/>/data-asset/taxonomy | 非隐藏 | data-asset:read<br/>data-asset-taxonomy | 项目/集合 | 治理 / 编目配置 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 82 | data-asset-detail<br/>/data-asset/detail/:id | 隐藏 | public/继承<br/>data-asset-catalog | 对象ID/版本 | 治理 / 资产目录 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 90 | data-quality-overview<br/>/data-quality/overview | 非隐藏 | quality:execution:read<br/>data-quality-overview | 项目/集合 | 治理 / 质量治理 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 91 | data-quality-table-config<br/>/data-quality/table-config | 非隐藏 | quality:monitor:read<br/>data-quality-table-config | 项目/集合 | 治理 / 质量治理 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 92 | data-quality-monitor-create<br/>/data-quality/monitor/create | 隐藏 | public/继承<br/>data-quality-table-config | 项目/集合 | 治理 / 质量治理 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 93 | data-quality-monitor-detail<br/>/data-quality/monitor/:id | 隐藏 | public/继承<br/>data-quality-table-config | 对象ID/版本 | 治理 / 质量治理 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 94 | data-quality-monitor-edit<br/>/data-quality/monitor/:id/edit | 隐藏 | public/继承<br/>data-quality-table-config | 对象ID/版本 | 治理 / 质量治理 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 95 | data-quality-execution<br/>/data-quality/execution | 非隐藏 | quality:execution:read<br/>data-quality-execution | 项目/集合 | 治理 / 质量检查 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 96 | data-quality-execution-detail<br/>/data-quality/execution/:executionNo | 隐藏 | public/继承<br/>data-quality-execution | Run/Execution | 治理 / 质量检查 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 97 | data-quality-rule-template<br/>/data-quality/rule-template | 非隐藏 | quality:template:read<br/>data-quality-rule-template | 项目/集合 | 治理 / 规则模板 | TOOL | 项目级可发现；可对象预筛选；独立权限 |

### 消费

| # | ID / 现有路径 | 显隐 | 当前权限 / menuCode | 范围 | V3.1 归属 | 决策 | 回链与约束 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 6 | dashboard<br/>/dashboard | 非隐藏 | public/继承<br/>dashboard | 项目/集合 | 消费 / 分析展示 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 7 | dashboard-new<br/>/dashboard/new | 隐藏 | public/继承<br/>dashboard | 项目/集合 | 消费 / 分析展示 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 8 | dashboard-editor<br/>/dashboard/:id/edit | 隐藏 | public/继承<br/>dashboard | 对象ID/版本 | 消费 / 分析展示 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 9 | dashboard-viewer<br/>/dashboard/:id | 隐藏 | public/继承<br/>dashboard | 对象ID/版本 | 消费 / 分析展示 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 10 | consumption-catalog<br/>/data-analysis/consumption | 非隐藏 | data-asset:read<br/>consumption-catalog | 项目/集合 | 消费 / 数据产品目录 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 11 | consumption-detail<br/>/data-analysis/consumption/:productKey | 隐藏 | public/继承<br/>consumption-catalog | ProductKey | 消费 / 数据产品目录 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 12 | dataset-management<br/>/dataset | 非隐藏 | public/继承<br/>dataset-management | 项目/集合 | 消费 / 数据集 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 13 | dataset-management-detail<br/>/dataset/:id | 隐藏 | public/继承<br/>dataset-management | 对象ID/版本 | 消费 / 数据集 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 14 | data-analysis-catalog<br/>/data-analysis/data-catalog | 隐藏 | public/继承<br/>— | 项目/集合 | 消费 / 旧数据目录兼容 | COMPAT | 旧 URL / 参数 / 后退兼容 |
| 16 | digital-screen<br/>/digital-screen | 非隐藏 | public/继承<br/>digital-screen | 项目/集合 | 消费 / 分析展示 | COMPOSE | 工作台视图；旧路由和子能力授权保留 |
| 17 | digital-screen-new<br/>/digital-screen/new | 隐藏 | public/继承<br/>digital-screen | 项目/集合 | 消费 / 分析展示 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 18 | digital-screen-editor<br/>/digital-screen/:id/edit | 隐藏 | public/继承<br/>digital-screen | 对象ID/版本 | 消费 / 分析展示 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 19 | digital-screen-viewer<br/>/digital-screen/:id | 隐藏 | public/继承<br/>digital-screen | 对象ID/版本 | 消费 / 分析展示 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 20 | data-analysis-chart<br/>/data-analysis/chart-analysis | 隐藏 | public/继承<br/>dashboard | 项目/集合 | 消费 / 分析展示 | COMPAT | 旧 URL / 参数 / 后退兼容 |
| 21 | data-service-api<br/>/data-service | 非隐藏 | data-service:read<br/>data-service-api | 项目/集合 | 消费 / API 服务 | PRIMARY | 旧 URL 与菜单授权保留；按子能力权限展示 |
| 22 | data-service-api-detail<br/>/data-service/api/:id | 隐藏 | public/继承<br/>data-service-api | 对象ID/版本 | 消费 / API 服务 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 23 | data-service-access<br/>/data-service/access | 非隐藏 | data-service:access<br/>data-service-access | 项目/集合 | 消费 / 调用方与密钥 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 24 | data-service-debug<br/>/data-service/debug | 非隐藏 | data-service:read,data-service:runtime<br/>data-service-debug | 项目/集合 | 消费 / API 调试 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 25 | data-service-overview<br/>/data-service/overview | 非隐藏 | data-service:observe<br/>data-service-overview | 项目/集合 | 消费 / API 运行 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 26 | data-service-logs<br/>/data-service/logs | 非隐藏 | data-service:observe<br/>data-service-logs | 项目/集合 | 消费 / API 调用记录 | TOOL | 项目级可发现；可对象预筛选；独立权限 |

### 工作/设置

| # | ID / 现有路径 | 显隐 | 当前权限 / menuCode | 范围 | V3.1 归属 | 决策 | 回链与约束 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 1 | home<br/>/home | 非隐藏 | public/继承<br/>home | 项目/集合 | 工作首页 | WORK | 原授权和任务回链 |
| 2 | quick-create<br/>/create | 隐藏 | public/继承<br/>home | 项目/集合 | 工作首页 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 3 | ai-agent<br/>/ai-agent | 隐藏 | public/继承<br/>home | 项目/集合 | Agent 协作 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 27 | settings<br/>/settings | 隐藏 | public/继承<br/>— | 项目/集合 | 平台设置 | COMPAT | 旧 URL / 参数 / 后退兼容 |
| 72 | approval-todo<br/>/approval/todo | 非隐藏 | data-approval:read<br/>data-approval-todo | 项目/集合 | 我的待办 | WORK | 原授权和任务回链 |
| 73 | approval-flows<br/>/approval/flows | 非隐藏 | data-approval:manage<br/>data-approval-flows | 项目/集合 | 平台设置 | SETTING | 按委派能力授权；不得加管理员门槛 |
| 74 | approval-detail<br/>/approval/instance/:id | 隐藏 | public/继承<br/>data-approval-todo | 对象ID/版本 | 我的待办 | DETAIL | 对象 ID / 冻结证据；返回来源；dirty 保护 |
| 98 | system-users<br/>/system/users | 非隐藏 | security:user:read<br/>system-users | 平台配置 | 平台设置 | SETTING | 按委派能力授权；不得加管理员门槛 |
| 99 | system-departments<br/>/system/departments | 非隐藏 | security:department:read<br/>system-departments | 平台配置 | 平台设置 | SETTING | 按委派能力授权；不得加管理员门槛 |
| 100 | system-roles<br/>/system/roles | 非隐藏 | security:role:read<br/>system-roles | 平台配置 | 平台设置 | SETTING | 按委派能力授权；不得加管理员门槛 |
| 101 | system-permissions<br/>/system/permissions | 隐藏 | security:permission:read<br/>system-permissions | 平台配置 | 工作入口 | TOOL | 项目级可发现；可对象预筛选；独立权限 |
| 102 | system-security-projects<br/>/system/projects | 非隐藏 | security:project:read<br/>system-security-projects | 平台配置 | 平台设置 | SETTING | 按委派能力授权；不得加管理员门槛 |
| 103 | system-resource-permissions<br/>/system/resource-permissions | 功能关闭 | security:resource-permission:read<br/>system-resource-permissions | 平台配置 | 平台设置 | GATED | 不开启功能；旧地址保持门禁 |
| 104 | system-configs<br/>/system/configs | 功能关闭 | security:config:read<br/>system-configs | 平台配置 | 平台设置 | GATED | 不开启功能；旧地址保持门禁 |
| 105 | system-operation-logs<br/>/system/oplogs | 非隐藏 | security:operation-log:read<br/>system-operation-logs | 平台配置 | 平台设置 | SETTING | 按委派能力授权；不得加管理员门槛 |
| 106 | system-messages<br/>/system/messages | 隐藏 | public/继承<br/>— | 平台配置 | 消息中心（兼容） | COMPAT | 旧 URL / 参数 / 后退兼容 |

## 四个业务旧地址（不在 106 条 appRoutes 中）

| 路径 | 当前处理 | V3.1 回归义务 |
| --- | --- | --- |
| /data-metadata/catalog | /data-asset/catalog?view=entity | 技术实体双视图与项目权限；后退刷新 |
| /metadata/explorer | /data-asset/catalog?view=entity | 旧筛选及技术实体身份，不误报资产已上架 |
| /metadata/detail/:id | data-metadata/detail-redirect | :id 精确返回和身份重验 |
| /data-metadata/search | /data-asset/catalog?view=entity | 参数兼容、空/无权/服务失败区分 |

## 后续实施证据（不可用本表替代）

每个实际改变呈现的路由，实施前补齐：真实 query schema、上游跳转来源和返回目标、Owner 的 Object/Run/Version identity、Project 重新核验、菜单 RBAC 与 Flyway 有效目录、失败/撤权/切项目/迟到响应/脏表单回归证据。
最低授权负例：SQL 审计单独 resource:sql-execution:read；调用方单独 data-service:access；服务运行单独 data-service:observe；质量执行单独 quality:execution:read。组合不能引入它们邻居的访问门槛。
迁移须追加新版本而非改已执行的 V2 SQL；验证升级和新装的有效菜单目录、MySQL/PostgreSQL 和回退；不通过隐藏菜单删除旧角色 grant。
