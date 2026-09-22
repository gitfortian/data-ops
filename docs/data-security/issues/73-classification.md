# Ticket 73：资产分级分类标签

**目标**：给数据对象（数据源/库/表/列）绑定等级+分类，形成分级基座。

**表**：`yak_dsec_classification`。
**行为**：
- `upsert(objectKey 规范, objectType, levelId, categoryId, source)`；`object_key` = `type:dsId:db.table.column`（缺段 `-`）。
- 分页（按等级/分类/关键词/状态过滤）；改级；确认候选（CANDIDATE→ACTIVE）；删除。
- 引用 `SecurityLevelService`/`DataCategoryService` 校验等级/分类存在。
**复用**：跳转查看数据源目录（datasource）。
**SPI 挂点**：`SecurityClassificationQueryApi.find(objectKey)` 供下游读等级/分类。
**审计**：`CLASSIFICATION_*`。
**错误码**：45030~45039。
**验收**：单测覆盖 upsert 幂等、objectKey 规范化、引用校验。
