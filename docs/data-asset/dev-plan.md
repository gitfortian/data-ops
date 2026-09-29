# 数据资产（Asset Center）—— 开发计划

## 0. 硬性开发约束（不可打破）

> 与 [lifecycle dev-plan.md《硬性开发约束》](../data-lifecycle/dev-plan.md) 一致，本节为资产模块适用的硬性要求；与任何交付进度冲突时以约束为准。

1. **契约先行**：`data-ops-business-asset` 根目录维护契约文件集（README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW）；每个 ticket 开工第一步更新契约文件集，契约 diff 先于代码 diff。
2. **前端契约文件只读**：`data-ops-ui/` 下所有 `.md` 绝不修改；新菜单 menuCode 同步登记 `src/constants/securityMenuCodes.ts` 并过 `navigationMenuContract.test.ts`。
3. **项目全局规范**：`CODE_STYLE.md`、`FRONTEND_CODE_STYLE.md`、`docs/architecture/PROJECT_SCOPE.md`（project_id 只取服务端可信上下文、不建物理外键）、`docs/home-overview-contract.md`（所有聚合固定查询预算、失败不伪造 0）、菜单授权契约。
4. **交互原则**：`docs/INTERACTION_PRINCIPLES.md`——asset_key/目录编码/标签编码自动生成、登记信息全量从源域带出、上架缺口支持一键补默认、规则必须试跑后才可启用、批量危险操作必经预览确认。
5. **数据库迁移**：自持 `db/migration/yak-asset` V1 起编，历史表 `flyway_schema_history_asset`；菜单注册进 yak-security 链，取 **V2032**。
6. **错误码段**：48001~48099。权限码 `data-asset:read/create/update/delete`。
7. **管目录不管内容（D1）**：禁止把源域业务事实复制为第二真相；快照列必须可追溯到 provider 与时间；详情页一律实时 SPI。
8. **SPI 纪律**：源域仅**新增** `AssetProvider` 只读实现；不修改 modeling/metric/dataset 等任何既有接口签名；asset 不得依赖源域内部包（架构测试守护，参照 `QualityLayeringConventionTest`）。
9. **无界禁止**：对账分批 ≤500 游标推进；概览 ≤8 次查询；列表一律分页；浏览流水写入限流去重。

## 1. 里程碑

| 里程碑 | 内容 | Ticket |
|---|---|---|
| M1 底座 | 模块可启动、菜单可见、台账模型就位 | 90~91 |
| M2 编目核心 | 目录/标签/规则 + 对账引擎 + 状态机 | 92~95 |
| M3 发现与评分 | 搜索、360° 详情聚合、健康度 | 96~97 |
| M4 界面 | 前端四页 + 详情 + 上架向导 | 98~99 |
| M5 驾驶舱 | 概览聚合 + 快照定时 + 流水清理 | 100 |

## 2. 状态总表

| 编号 | Ticket | 阶段 | 状态 |
|---|---|---|---|
| 90 | 资产模块骨架 + Flyway + 菜单权限（V2032）+ 契约文件集 | P1 | done |
| 91 | 台账模型 `yak_asset_item` + 手工登记 + 快照编辑 + 负责人 | P1 | done |
| 92 | 目录树 + 模板一键初始化 + 批量移目录 | P1 | done |
| 93 | 标签字典 + 打标/去标 | P1 | done |
| 94 | `AssetProvider` SPI 契约 + Registry + MODEL/METRIC 两个源实现 | P1 | done |
| 95 | 对账引擎（游标分批/NEW/CHANGED/GONE）+ 编目规则（含 dry-run）+ 手动触发 | P1 | done |
| 96 | 状态机（预检 token/上架/下架/批量）+ 审计 | P1 | done |
| 97 | 搜索列表 + 360° 详情聚合（分区容错）+ DATASET/DASHBOARD/TASK provider | P2 | done |
| 98 | 健康度评分纯函数 + 每日重算定时 + 明细 | P2 | done |
| 99 | 前端·资产目录 + 360° 详情 + 上架向导 | P1 | done（2026-09-20 浏览器全链路实测：登记→预检缺口→补齐重检→上架→下架；含后端补 `GET /assets/{id}/tags` 与详情 health 分区） |
| 100 | 前端·盘点工作台 + 目录与标签页 + 概览驾驶舱 + 浏览流水/快照 | P2 | done（2026-09-20 浏览器实测：概览 KPI/待办深链、盘点三 Tab、手工对账受理→状态回写、规则 建→48010 拦截→试跑→启用→重应用→停用、标签 CRUD、目录树 32 节点。`/changes` 已改返 `PagingData`，2026-09-21 后端重启后补验：接口 bizData+pagination 共 14 条，「变更确认」视图正常渲染；浏览流水/快照按 plan 归 P2 后续） |

## 3. 依赖图

```
90 ─▶ 91 ─▶ 92/93 ─────────────┐
        └─▶ 94 ─▶ 95(对账+规则) ─▶ 96(状态机) ─▶ 97(详情聚合) ─▶ 98(健康度) ─▶ 100(前端工作台/概览)
                     └───────────────────────────────▶ 99(前端目录/详情/向导) ◀── 96/97 API
```

## 4. 关键口径与假设

- `asset_key` **直接复用各源域血缘登记键生成器**（实测 lineage 键为小写前缀式：`table:{dsId}:{db}.{schema}.{tbl}`、`dataset:{id}`、`chart:analysis:{id}`；见 design §4.1）。ticket 94 第一步以**契约测试**锁定"provider 产出的 asset_key == 该源域血缘登记键"，不造第二套键、不做适配层。
- 忽略（IGNORED）是对账抑制态：仅 PENDING/OFFLINE 可忽略（错误码 48016）；对账只刷在场时间不复活；源指纹变化自动回 PENDING 并重新记 NEW（design §6.1）。
- MODEL provider 的 content_hash = 模型名+描述+列结构（列名/型/注串联）摘要；META_CHANGED 仅比较 descriptor 字段，不比 schema（schema 变化在详情页"字段"块实时可见即可）。
- SOURCE_GONE 判定要求连续两个对账周期缺失（窗口设置默认 7 天），防源域临时故障误判。
- 首版目录模板 = semantic 分层 5 项 + 业务域一级子节点；semantic 未配置分层时模板按钮置灰并引导。
- 无质量/血缘 SPI 时健康度对应项计 0 分并在 detail 标注"数据不可用"，不阻塞交付。

## 5. 跟踪约定

状态：`backlog → ready-for-agent → in-progress → in-review → done`；完成后在票内（`issues/` 拆票后）追加"验证记录"。
