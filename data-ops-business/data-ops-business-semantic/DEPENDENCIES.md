# Semantic Dependencies

## 本模块依赖(出向)

| 依赖 | 范围 | 原因 |
| --- | --- | --- |
| `data-ops-common` | 编译 | PO(`bean.po.semantic`)、权限码(`constant.semantic`)、错误码(`enums.semantic`)——平台惯例 |
| `data-security-spring-boot-starter` | 编译 | `Result`/`PagingData`/`BusinessException`/`@RequiresPermission`/`CurrentUserProvider` |
| `data-ops-business-audit` | 编译 | `BusinessAuditService`/`AuditTransactions` 审计门面(fail-open) |
| `data-ops-business-approval` | 编译 | 标准生效审批:`approval` 包经 `ApprovalApi` 发起 STANDARD_PUBLISH 单 + `ApprovalFlowHandler` 回调批准即启用(同事务);前端发起入口见语义缺口单 03 |
| `data-ops-business-datasource` | 编译(optional) | **仅基础设施**:`BusinessDatabaseConfiguration`(共享数据源/SqlSessionFactory/事务管理器)与 `ConditionalOnDataSourceEnabled`;36 起按其公共契约做连通性校验。禁止使用其内部实现类型 |

## 被依赖(入向,规划)

| 模块 | 方式 | ticket |
| --- | --- | --- |
| `data-ops-business-modeling` | **仅经 `api` 包 SPI**(StandardQuery/Recommend/Capture/Usage、ProcessApi、LayerConfigApi),禁止直读本模块表 | 38/39/40/41/42/43/44/45/46/47 |
| `data-ops-business-security` | 仅经 `api` 包 SPI(StandardQueryApi):等级 `std_security_id` 引用 SECURITY 标准并校验,松散 ID | 语义缺口单 01 |
| `data-ops-business-data-development` | 仅经 `api` 包 SPI(StandardRecommendApi):SQL 编辑器输出字段命名符合度检查,只读提示不阻断 | 语义缺口单 02 |
| `data-ops-business-agent`(后续) | 同上,经 gateway 包(单一 import 点) | 48 |

## 禁止

- **禁止 import `io.yak.ops.business.modeling.*`**——依赖方向仅 modeling→semantic 单向(决策 E)。
- 禁止反向读取 modeling 表/绕过 SPI 暴露内部实现类型(dao/dao.model/repository.impl 一律不对外)。
