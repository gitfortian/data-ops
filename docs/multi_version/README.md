# 统一多版本 · Issue 总索引

> 依据：[../multi_version_unification.md](../multi_version_unification.md)（设计基线，契约条款 C1-C5 与裁决点 Q1-Q5 均引用该文档）
> 格式参照：`docs/agent/agent-observability-optimization-issues.md` 工单风格（现状问题/改动点/验收）。
> 日期：2026-09-21

## 工单清单

| 编号 | 标题 | 优先级 | 依赖 | 文件 |
|---|---|---|---|---|
| S1 | 共享状态枚举 PublishState 落地 | P0 | Q6 | [w0-shared-support.md](./w0-shared-support.md) |
| S2 | 版本工具收编（Digest + nextVersionNo 模板） | P0 | - | w0-shared-support.md |
| S3 | AuditTransactions 8 份复制收敛 + diff 槽位 | P0 | - | w0-shared-support.md |
| S4 | 前端 VersionHistoryPanel + JsonDiffView | P0 | - | w0-shared-support.md |
| S5 | 新对象版本化脚手架 checklist | P1 | S1-S4 | w0-shared-support.md |
| W1-1 | lifecycle TTL 策略版本化 | **P0** | S1-S4 | [w1-p0-correctness.md](./w1-p0-correctness.md) |
| W1-2 | sync-offline 作业草稿/修订双表 | **P0** | S1-S3 | w1-p0-correctness.md |
| W1-3 | modeling 消费方读已发布快照 + 回滚两步 + 发布前强制保存 | **P0** | S1 | w1-p0-correctness.md |
| W1-4 | workflow 枚举收敛 + 回滚端点 + 版本抽屉可操作 | **P0** | S1-S4 | w1-p0-correctness.md |
| W1-5 | mdm 配置侧草稿/发布 + 审计 diff | P0 | S1-S3 | w1-p0-correctness.md |
| W2-1 | metric 快照改发布触发 + 快照补全 + 禁物理删版 | P1 | S1-S2、**Q5** | [w2-semantics-fix.md](./w2-semantics-fix.md) |
| W2-2 | semantic 标准快照语义纠偏 + publish/rollback | P1 | S1-S4、**Q4** | w2-semantics-fix.md |
| W2-3 | dataset 回切端点 + 上下线绑版本 | P1 | S1-S4、**Q3** | w2-semantics-fix.md |
| W2-4 | realtime sync 版本列表 + 按版回切 | P1 | S1-S4 | w2-semantics-fix.md |
| W2-5 | quality revision 浮出为用户可见版本 | P1 | S1-S4 | w2-semantics-fix.md |
| W2-6 | data-service 节点补前端回滚入口 | P1 | S4 | w2-semantics-fix.md |
| W2-7 | mdm 实体配置版本化（自 W1-5 拆出，前置=活配置直读点盘点） | P1 | W1-5 | w2-semantics-fix.md |
| W3-1 | 四处既有版本 UI 替换为 VersionHistoryPanel + diff 铺开 | P2 | W1/W2 全部、S4 | [w3-convergence.md](./w3-convergence.md) |
| W3-2 | 端点别名收敛（restore/activate → rollback） | P2 | **Q1** | w3-convergence.md |
| W3-3 | 版本 PO 归属裁决落地 | P2 | **Q7** | w3-convergence.md |
| W3-4 | C5 审计 diff 全模块扫尾 + C 域判定归档 | P2 | S3 | w3-convergence.md |

## 批次依赖

```
S1/S2/S3（一个 PR，纯新增+复制收敛） ──┬─→ W1-1..W1-5（互不依赖，按模块分 PR 并行）
S4（前端组件，可与 W1 并行）          ─┘
W1 合入 → W2（每对象独立 PR） → W3（收尾批量）
```

## 前置裁决点（阻塞项开工前须拍板）

| Q | 问题 | 阻塞工单 | 设计基线建议 |
|---|---|---|---|
| Q1 | dev-task `activate/{revisionNo}` 指针回切是否豁免追加式统一 | W3-2 | 豁免存量 |
| Q2 | dashboard 子表分行快照是否强行改 blob | W3-1 | 不强行，唯一豁免 |
| Q3 | dataset 任务源版本存指针 vs 内容拷贝 | W2-3 | 保持指针+契约声明 |
| Q4 | semantic 业务过程/域/分层是否入版本化 | W2-2 | 仅补 status 不建版本表 |
| Q5 | metric 存量"创建即 v1"流水如何迁移 | W2-1 | 末条转 v1 发布版，其余只读保留 |
| Q6（新增） | `PublishState` 落点：复活 `common/enums/workflow/DefinitionState` 迁包，还是 common 新建后 DefinitionState 指向它 | S1 | 新建 `io.yak.ops.common.enums.PublishState`，DefinitionState 删除（零引用无兼容成本） |
| Q7（新增） | 版本表 PO 归属：进 common `bean/po` 还是模块内 `dao/model` | W3-3 | 新对象一律模块内，存量不迁移 |

## 全工单统一验收底线

- 后端：offline maven 编译 + 涉及模块测试绿（注意 target 里 stale test-classes 陷阱，先 clean）；Flyway 只加新 V 号，禁改已应用迁移。
- 前端：tsc 通过（199 行预算内）；**真实页面实测**：编辑草稿→线上读旧版→发布→版本列表出现新版→回滚→内容等价历史版且有二次确认。
- 审计：publish/offline/rollback 三类操作在审计页能看到带 before/after 的 diff，不再出现 `Map.of()`。
