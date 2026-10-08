# P1 · 精确历史来源版本归一化 Usage 读取（2026-10-08）

**基线**：`data-ops/main d09d0b6144920202606927115d908efa051e498d`，PR #408 已合并。范围：PD-002 ACCEPTED / F-004 APPROVED 的已知 Consumer Usage Evidence 与精确 SourceVersion 核对；**PD-008 / PD-007 均仍 PROPOSED**，不引入正式 Consumer Response、Change Proposal、通知、Approval 或发布 Gate。

## 读码确认的缺口

`ConsumerImpactService.view(ProductKey,int)` 只调用 `UsageEvidenceRepository.list(projectId,productKey,null,limit<=200)`，`MybatisUsageEvidenceRepository` 在关系表中先按产品查询，再按 `observedAt DESC,id DESC` 截取最近 200 条。PR #408 纠正了**来源最近成功调用读取**被失败调用挤占的问题，但当来源产品最近 200 次成功归一化 Usage 全属于新版本时，旧不可变 SourceVersion 的 Usage 即使早已持久化，也不会出现在产品概览里。PR #407 新增的返回同一精确版本深链仍只能提示“版本已超出窗口”，没有实际核对能力。

具体读到的代码：`ConsumerImpactService`、`ConsumerImpactController`、`UsageEvidenceRepository`、`MybatisUsageEvidenceRepository`、`VersionChangeImpactReview.tsx`、`version-impact-review.ts`、`services/consumption/api.ts`；确认仍使用 F-004 的 Project-scoped READ 路径和唯一 Consumption-owned normalized Usage。

## 方案与使用闭环

1. `GET /api/v1/consumption/impact?productKey=<type:id>&usageLimit=200&sourceVersionIdentity=<exactId>` 扩展现有只读合同；**不传参数**的旧接口仍走原 200 条产品概览，确保兼容。
2. `UsageEvidenceRepository.listByVersion(projectId,productKey,sourceVersionIdentity,limit)` 由 MyBatis-Plus LambdaQueryWrapper 在**同一次 SQL**中参数化精确过滤 `projectId`、`productKey`、`sourceVersionIdentity`，再 `observedAt DESC,id DESC` 和 `LIMIT<=200`。不用拼接原始 version ID 到 SQL，不增加 Mapper XML，不读取其他 Project/产品。
3. ConsumerImpactService 已有的 Subscription / Usage 分开聚合、正常来源再同步、来源 GAP / Provider UNAVAILABLE 语义保持；**精确版本读取是已有归一化表的历史证据，不是重新扫描全量来源审计**。扩充 `coverageNote`，明确目标版本按 Project/Product/Revision 查询、归一化结果仍有 200 条限制，以及来源补偿同步仍只覆盖最近有限来源窗口。
4. VersionChangeImpactReview 为明确的 `reviewVersion` ID 启动独立精确查询；以独立响应展示此版 ConsumerRef、历史成功次数、证据深链及当前声明依赖。对仅在历史归一化数据中出现的版本，也允许保留真实版本身份并核对，而不偷偷退回 active。无 version 参数的旧页面保留概览语义，并增加“按该版本核对历史已归一化 Usage”入口。
5. 对同一 ProductKey/version 的异步切换、后端失败、无授权，页面在精确查询完成前不使用概览数据伪装精确版本结论，也不允许复制已过时的人工变更草稿。项目读取失败以来源错误表达，不能“零消费者”。任何 ID 不通过 JS Number，而是一直保留原始十进制字符串。

## 回归 / 真实性

- MyBatis-Plus 单测捕获生成 SQL 段和参数值，核对 Project/Product/immutable-version 三重 WHERE、倒序、LIMIT<=200、SQL 不直接插值 identity。
- ConsumerImpactService 测试：新版本挤占最新产品窗口，但旧 revision 仍由 `listByVersion` 读取到，保持历史 Consumer、Usage count、Evidence Ref；旧 `view(product,limit)` 接口兼容；非法版本字符串 fail-closed。
- UI：当 reviewVersion 指向旧、仍有持久化 normalized evidence 的 SourceRevision，从来源返回后可看到其独立 impact；无记录不得推断未使用，来源不可用不得继续复制。
- #336 真正部署 Golden E2E：Project A 中先形成版本 R1 的真实 Dataset QUERY/Data Service INVOKE Usage，后有 200+ 版本 R2 的**成功**记录；产品总览可以看不到 R1，但 `?reviewVersion=R1` 的 API/UI 应显示 R1 的归一化 Consumer 证据，且不串到 R2。Project B/受限 Actor 不得读到 A。记录真实 deployedSHA、actor/Project、ProductKey/SourceVersion/ConsumerRef、来源 API、网络响应、PASS/FAIL/BLOCKED。
- GitHub CI Product Guard/Consumption/Architecture 仅检验编译和合同，不等于部署验收。**#336 仍 PENDING**。

## 边界

本轮不改变来源审计的长期保留策略、不构建全历史扫描、不把未找到归一化记录认定为没有历史消费、更不生成正式 Consumer 确认；仅使 F-004 已批准的历史精确版本 Consumption Usage 可核查。
