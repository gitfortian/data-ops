# Data Service Review Guide

> 用于 Data Service 需求实现、重构和 PR Review。目标不是追求某种框架形式，而是保护发布来源、执行安全、访问控制和 Runtime Truth 的边界。

## 1. 开始实现前

### Requirement

- [ ] `REQUIREMENTS.md` 已描述目标行为。
- [ ] 如果没有，先报告并确认 `Requirement Gap`。
- [ ] 明确 REST / DB / SourceProvider / SqlExecution compatibility 是否变化。

### Domain

- [ ] 已确认 Truth Owner：Source Revision、DataServiceDefinition、RuntimePolicy 还是 process-local Runtime state？
- [ ] 是否需要新的 Aggregate/value object，而不是 Map key / boolean / PO 字段？
- [ ] 是否改变 publish/republish/access/runtime 生命周期不变量？

### Architecture

- [ ] 新职责有明确 package + role owner。
- [ ] 没有为了“方便调用”把逻辑塞回通用 Service。
- [ ] 新 import edge 已对照 `DEPENDENCIES.md`，不会形成 cycle。

## 2. Publication Review

- [ ] SQL 是否仍只能来自服务端 Source Provider？
- [ ] `dataSourceId` 是否仍只能来自 resolved source？
- [ ] 是否要求有效 immutable Revision？
- [ ] 是否只允许 publishable/ONLINE source？
- [ ] Republish 是否更新同一个 Data Service ID？
- [ ] Republish 是否保留 AuthMode / API Keys / RuntimePolicy？
- [ ] Source-managed name/path/limits/contract 是否仍由 Source 拥有？
- [ ] 客户端是否意外获得覆盖 source-owned definition 的入口？
- [ ] 无 Provider 的 frozen legacy source 是否仍拒绝伪 republish？

## 3. SQL / Execution Review

- [ ] 仍然只允许单条 SELECT。
- [ ] 请求参数通过 named binding，不做字符串拼 SQL。
- [ ] 参数提取是否正确忽略字符串/注释/`::` 等非参数语法？
- [ ] 缺失参数是否快速失败？
- [ ] Pagination control 是否从 SQL 参数中移除？
- [ ] Pagination identity 是否进入 cache key？
- [ ] `pageSize <= maxRows`？
- [ ] 物理执行是否统一走 `SqlExecutionRuntime`？
- [ ] Caller/context 是否仍标记 Data Service identity？
- [ ] 非 ResultSet 是否失败？

## 4. Access / Secret Review

- [ ] raw API Key 是否只在 create/rotate 返回一次？
- [ ] raw secret 是否可能进入 PO、日志、异常、`toString()`、metrics tag？
- [ ] DB 是否只保存 hash + prefix？
- [ ] API_KEY mode 是否至少要求一个有效 Key？
- [ ] 是否阻止 disable/delete 最后一个有效 Key？
- [ ] rotate/disable/delete 是否清理本节点 rate-limit bucket？
- [ ] expired/disabled/invalid key 是否返回 401？
- [ ] local quota exceeded 是否返回 429？
- [ ] 调用日志只保存 Key identity snapshot，不保存 secret？

## 5. Runtime Review

- [ ] 持久化 `RuntimePolicy` 与 `LocalDataServiceRuntime` state 是否明确分离？
- [ ] 是否错误地把单节点 Cache/Circuit/Metrics 当成集群 Truth？
- [ ] Policy 变化是否使旧本地 state 失效？
- [ ] Republish/disable/delete 是否清理应失效的 state？
- [ ] Circuit open 是否保持 503？
- [ ] Cache key 是否包含 SQL + bindings + pagination identity？
- [ ] Cache 是否可能跨 API、跨参数或跨分页误命中？
- [ ] Metrics 失败是否会改变业务定义？不应该。

## 6. Documentation Review

