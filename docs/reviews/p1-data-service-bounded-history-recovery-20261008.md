# P1 — Data Service 精确 Revision 成功调用审计：有界历史恢复

日期：2026-10-08。业务授权：ACCEPTED PD-002 / APPROVED F-004，#180 P1 E3，#336 Golden E2E。
基线：#413 历史 Revision 的最近成功审计补偿，#422 Dataset 同版本有界恢复。

## 用户问题及 Truth Owner

当同一个 Data Service 的旧 `sourceRevisionId` 拥有超过 200 条**仍被保留**的成功 Invocation 审计时，现有精确版本 Impact GET 仅补偿该 Revision 最近的成功来源窗口，旧调用的 normalized Usage 缺失仍可能无法被恢复。

原始调用审计、Invocation ID、Consumer ID、成功结果由 Data Service 持有；Consumption 只拥有幂等 normalized Usage。新能力不重新创建 Data Service 调用，也不重建 Consumer 授权、发布、生命周期或 Usage 总量 Truth。

## 显式 POST 命令与 BIGINT 游标

```http
POST /api/v1/consumption/impact/data-service-revision-recovery?productKey=DATA_SERVICE:7&sourceVersionIdentity=9007199254740995&limit=200
```

首请求不传 `beforeInvocationId`。后续收到 `retryRequired=false` 且 `nextBeforeInvocationId` 非空时，传上一页返回的**字符串游标**：

```http
POST /api/v1/consumption/impact/data-service-revision-recovery?productKey=DATA_SERVICE:7&sourceVersionIdentity=9007199254740995&beforeInvocationId=9007199254740993&limit=200
```

`requestedBeforeInvocationId` 和 `nextBeforeInvocationId` 在 JSON 中都是字符串（或 null），避免 JavaScript `Number` 对 BIGINT Invocation ID 的精度损失。Controller/Service 验证 canonical 正整数十进制字符串，服务端转换为精确 Long 执行 SQL。

Result payload `DataServiceAuditRecoveryView` 返回版本、请求 limit、访问审计数、已归一化或已有幂等记录数、GAP/UNAVAILABLE 数、可继续的下一游标、retryRequired、retainedAuditExhausted。后者只说明源库中**仍保留**的 Success 审计已经被遍历，不代表审计清理之前的完整历史，也不代表 normalized Usage 的 200 行 Impact 视图能够展示全量。

## Source-owned SQL 与失败语义

- 通过 `CurrentProject.requireProjectId()` 取得受信 Project，不接受客户端传参代替；DAO 首先约束 `project_id`、`api_id`、精确 `source_revision_id` 和 `success=true`，可选 `id < beforeInvocationId`。
- 排序 `ORDER BY id DESC LIMIT <= 200`，不能改用时间戳/Offset 或全局扫描。旧 Revision 的第一页不会被新 Revision 成功调用挤占。同页重复恢复使用原有 `DATA_SERVICE_INVOCATION:<id>` 幂等键。
- 缺乏可归因 Consumer、Revision、Project 或稳定 InvocationId 的来源成功调用是 GAP；存储不可用是 UNAVAILABLE。GAP / UNAVAILABLE / IGNORED 均 `retryRequired=true`、`nextBeforeInvocationId=null`，应以原游标重试；异常直接失败，不能返回伪 EMPTY 或虚假完成。
- 复用现有 Console `@ProjectScope(PROJECT_REQUIRED)` + `@RequiresPermission(AssetPermissionCode.READ)`，和 Dataset 专属补偿保持一致。它是显式 **POST** 操作，不让浏览器 GET 预取触发持久化投影写入。
- 不改变 `GET /api/v1/consumption/impact` 既有有界 200 条显示/查询行为；不是全量消费计数新 API。无 Schema、新定时任务、后台轮询、权限状态机或额外日志真相。

## 验收范围与待办

源码单测保护 source SQL predicate/排他 BIGINT/id DESC、失联 fail closed、最大 200 行、200+ 次查询的下一页续接、错误游标、幂等成功、GAP/UNAVAILABLE/IGNORED 不前进、POST 和 JavaScript 无损字符串接口。

#336 真正部署 Golden E2E **PENDING**：在隔离 Project 构造旧 Revision 200+ 已保留成功 Invoke，其中早期成功调用缺 normalized Usage；对比 Recovery 前后 persisted normalized Usage、同一 Consumer/Revision/Invocation 归因、页重试、跨 Project、拒绝/失败调用不产生成功 Usage，记录部署 SHA / DB source rows / SQL 查询计划 / HTTP 响应和 RBAC 身份。Data Service 原始日志默认保留 30 天，已汇总/清理的逐条 Invocation 不可由 rollup 推造回 Usage。
