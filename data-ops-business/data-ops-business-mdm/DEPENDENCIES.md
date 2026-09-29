# MDM Dependencies

## 本模块依赖(出向)

| 依赖 | 范围 | 原因 |
| --- | --- | --- |
| `data-ops-common` | 编译 | PO(`bean.po.mdm`)、错误码(`enums.mdm`)、权限码(`constant.mdm`)——平台惯例 |
| `data-security-spring-boot-starter` | 编译 | `Result`/`PagingData`/`BusinessException`/`@RequiresPermission`/`CurrentUserProvider` |
| `data-ops-business-audit` | 编译 | `BusinessAuditService`/`AuditTransactions` 审计门面(fail-open) |
| `data-ops-business-datasource` | 编译(optional) | **仅基础设施**:`BusinessDatabaseConfiguration`(共享数据源/SqlSessionFactory/事务管理器)与 `ConditionalOnDataSourceEnabled`;识别/采集经其公共契约读元数据。禁止使用其内部实现类型 |
| `data-ops-business-semantic` | 编译(optional,52 起) | 主数据属性类型/单位/码值/安全引用数据标准;经其公共契约(SPI/options)消费,不直读表 |
| `data-ops-business-sync` | 编译(optional,54/55 起) | 采集执行/调度/字段映射复用 sync;MDM 只查询「主数据」标签任务执行状态(离线+实时,D-M11),不建采集 |
| `data-ops-business-task-catalog`(数据开发任务目录) | 编译(optional,55 起) | 主数据加工任务生成与注册:MDM 按实体/属性/来源绑定生成加工任务,经任务目录交数据开发执行(参照 modeling 44 模式,D-M11) |
| `data-ops-business-quality` | 编译(optional,56/61 起) | 质量规则复用;MDM 只做去重/合并/标准化/补全(特有) |
| `data-ops-business-data-service` | 编译(optional,58/59 起) | API 管理与缓存复用;MDM 只做分发配置与订阅(特有) |
| `data-ops-business-lineage` | 编译(optional,61 起) | 血缘复用;MDM 只做来源/分发聚合展示 |
| `data-ops-business-dataset` | 编译(optional,62 起) | 资产统计复用;MDM 只做主数据特有分析 |

## 被依赖(入向,规划)

| 模块 | 方式 | ticket |
| --- | --- | --- |
| `data-ops-business-modeling` | **仅经 `api` 包 SPI**(EntityQueryApi/MdmServiceApi),数仓维表引用 master_id;禁止直读本模块表 | 后续 |

## 禁止

- **禁止 import 被依赖模块的内部实现类型**(dao/dao.model/repository.impl 一律不对外);datasource 仅公共契约与基础设施。
- 依赖方向单向:modeling → mdm → datasource/sync/quality/data-service/semantic/lineage/security/dataset;**不允许反向依赖**。
- 跨模块数据引用 = 松散 ID(无物理外键),展示名经 SPI 解析。
