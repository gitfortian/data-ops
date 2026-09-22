# Ticket 113：前置改造——把 `LineageRegistrationApi` 抽到 lineage 的 `api` 包

> ⚠️ **改的是 lineage 的包结构，不改它的行为。** 可与 128/129 并行，但必须排在 133 之后开工。

**对应需求：** 元数据中心（依赖纪律） | **阶段：** P0 | **模块：** yak-ops-business-lineage

**What to build：** 元数据 GONE 时要"从血缘图撤销节点"（plan §3.4 末），必须走 lineage 的 api；而 `LineageRegistrationService` 现在在 `…/lineage/registration/` 包里**不在 `api`**。本票只做一次抽取重命名，让 §0.3"跨模块只走 `api`"对元数据成立。

**Blocked by：** 110

**验收清单**
- [ ] 新建接口 `io.yak.ops.business.lineage.api.LineageRegistrationApi`：`registerAsset(RegisterAssetCommand)`（`:29`）、`registerRelation`（`:34`）、`registerAssetsBatch`（`:39`）、`registerRelationsBatch`（`:45`）四个方法签名**原样搬运**，两个 record（`:50`/`:96`）一并进 `api`
- [ ] 实现留在原处（模板：`modeling/api/ModelTtlQueryApi.java` + `modeling/catalog/ModelTtlQueryApiImpl.java`，本仓库已在用的写法）
- [ ] 既有引用**全量迁移**、一次编译收敛（风险只在改包路径影响 import）
- [ ] lineage 既有测试全绿；分析/看板/数据集三个 `*LineageSynchronizer` 编译通过
- [ ] grep 断言：metadata 模块内**不存在** `import io.yak.ops.business.lineage.\(registration\|domain\|dao\)` 之类的内部包引用

**为什么必须做**：不做的话元数据只有两条坏路——直插 `yak_metadata_asset`（plan §0.4"写别人的表=死罪"）或复制一份撤销逻辑（第二份真相）。
