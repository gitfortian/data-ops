# A8.1c — Common / Security 兼容合同冻结

> 范围：在 A8.1b [#420](https://github.com/gitfortian/data-ops/pull/420) 之后，为 A8.2 Security Platform 的真正迁移建立可重复的源码边界和运行时合同证据。总体计划：[A8 #412](https://github.com/gitfortian/data-ops/issues/412)。
>
> **本 PR 不宣称完成 Common 迁移**；当前 Common 模块仍依赖旧 `data-common`，Framework Security 也仍使用旧 Common。目标是降低真正迁移时的接口破坏风险，不为“删目录”破坏认证/业务错误结果。

## 证据：Data-Ops Common 的真实 Framework 引用

逐个检查 `data-ops-common/src/main/java` 中现有 163 个生产 Java 文件：

- 12 个业务错误码枚举 `Alert / Approval / Asset / DataSource / Lifecycle / Mdm / Metadata / Metric / Modeling / Resource / Security / Semantic ErrorCode` 引入 `io.yak.framework.common.ErrorCode`。它们会被当前旧 `Result.fail(ErrorCode)`、`BusinessException` 等直接消费，必须保留接口可赋值性与错误码、错误消息。
- `YakScheduleGateway` 引入四个 `io.yak.framework.schedule.api` 类型：`ScheduleDefinition`、`ScheduleKey`、`ScheduleManager`、`ScheduleSnapshot`。这个能力属于将来的 **A8.3 Schedule**，不是 A8.1 抽象错误码的旁支。
- Framework Security 的 Maven 模块目前直接依赖旧 `data-common`，**并不能安全地反向依赖** `data-ops-common`。直接将旧 `ErrorCode` / `Result` 移入产品 Common、让 Framework Security 回头依赖该模块，会逆转依赖方向并可能引入第二份业务协议。

上面是当前源码快照的边界识别，不是 Maven effective dependencies、反射、运行时或外部 JAR 消费者的完整证明。A8.0 的不可变 baseline guard [#415](https://github.com/gitfortian/data-ops/pull/415) 禁止在新文件里回引旧 Framework FQCN；本轮不添加新的生产旧类引用。

## 本轮实现

1. `scripts/architecture/check-common-security-corridor.mjs`：全仓 Git 跟踪源码检查，产品 Common 只允许这 12 个历史 ErrorCode 枚举的旧接口导入和独立调度网关的四个既有 Schedule API 类型；**不允许新增其他 Common→Framework 生产依赖**。
2. 同一守卫明确禁止 Framework Security 的生产 Java、Maven POM 反向依赖 Data-Ops Common / Business / Boot。
3. `scripts/architecture/check-common-security-corridor.test.mjs`：7 组正反测试，包括新业务错误码引用、其他旧类型、Scheduler 扩权、Security Java/POM 逆向依赖以及实际仓库检查；依赖收敛可以减少旧引用，不要求永久保留旧枚举接口。
4. `FrameworkCommonCompatibilityTest.java`：在现有产品 Common 模块内，逐一验证 12 组业务错误码枚举与旧 ErrorCode/Result 的赋值及 Code/Message 语义；回归 BusinessException 结构化错误；以 Jackson 验证成功响应和分页 `bizData / pagination` 的 JSON 输出，以及空分页默认值。
5. Existing Architecture Checks 同时运行 JS 正反测试和仓库静态规则；真实 Maven reactor 检查会运行新增 Java 合同测试。

## 下一次实际删除旧 data-common 的硬门槛

- [ ] A8.2 将 Security Platform 的代码归属迁到产品维护的稳定层，**不新增**底层 Framework→产品业务逆向依赖；
- [ ] 确定唯一 ErrorCode/Result/Error 类型 Owner，所有消费者一致迁移、无双份二进制 API / Spring 响应结构；
- [ ] 迁移 JDK 类型签名、异常处理与 JSON 行为时，此处测试必须同时在新旧行为路径执行；在旧类型完全清退后将测试转换为 Data-Ops 自有最终合同，不能为了通过简单删除测试；
- [ ] 另由 A8.3 处理 Schedule API 的真实引用；
- [ ] Maven 依赖树、MySQL/PG Security、Project/RBAC、认证失效、接口码值、历史数据升级/回滚以及 A0～A8 正序组合验收有精确运行证据；
- [ ] 所有架构 Draft 在得到明确授权之前禁止合并。

**明确非目标：** 不移动 `data-common` 源码，不修改 Java 包名、枚举值、Session/Token、Spring Bean、存储和工作流执行机制；不伪称已实现零 Framework 依赖。
