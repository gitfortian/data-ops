# Data Security Review Guide

每张 ticket 评审时按以下清单核对:

1. **契约先行**:契约 diff 先于代码 diff;REQUIREMENTS.md 是否覆盖本票全部用户可见行为。
2. **分级地基唯一口径**:敏感度判断一律落到等级 `rank_no`;脱敏阈值/裁决默认/合规阈值共享同一 rank,出现旁路另立标准即违规。
3. **复用边界**:本模块只做"数据安全特有";数据源/元数据归 datasource、SECURITY 标准定义归 semantic(引用其 ID)、RBAC 归 framework security、变更留痕经 audit 门面、血缘归 lineage——重复实现即违规。
4. **依赖边界**:无 `io.yak.ops.business.modeling/metric.*` 等反向 import;datasource/semantic 仅经公共契约与基础设施消费,禁止 import 内部实现类型;下游只经 `api` 包三个 SPI 接口消费,不直读 `yak_dsec_*` 表。
5. **项目空间**:所有读写绑定 `CurrentProject`;全部业务表带 project_id;不建物理外键;自然键 `(project_id, code)` 唯一靠 DB 兜底。
6. **迁移纪律**:只增不改;表结构与 ARCHITECTURE.md 迁移所有权表一致;菜单走 yak-security Flyway(V2030 起,`outOfOrder`)。
7. **审批门禁**:访问策略仅 APPROVED 参与裁决;DENY 优先;发现扫描只落 CANDIDATE 须经确认才生效;内置脱敏算法只读。
8. **审计 fail-open**:全部写操作经 `SecurityAudit.tx` 落审计且异常不影响主流程;裁决/敏感访问写 `access_log`(只追加)。
9. **错误码**:45001~45099 段,`SecurityErrorCode` 单点登记(44xxx 归 mdm/metric,46xxx 归 alert,勿复用)。
10. **统计服务端聚合**:总览/审计热点禁止无界 list() 后内存统计;独立容错,"查不到"≠异常。
11. **测试**:纯函数(MaskingEngine/DiscoveryService.matches/objectKey)有直测;服务层校验/唯一性/引用阻断有 Mockito 单测。
12. **前端契约**:menuCode 稳定;不改 data-ops-ui 下任何 .md;只注册 6 个特有页,通用能力跳转。
