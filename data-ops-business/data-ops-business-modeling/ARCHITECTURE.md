# Modeling Architecture

本文定义建模模块的 package 角色与边界。当前为骨架阶段,结构随 ticket 演进,但分层边界从第一行业务代码起生效。

## Current(_ticket 01~02)

```text
io.yak.ops.business.modeling
  ├── config
  │    ├── ConditionalOnModelingPersistence   // 持久化开关(数据源启用),契约保持在建模边界
  │    └── ModelingPersistenceConfiguration   // 自持 Flyway + @MapperScan(dao.mapper → yakBusinessSqlSessionFactory)
  ├── controller/v1                           // REST + DTO/VO + converter(v1 下含 api/dto/vo/converter)
  ├── catalog                                 // 模型目录 CRUD + 目录树/标签组织门面(ticket 02~04;领域规则唯一归属)
  ├── structure                               // 表结构编辑:表基础信息 + 字段全量替换(ticket 05;领域规则唯一归属)
  ├── support                                 // 横切支撑:audit/AuditTransactions(事务提交后落审计事件,唯一实现)
  ├── repository                              // Repository 接口 + MyBatis 适配器(项目隔离在这里强制)
  ├── dao/mapper                              // MyBatis-Plus BaseMapper(model/model_column/directory/tag/model_tag_rel),只谈表
  ├── domain                                  // Model / ModelDialect / ModelStatus / ModelingDirectory / ModelingTag / ColumnDefinition 值对象
  └── exception                               // ModelingException + 模块内 RestControllerAdvice
```

持久化对象 `ModelingModelPO` 与权限码/错误码常量按平台惯例放在 `data-ops-common`(`bean/po/modeling`、`constant/modeling`、`enums/modeling`)。

## Target Layering(后续 ticket 按此演进)

```text
HTTP / neighboring module
          ↓
   controller/v1(REST + DTO/VO + converter,不落业务规则)
          ↓
   service / 角色门面(领域规则唯一归属地)
          ↓
   repository(领域对象 ↔ 持久化模型转换)
          ↓
   dao(MyBatis-Plus mapper,只谈表)
```

规划中的角色包(与 dev-plan ticket 对应,新增包必须先更新本文件):

```text
catalog/      模型目录与 CRUD、目录树/标签组织、回收站(ticket 02~04)
structure/    表结构编辑:表基础信息 + 字段编辑 + 主键/索引/分区 + 方言校验(ticket 05~07)
import/       逆向导入、批量导入(ticket 08/26)
ddl/          多方言 DDL 生成器(ticket 09/10)
relation/     模型关联关系与画布数据(ticket 11/12)
version/      发布、版本快照、结构 diff(ticket 13/14)
logical/      逻辑模型、实体/属性/关系、逻辑→物理生成(ticket 15~18)
mapping/      来源映射与加工 SQL 生成(ticket 19~21)
lineage/      血缘引导登记(ticket 23/24)
change/       源端结构变更检测与事件(ticket 27/28)
overview/     项目空间全域视图(服务端聚合,ticket 29)
```

## 边界规则

1. **controller/v1 不含业务规则**;DTO/VO 与领域对象之间的转换放在 converter。
2. **领域规则只在 service/门面层**;dao/mapper 不做业务判断。
3. **跨模块访问只经对方模块的稳定门面/Reader**(如数据源 catalog Reader、血缘 RegistrationService、数据开发任务创建),不直插对方 dao/内部表;具体依赖清单见 [DEPENDENCIES.md](./DEPENDENCIES.md)。
4. **写路径必须携带项目空间上下文**;projectId 只取服务端可信上下文,异步任务必须能独立恢复项目上下文,不建物理外键(PROJECT_SCOPE)。
5. **统计/总览只做服务端聚合**,禁止无界 list() 后内存统计(home-overview-contract)。
6. Flyway migration 只增不改:已合入的 migration 文件不可修改。