- [ ] SQL parameter names 是否仍是 parameter docs 的事实来源？
- [ ] 文档是否拒绝 SQL 中不存在的参数？
- [ ] SQL 新参数是否自动进入当前 contract？
- [ ] 删除参数是否不会继续暴露？
- [ ] schema type 是否经过 allowlist，不静默降级未知值？
- [ ] SQL fingerprint 变化是否能反映 `schemaStale`？
- [ ] OpenAPI path/auth/parameters/response 是否基于当前 Data Service？

## 7. Observability Review

- [ ] 成功和失败调用是否都记录？
- [ ] auth/rate-limit rejection 是否有可识别 audit？
- [ ] 参数/error 是否有长度限制？
- [ ] 是否记录 raw secret 或数据库凭据？禁止。
- [ ] 历史 log 是否保持 snapshot 语义，不随当前名称/Key 改动重写？
- [ ] Overview 是否只做 read-side 聚合，不成为 definition/runtime state owner？

## 8. Persistence Review

- [ ] 新业务代码是否直接 import `dao.mapper` / `dao.model`？除 RepositoryAdapter 外禁止。
- [ ] Controller 是否直接依赖 Repository？禁止。
- [ ] PO -> Domain / Domain -> PO 是否显式映射？
- [ ] 是否为了 ORM 便利给 Domain 增加 broad setter / `@Data`？禁止。
- [ ] 是否不必要修改现有 Flyway/表结构？结构重构不应修改。

## 9. Cross-module Review

- [ ] Data Service 是否反向 import Data Development？禁止。
- [ ] 上游实现是否只依赖 `publication.source.DataServiceSourceProvider`？
- [ ] SourceProvider contract 是否仍保持轻量，不暴露 Repository/Runtime/HTTP 类型？
- [ ] Data Service 是否直接依赖 Datasource DAO/Mapper/Plugin implementation？禁止。
- [ ] SQL 是否通过 core contract，而非 Datasource 内部实现？

## 10. Package / Naming Review

- [ ] 是否新增 `service/common/helper/utils/util/base` package？禁止。
- [ ] 是否新增 `*ServiceImpl`？默认禁止。
- [ ] 是否新增 generic `*Helper/*Utils`？默认禁止。
- [ ] 类名是否能回答它是什么角色：Manager/Reader/Publisher/Registry/Authorizer/Compiler/Invoker/Executor/Runtime/Recorder/Repository/Adapter/Renderer/Factory？
- [ ] 是否出现 `BeanUtils.copyProperties` 代替显式 Domain mapping？避免。
- [ ] 是否使用字段注入？禁止。
- [ ] 是否有 wildcard import / System.out / System.err？禁止。

## 11. Dependency Review

重点检查：

```text
query -> execution        forbidden
runtime -> execution      forbidden
business role -> dao      forbidden
controller -> repository  forbidden
domain -> anything        forbidden
Data Service -> Data Development forbidden
```

合法共享能力应通过 Domain value、窄 port 或 Repository port 解耦，而不是互相 import implementation。

## 12. 测试最低要求

涉及对应能力时至少覆盖：

- Domain lifecycle/invariant；
- publish/republish ownership；
- SELECT-only / named parameter；
- API Key raw secret / auth invariant；
- cache/circuit behavior；
- documentation parameter truth；
- HTTP contract 不接受 SQL/dataSourceId；
- SourceProvider compatibility；
- architecture dependency/code-style guard。

## 13. 合并前报告

PR 描述建议包含：

```text
Requirement Compliance
- requirements covered:
- behavior intentionally changed:

Domain Compliance
- truth owner/invariants:
- domain gaps:

Architecture Compliance
- packages/roles changed:
- new dependency edges:
- guards updated:

Compatibility
- REST:
- DB/Flyway:
- SourceProvider:
- SqlExecutionRuntime:
- HTTP status:

Validation
- tests actually executed:
- static checks:
- environment limitations:
```

只声明实际跑过的验证；源码 Review 不等于 Maven/CI green。
