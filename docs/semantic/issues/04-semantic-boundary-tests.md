# 04: 语义依赖边界架构测试（DependencyBoundaryTest）

**对应需求:** 语义中心盘点 §6 缺失能力（P2）| 阶段: P2

**What to build:** 05 文档 S2 契约标 ✅"只读 SPI+编译期边界测试",但仓库内 agent/analysis/dashboard 等均有 `DependencyBoundaryTest`,semantic 及其五个消费方(modeling/metric/asset/lifecycle/mdm)**均无对应架构测试**——消费方一旦 import semantic 内部 dao/repository 无人拦截,文档标注与代码现实有出入。补齐架构测试,让 S2 契约在编译/测试期真实成立。

**模块归属:** **跨模块**——semantic + modeling/metric/mdm/asset/lifecycle 各补一条测试

**Blocked by:** 08 SPI 泄漏对象收敛(消费方现在合法 import api 包外的 `BusinessDomain/Standard/StandardField/WarehouseLayer`,不先收敛测试一加即红;过渡期可白名单)

**Status:** backlog(待排期)。注意排期张力:本单 P2 却阻塞于 P3 的 08,建议排期时把 08 提到本单之前。

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md);若最终决定不补测试,必须**先修订 05 文档 S2 表述**,不允许文档继续虚标。

- [ ] 各消费方模块补 `DependencyBoundaryTest`:禁止 import `io.yak.ops.business.semantic` api 包之外的深层包(dao/repository/controller 等)
- [ ] 过渡期例外用显式 allowlist 并逐条注明原因、引用 08
- [ ] 测试接入常规构建,新增违规会被拦截
- [ ] 05 文档 S2 的 ✅ 与代码现实一致(补测试或改口,二选一)
- [ ] 契约测试通过
