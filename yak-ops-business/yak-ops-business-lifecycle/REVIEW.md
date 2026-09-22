# Lifecycle Review Guide

每张 ticket 评审时按以下清单核对：

1. **契约先行**：契约 diff 先于代码 diff；REQUIREMENTS.md 覆盖本票全部用户可见行为。
2. **管策略不管执行**：模块内不得出现任何"平台自己删分区"的代码；清理永远由 Doris/Paimon 自治，平台只下发 TTL 属性。
3. **依赖边界**：无 modeling/semantic 内部实现 import（仅 `ModelTtlQueryApi`/`LayerConfigApi`）；SQL 只经 `TtlSqlGateway`→datasource SPI；PO/错误码/权限码在 yak-ops-common。
4. **生成/执行分离（D3）**：`writable=false` 语句在 service 与 gateway 双层拒绝下发。
5. **项目空间**：所有读写绑定注入的 `CurrentProject`（接口，禁止静态调用）；调度 alarm 按项目隔离。
6. **确认令牌**：下发入口必须 validate 令牌；令牌绑定项目+模型集+时间窗，无令牌=47009。
7. **迁移纪律**：Flyway 只增不改；菜单变更走 yak-security V2031+ 新文件。
8. **审计**：绑定/策略/下发全部 fail-open 落审计，事件 `LIFECYCLE_*`。
9. **纯函数测试**：generate/classify/deriveState/模板换算为 static 纯函数且各有单测（现 41 例）；新增规则先加表驱动用例。
10. **注入防护**：语句中的库表名一律经 `safeIdentifier`（剥反引号+包裹）。
11. **前端契约**：menuCode 稳定且过 `navigationMenuContract.test.ts`；不改 yak-ops-ui 下任何 .md；表单遵守"能选择就不填、能默认就不留空"。
