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

持久化对象归属本模块的 `dao.model`；权限码与错误码继续使用 `data-ops-common` 的稳定共享契约。

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

## Flyway 历史兼容

`yak-modeling` 保存公共迁移和累计基线。V22 和 V24 均存在不同的历史脚本，启动时读取
`flyway_schema_history_modeling` 的 V22、V24 `script`，再加载相应历史目录：

- `V22__model_version_meta_snapshot.sql` → `yak-modeling-history-v22-meta`
- `V22__model_version_foundation.sql` → `yak-modeling-history-v22-foundation`
- `V24__model_impact_analysis.sql` → `yak-modeling-history-impact`
- `V24__logical_modeling_foundation.sql` → `yak-modeling-history-logical`

若 V22 已执行元数据快照迁移，跳过 impact 历史的 V25 或 logical 历史的 V27 快照迁移；若 V22 已创建逻辑模型版本表，跳过后续同表的建表迁移。其余历史按对应的 `snapshot`、`foundation` 目录补齐。各目录中的既有迁移按原历史保留版本号和脚本名。不要重命名或编辑已应用脚本；新增空库基线继续放在公共 `yak-modeling` 目录。


## F-023 场景 Skill 标准匹配

F-023：原标准助手消费 AI 类型候选，仅带入当前未保存字段。模型定义仍由 Modeling 拥有，ModelSuggestionQueryApi 以授权事务锁读取定义指纹；编辑上下文同时给出结构与指纹，保存可携带 If-Match，在原结构事务/审计之前拒绝过期定义。原发布快照与审批指纹序列化保持兼容。

依赖：Agent runtime → toolset → gateway → semantic.api / modeling.api；源域不依赖 Agent，不新增状态机或第二业务真相。合同见 [F-023](../../docs/product/features/F-023-skill-standard-match.md)。
