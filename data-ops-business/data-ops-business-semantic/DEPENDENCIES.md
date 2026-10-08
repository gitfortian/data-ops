# Semantic Dependencies

## 本模块依赖(出向)

| 依赖 | 范围 | 原因 |
| --- | --- | --- |
| `data-ops-common` | 编译 | 、权限码(`constant.semantic`)、错误码(`enums.semantic`)——平台惯例 |
| `data-security-spring-boot-starter` | 编译 | `Result`/`PagingData`/`BusinessException`/`@RequiresPermission`/`CurrentUserProvider` |
| `data-ops-business-audit` | 编译 | `BusinessAuditService`/`AuditTransactions` 审计门面(fail-open) |
| `data-ops-business-approval` | 编译 | 标准生效审批:`approval` 包经 `ApprovalApi` 发起 STANDARD_PUBLISH 单 + `ApprovalFlowHandler` 回调批准即启用(同事务);前端发起入口见语义缺口单 03 |
| `data-ops-business-datasource` | 编译(optional) | `ConditionalOnDataSourceEnabled` 持久化条件;36 起按其公共契约做连通性校验。禁止使用其内部实现类型 |

## 被依赖(入向,规划)

| 模块 | 方式 | ticket |
| --- | --- | --- |
| `data-ops-business-modeling` | **仅经 `api` 包 SPI**(StandardQuery/Recommend/Capture/Usage、ProcessApi、LayerConfigApi);并实现 `StandardReferenceReader` 供标准生命周期校验引用数,禁止直读本模块表 | 38/39/40/41/42/43/44/45/46/47 |
| `data-ops-business-metric` | 实现 `StandardReferenceReader`,向标准生命周期提供 UNIT/CALIBER 当前指标依赖计数 | 指标标准依赖保护 |
| `data-ops-business-security` | 仅经 `api` 包 SPI(StandardQueryApi):等级 `std_security_id` 引用 SECURITY 标准并校验,松散 ID | 语义缺口单 01 |
| `data-ops-business-data-development` | 仅经 `api` 包 SPI(StandardRecommendApi):SQL 编辑器输出字段命名符合度检查,只读提示不阻断 | 语义缺口单 02 |
| `data-ops-business-agent`(后续) | 同上,经 gateway 包(单一 import 点) | 48 |

## 禁止

- **禁止 import `io.yak.ops.business.modeling.*`**——依赖方向仅 modeling→semantic 单向(决策 E)。
- 禁止反向读取 modeling 表/绕过 SPI 暴露内部实现类型(dao/dao.model/repository.impl 一律不对外)。


## F-023 场景 Skill 标准匹配

F-023：StandardSuggestionQueryApi 是 Agent gateway 的授权只读入口。每次检查 semantic 读取权限与当前项目；只查启用 TYPE，SQL 最多 21 行、交付 20 行及截断标识。按 ID/版本复核时拒绝跨项目、停用、错类别、陈旧版本；故障不能折算成无候选。不接收 Agent 写命令。

依赖：Agent runtime → toolset → gateway → semantic.api / modeling.api；源域不依赖 Agent，不新增状态机或第二业务真相。合同见 [F-023](../../docs/product/features/F-023-skill-standard-match.md)。

## P0-B03：标准字段反向引用读侧 SPI

Semantic 增加 `api.SemanticFieldReferenceReader`：消费者（当前 Modeling）实现，向字段库删除校验提供可信当前 Project 的引用数量。Semantic 绝不反向依赖 Modeling 的 Java 实现或 SQL 表；Modeling 查询本域 `yak_modeling_model_column.std_field_id`（包含可恢复模型列）并在拒绝/异常时禁止删除。该策略与 `StandardReferenceReader`、`LayerStdBindingReader` 使用相同依赖方向和 Spring 注入方式，不承诺前端动态聚合其它领域的引用计数。
