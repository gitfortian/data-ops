# Data Security Dependencies

## 本模块依赖(出向)

| 依赖 | 范围 | 原因 |
| --- | --- | --- |
| `data-ops-common` | 编译 | 、错误码(`enums.security`)、权限码(`constant.security`)——平台惯例 |
| `data-security-spring-boot-starter` | 编译 | `Result`/`PageData`/`BusinessException`/`ErrorCode`/`@RequiresPermission`/`CurrentUserProvider` |
| `data-ops-core`(经 common/boot) | 编译 | `CurrentProject`/`@ProjectScope`(项目空间上下文) |
| `data-ops-business-audit` | 编译 | `BusinessAuditService`/`AuditEventType`/`AuditOperationRequest` 审计门面(fail-open) |
| `data-ops-business-datasource` | 编译(optional) | 既有数据源功能开关注解；共享持久化由 Boot 装配；发现扫描的字段目录由数据源侧以 `DiscoverableField` 传入,不反向依赖其实现类型 |
| `data-ops-business-semantic` | 编译(optional) | **已接线(语义缺口单 01)**:等级字典真源在本模块;`SecurityLevelService` 经 `StandardQueryApi` 校验 `std_security_id` 引用(存在+SECURITY+ENABLED)。语义 SECURITY 标准=字段级分级/脱敏模板(mask_rule 仅参考文案,不参与脱敏执行),松散 ID,不直读语义表 |
| spring-web / validation / tx / mybatis-plus / flyway-core / lombok | 编译 | Web 层、事务、ORM、迁移、样板 |
| spring-boot-starter-test(JUnit5+Mockito+AssertJ) | test | 单元测试 |

## 被依赖(入向)

| 模块 | 方式 | 说明 |
| --- | --- | --- |
| `data-ops-boot` | 依赖 | 聚合启动;组件扫描 `io.yak.ops` 覆盖本模块,boot pom 已引入 `data-ops-business-security` |
| modeling / metric / data-service(规划) | **仅经 `api` 包 SPI** | 消费分级标签/脱敏指令/访问裁决;禁止直读 `yak_dsec_*` 表 |

## 闭环关系(业务)

```
datasource(源) ─► 发现扫描 ─► 分级标签(CANDIDATE→ACTIVE)
                                   │(rank)
             semantic(SECURITY标准)─┤
                                   ▼
   脱敏策略 ◄── resolve ── 访问裁决(读侧鉴权+脱敏)──► 访问审计日志
                                   │                          │
                            合规体检(读分级/脱敏/审计)──► 发现项 ─► 总览
```

## 禁止

- **禁止 import 被依赖模块的内部实现类型**(dao/repository/impl 一律不对外);datasource 仅公共契约与基础设施。
- 依赖方向单向:`modeling/metric/data-service → security → datasource/semantic/audit`;**不允许反向依赖**,尤其禁止 security 反向 import modeling/metric。
- 跨模块数据引用 = 松散 ID(无物理外键),展示名经 SPI 解析。
- 敏感度口径唯一:任何"是否敏感/多敏感"的判断必须落到等级 `rank_no`,不得在下游另立标准。
