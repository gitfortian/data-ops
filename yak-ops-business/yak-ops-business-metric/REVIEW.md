# Metric Review Guide

每张 ticket 评审时按以下清单核对：

1. **契约先行**：契约 diff 先于代码 diff；REQUIREMENTS.md 是否覆盖了本票全部用户可见行为。
2. **依赖边界**：无 `io.yak.ops.business.modeling.*` / `io.yak.ops.business.semantic.*` 内部实现 import；datasource 仅基础设施；PO/错误码/权限码在 yak-ops-common。
3. **项目空间**：所有读写绑定 `CurrentProject`；无 project_id 的表仅限平台预置模板。
4. **迁移纪律**：只增不改；表结构与 ARCHITECTURE.md 迁移所有权表一致。
5. **审计**：全部写操作落审计且 fail-open；审计事件命名 `METRIC_*`。
6. **SPI 纪律**：对外只暴露 `api` 包；内部实现类型（dao/dao.model/repository.impl）不出现在 controller/api 签名中。
7. **错误码**：44001+ 段，`MetricErrorCode` 单点登记。
8. **测试**：服务层规则（校验/不变量/引用阻断）有单测；适配器有项目绑定测试。
9. **前端契约**：menuCode 稳定且过 `navigationMenuContract.test.ts`；不改 yak-ops-ui 下任何 .md。
10. **血缘登记**：指标创建/更新时 `metric_dependency` 自动写入，lineage Asset/Relation 注册同步完成。
