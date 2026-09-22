# Dataset Review

> 本文件定义 Dataset PR 如何 Review。Reviewer / AI 是裁判，不在 Review 中自行补需求或创造新领域语义。

## Review 前必读

```text
REQUIREMENTS.md
DOMAIN.md
ARCHITECTURE.md
DEPENDENCIES.md
../../CODE_STYLE.md
PR diff / tests
```

## 1. Requirement Alignment

先检查是否改变：

- Dataset identity / ONLINE-OFFLINE lifecycle；
- immutable DatasetVersion publication；
- currentVersionId pointer；
- stable fieldId；
- exact-version query；
- DevelopmentNode stable Dataset binding；
- Analysis binding；
- derived lineage；
- HTTP / public facade compatibility。

未定义的新需求报告 `Requirement Gap`，不要混进重构。

## 2. Domain Compliance

重点检查：

- Dataset / Version / Field 是否混淆；
- 旧 DatasetVersion 是否被覆盖；
- exact TaskRevision / SQL snapshot 是否被 current source 替代；
- preview field 是否提前拥有持久化 fieldId；
- 指定 query version 是否漂移；
- DevelopmentNode 是否错误创建第二个 Dataset identity；
- Query Performance 是否成为业务状态；
- Lineage 是否反向成为 Dataset truth。

违反现有规则：`Domain Violation`。模型表达不了真实需求：`Domain Gap`。

## 3. Architecture Alignment

检查：

- 只有 `DatasetService / DatasetQueryService / DevelopmentDatasetFacade` 使用稳定 `@Service`；
- internal role 是否保持 Reader/Manager/Publisher/Coordinator/Adapter 等明确职责；
- 是否重新创建 `service/common/helper/utils/base`；
- Facade 是否直接依赖 Repository/DAO/外部实现；
- business role 是否绕过 Gateway；
- Repository/DAO 是否保持 persistence boundary；
- Lineage 是否重新反向依赖 Definition；
- Config 是否开始手工装配业务角色形成 cycle。

## 4. Dependency Alignment

任何新增内部 import 都检查 `DEPENDENCIES.md` 与 `DatasetDependencyBoundaryTest`。

重点 corridor：

```text
definition -> DatasetLineageRefreshPublisher
publication -> DatasetReader / Schema / TaskCatalogGateway / LineageRefreshPublisher
query -> Observability / Repository / TaskCatalogGateway
lineage -> Dataset-owned Lineage/TaskCatalog Gateways + Repository
development -> declared Dataset roles only
```

外部 TaskCatalog/Datasource/Lineage/Core SQL Runtime 只能从声明的 Adapter/Config 文件进入。

## 5. Correctness / Safety

高风险场景：

- 发布时 exact revision 变化；
- release 重试导致重复版本；
- append version 后 current pointer 错配；
- stable fieldId 漂移；
- read-only SQL 校验失效；
- query version drift；
- source adapter 选错；
- Dataset OFFLINE 仍可查询；
- DevelopmentNode 重复保存创建新 Dataset；
- lineage parse failure 删除结构资产；
- AFTER_COMMIT lineage failure 反噬 Dataset transaction；
- optional analyzer/catalog 缺失导致模块启动失败。

## 6. Compatibility

检查是否破坏：

- `/api/v1/datasets/**`；
- DTO / VO JSON；
- `DatasetService` API；
- `DatasetQueryService` API；
- `DevelopmentDatasetFacade` API / nested records；
- Analysis binding call；
- DB/Flyway/DAO/Repository；
- persisted sourceType/status；
- shared Lineage contract；
- Data Development / Dashboard / Chart 调用。

架构治理默认不做 breaking change。

## 7. Tests / Guardrails

P0/P1 问题都回答：哪个测试应该挡住？

重点：

```text
DatasetArchitectureTest
DatasetDependencyBoundaryTest
DatasetCodeStyleConventionTest
DatasetRoleConventionTest
DatasetPublisherTest
DatasetSchemaDiscoveryTest
DatasetQueryCoordinatorTest
QueryRevisionDatasetSourceAdapterTest
DevelopmentDatasetManagerTest
DatasetBindingPolicyTest
DatasetLineageSynchronizerTest
DatasetLineageRefreshListenerTest
```

架构 corridor 变化时，代码、`DEPENDENCIES.md` 和 executable guard 必须同 PR 更新。

## 严重级别

```text
P0 Blocker
- Dataset SQL 产生破坏性写入
- immutable version 被错误覆盖造成不可恢复数据问题
- 明确敏感数据泄漏

P1 Must Fix
- Requirement / Domain violation
- exact source/version drift
- duplicate/wrong version pointer
- field identity / query / lineage transaction correctness
- compatibility break
- package cycle / external boundary 绕过造成真实架构风险

P2 Suggestion
- 不影响正确性的可维护性、性能、可观测性建议
```

## 固定输出

```text
# Review Result
Conclusion: PASS | CHANGES_REQUIRED

## P0 Blocker
无 / 问题列表

## P1 Must Fix
无 / 问题列表

## P2 Suggestion
无 / 建议列表

## Requirement Gap
无 / 说明

## Domain Gap
无 / 说明

## Missing Tests
无 / 说明
```

有 P0/P1 -> `CHANGES_REQUIRED`；只有 P2 可以 `PASS`。