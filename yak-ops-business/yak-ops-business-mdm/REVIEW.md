# MDM Review Guide

每张 ticket 评审时按以下清单核对:

1. **契约先行**:契约 diff 先于代码 diff;REQUIREMENTS.md 是否覆盖了本票全部用户可见行为。
2. **复用边界(design.md 一/menu.md 七)**:MDM 只做"主数据特有";采集执行归 sync、质量引擎归 quality、API 网关/缓存归 data-service、标准归 semantic、血缘归 lineage、权限归 security、统计归 dataset——出现重复实现即为违规。
3. **依赖边界**:无 `io.yak.ops.business.modeling.*` import;被依赖模块仅经公共契约/SPI 与基础设施(infrastructure 包)消费,禁止 import 其内部实现类型。
4. **项目空间**:所有读写绑定 `CurrentProject`;全部业务表带 project_id;不建物理外键。
5. **迁移纪律**:只增不改;表结构与 ARCHITECTURE.md 迁移所有权表一致。
6. **审计**:全部写操作落审计且 fail-open;审计事件命名 `MDM_*`。
7. **错误码**:44001+ 段,`MdmErrorCode` 单点登记(43001-43018 已被资源模块占用,勿复用)。
8. **核心口径**:master_id 跨系统唯一、source_ids 记录各系统原始 ID、变更需审批、统计服务端聚合(禁止无界 list() 后内存统计)。
9. **测试**:服务层规则(校验/不变量/引用阻断)有单测;适配器有项目绑定测试。
10. **前端契约**:menuCode 稳定且过 `navigationMenuContract.test.ts`;不改 yak-ops-ui 下任何 .md;只注册 5 个特有菜单,通用能力跳转。
