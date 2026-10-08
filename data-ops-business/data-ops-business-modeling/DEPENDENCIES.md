# Modeling Dependencies

本文定义建模模块的依赖方向。依赖只能单向、向下,禁止反向依赖与环形依赖。

## Maven 依赖(当前)

| 依赖 | scope/optional | 原因 |
| --- | --- | --- |
| `data-ops-common` | compile | 平台共享契约、权限码与错误码；PO 由本模块 dao.model 拥有 |
| `data-ops-business-datasource` | optional | 现有持久化条件与 catalog 元数据读取;建模不反向提供任何能力给数据源 |
| `data-ops-business-audit` | compile | `BusinessAuditService` 审计门面(fail-open);建模不反向提供任何能力给审计 |
| `data-security-spring-boot-starter` | compile | 框架集成:`Result`/`PagingData`/`BusinessException`、`@RequiresPermission`、`CurrentUserProvider` |
| Spring Boot Web/Validation、MyBatis-Plus、Flyway、Lombok | compile | 与 lineage 等业务模块一致的技术底座 |

模块纳入 `data-ops-business` 聚合、`data-ops-bom` dependencyManagement(`${project.version}`),由 `data-ops-boot` 以运行时依赖引入。

> Ticket 30 起依赖方向新增 `data-ops-business-semantic`(见下节);Maven 依赖在 modeling 实际消费 SPI 时(38/39)引入。

## 跨模块依赖方向(规划,随 ticket 落地并更新本文件)

```text
modeling ──> semantic     数据标准/业务过程/分层配置(ticket 30 起依赖方向确定):
                          仅经 semantic api 包 SPI(StandardQuery/Recommend/Capture/Usage、
                          ProcessApi、LayerConfigApi),禁止直读其自持表;
                          跨模块数据只存松散 ID(std_*_id/std_field_id/process_id/layer_id,无物理外键),
                          展示名经 SPI 批量解析(决策 E,单向前向依赖);
                          44 字段继承新增两个只读方法:ProcessApi.listProcessSources(过程源表绑定,
                          含 tableRole/joinCondition)、ProcessApi.listFields(标准字段库,供 38/44 匹配兜底),
                          均为纯新增,无破坏性变更
modeling ──> datasource   catalog 元数据读取(逆向导入/变更比对),只经 DataSourceCatalogReader 等稳定门面
modeling ──> lineage      血缘登记(引导式,用户确认后),只经 LineageRegistrationService
modeling ──> data-development  加工任务创建(生成 SQL 任务草稿),只经其任务/目录门面与扩展点
```

规则:

1. **单向**:以上模块不得反向依赖 modeling;建模对外的贡献只允许通过事件或明确约定的 SPI(如有,必须先在本文件登记)。
2. **只走门面**:禁止 import 其他模块的 dao/mapper/内部表;禁止直接读写其他模块的自持表。
3. **执行引擎外包**:建模不持有任务执行/调度能力(决策 D2),加工执行一律交数据开发。
4. **权限与菜单**:建模的菜单/权限目录行在 yak-security migration(`data-ops-boot`)中登记,本模块 Java 代码不操作 `yak_security_*` 表。
5. **审计与通知**:操作审计走平台审计设施;变更通知走通知/告警基础设施,不自建通知通道。
6. **初始化顺序**:建模 `V1__modeling_baseline.sql` 中历史 V18 Source 段保留了一次性历史回填,会读取语义中心的 `yak_semantic_process`;因此建模 Flyway 必须在语义中心 Flyway 成功后运行。该顺序只保障已有迁移链的初始化,不授权新增运行时代码直读语义中心表。


## F-023 场景 Skill 标准匹配

F-023：原标准助手消费 AI 类型候选，仅带入当前未保存字段。模型定义仍由 Modeling 拥有，ModelSuggestionQueryApi 以授权事务锁读取定义指纹；编辑上下文同时给出结构与指纹，保存可携带 If-Match，在原结构事务/审计之前拒绝过期定义。原发布快照与审批指纹序列化保持兼容。

依赖：Agent runtime → toolset → gateway → semantic.api / modeling.api；源域不依赖 Agent，不新增状态机或第二业务真相。合同见 [F-023](../../docs/product/features/F-023-skill-standard-match.md)。
