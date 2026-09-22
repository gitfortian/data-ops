# Data Development Review

Review Data Development 改动时，先判断“truth 有没有换 owner”，再看代码是否漂亮。

## 必看

### Domain

- Node、Draft、Revision、Execution 是否仍然分离？
- Published Revision 是否保持 immutable？
- Editor Run 是否仍是 version-0 snapshot，而不是隐式 Publish？
- DATASET / DATA_SERVICE 是否仍然位于 Output Node lifecycle？
- Task Catalog 是否只是 projection，而不是 Revision truth？
- Lineage 是否仍是 derived evidence？
- Node / Directory 名称、Node Type 等命令侧不变量是否由领域值对象/领域类型持有，而不是重新散落进 Service private method？
- `Page / Summary / Detail / View / Response` 是否只是读侧投影？如果是，为什么会出现在 `domain`？

### Model Placement

- `domain` 中的新类型是否真的拥有领域事实、值语义或不变量？
- Release API 的组合查询模型是否位于 `release.model`？
- Execution history / manual-run response 是否位于 `execution.model`？
- Read Model 是否被错误地当成新的 truth owner？
- 是否为了“看起来像 DDD”而制造第二套 DTO / domain wrapper？如果是，默认拒绝。

### Role

- 新类的名字是否表达真实职责？
- `Service` 是否真的是稳定应用入口，而不是因为“不知道放哪”？
- Parser / Resolver / Analyzer / Compiler / Worker / Outbox / Gateway 是否保持专业角色？
- 是否向 frozen `service` island 新增了文件？如果是，默认拒绝。

### Dependency

- Controller 是否只进入稳定入口和所属子系统 read model？
- Application role 是否绕过 Repository 直接碰 DAO？
- Domain 是否出现 Spring JDBC / MyBatis / Task Runtime / Lineage Service？
- Domain 是否反向依赖 Release / Execution read model？
- 新跨模块依赖是否有明确 corridor 和 truth owner？
- 是否出现新的 package cycle？

### Compatibility

纯结构重构必须确认没有顺手改变：

```text
REST route / request / response JSON shape
Flyway / table schema
Draft optimistic revision
Task Plugin validation
Revision checksum / append-reuse
Task Catalog projection
Editor manual-run snapshot
Data Service contract
SQL lineage outbox / replacement semantics
```

## PR 建议说明

涉及领域或架构修改时，PR 至少写：

```text
Domain Impact Analysis
- Truth owner:
- Invariant/lifecycle impact:
- Domain Gap: yes/no

Architecture Impact Analysis
- Target subsystem:
- Stable entry / role:
- Dependency direction changed: yes/no
- Legacy service island changed: yes/no

Domain Compliance Report
- Rule preserved/implemented:
- Tests:
- Known gaps:
```

## 自动护栏

不要为了让重构通过而删除或放宽 architecture tests。

当前 model-placement 护栏：

```text
DataDevelopmentDomainModelPlacementTest
```

它要求 Release / Execution 的 read/response projection 留在所属子系统，不能重新回到核心 `domain`。

如果规则确实变化，必须在同一个 PR 中同时更新相关 contract 文档和 architecture tests；需求事实未变化时，不为了形式主义修改 `REQUIREMENTS.md`。

推荐验证：

```bash
mvn -pl yak-ops-business/yak-ops-business-data-development -am test
```
