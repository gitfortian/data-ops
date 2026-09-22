# 审计模块——Ticket 清单（全量动作审计方案）

> 状态：v1.0（2026-09-22）
> 来源：[docs/v1/modules/审计-现状与能力分析.md](../../v1/modules/审计-现状与能力分析.md)（盘点数据已刷新）+ 2026-09-22 全量审计方案
> 编号从 **01** 起，模块内有序

## 〇、方案一句话

现状是**纯手工埋点**（无注解、无切面、无触发器）：437 个写接口仅 129 个审计操作点，真实留痕率约 25%–35%，且 fail-open 全静默。方案翻转为「**HTTP 拦截兜底全记 + `@Auditable` 声明补语义 + CI 守卫防回归**」，存量 129 个埋点零改动、靠 `AuditContext` 合并防双记。

## 一、Ticket 总览

| 编号 | Ticket | 批次 | 优先级 | 阻塞于 | 状态 | 模块 |
|---|---|---|---|---|---|---|
| 01 | @Auditable 注解 + 操作类型推导工具 | M1 | P0 | — | **done（2026-09-22，66 测试绿）** | common + audit |
| 02 | HTTP 写接口审计兜底（拦截器 + 存量合并） | M1 | P0 | 01 | **代码完成，待真机复核**（合并守卫改用新增 AuditWebLedger，AuditContext 假设已证伪并修正） | boot + audit |
| 03 | 敏感脱敏 + 可选请求体留痕 | M1 | P1 | 02 | **done（2026-09-22，含 6 例脱敏契约）** | audit + boot(filter) |
| 04 | 登录审计（成功/失败/登出） | M2 | P1 | 01 | ready-for-agent | boot |
| 05 | 审计覆盖 CI 守卫（基线棘轮） | M2 | P1 | 02 | ready-for-agent | boot(test) + ci |
| 06 | 语义补齐第一批：modeling 余量 + semantic 缺口 | M2 | P2 | 02,03 | ready-for-agent | modeling + semantic |
| 07 | 语义补齐第二批：metadata + quality | M2 | P2 | 02,03 | ready-for-agent | metadata + quality |
| 08 | 语义补齐第三批：data-development + data-service + sync 接口 | M2 | P2 | 02,03 | ready-for-agent | 三模块 |
| 09 | 异步/调度链 AuditContext 传播工具化 | M2 | P2 | 02 | ready-for-agent | audit + 调度侧 |
| 10 | 一本账收尾：oplog 桥接 + 前端清理 + RUNNING 巡检 | M3 | P2 | 02 | ready-for-agent | boot + ui |

## 二、依赖图

```
01 ─▶ 02 ─▶ 03 ─▶ 06 / 07 / 08（语义补齐可按模块并行）
        02 ─▶ 05（守卫以兜底落地为基线）
        02 ─▶ 09 / 10
01 ─▶ 04（登录也走 @Auditable 常量，但不依赖 02）
```

## 三、明确不做（记录在案）

- **不改存量 129 个手工埋点**：兜底拦截器检测到请求线程内 `AuditContext` 已开账即跳过，双轨合并而非迁移（02）。
- **不做 GET 读审计**：读接口量级大、噪声高，访问留痕已由 `yak_dsec_access_log` 口径承担；如需按专项另立票。
- **不合并四套日志表**：`yak_audit_*`、`yak_security_oplog`、`yak_dsec_access_log`、各模块私有 log 表（call_log/query_log/merge_log）语义各異，只桥接 oplog 写入端（10），不动表结构。
- **AOP 切面方案否决**：Web 端点用 `HandlerInterceptor` 即可拿到 `HandlerMethod` 注解与 HTTP 状态，零 aspectj 依赖；仅登录 hook 允许用 `@Aspect`（外部 jar 无法改代码）。
- **请求体全量记录否决**：默认只记 method/URL/状态/耗时，`recordPayload=true` 白名单开、且必过脱敏（03）。

## 四、自曝缺口（方案三连问结论）

- **可落地性**：02 的兜底粒度是「一次 HTTP 写 = 一条 operation」，对 Service 内多步操作（如批量下发逐条留痕）仍以手工埋点为准，兜底只保证"至少有痕"。
- **零侵入**：M1 对业务模块 pom 零改动（注解放 common，job 模块若缺直依赖补一行）；代价是未标注接口的 `operation_name` 是路径推导值（如 `POST /api/v1/modeling/models` → `MODELING_MODELS_CREATE`），可读性靠 06–08 渐进偿还。
- **能否更简单**：极简版=只做 02+05（无注解、全推导），当天即可 100% 留痕但审计中心名称难看；本清单按完整版拆票，如要压缩先砍 03 与 06–08。
