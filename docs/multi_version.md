# 重要业务对象多版本能力 · 全项目 Review

> 日期：2026-09-19 范围：`data-ops-business/*` 全部可编辑业务对象 + `data-ops-ui` 对应页面
> ⚠️ 2026-09-21 更新：本文 P0/P1 条目已按最新代码复核，立项落地版见 [multi_version_unification.md](./multi_version_unification.md)（本文两处表述修正亦记录于该文档 §2.2）。
> 结论组织按固定优先级：**P0 流程通畅（断点/假闭环）→ P1 业务闭环缺口 → P2 交互 → P3 UI/范式**。证据均为静态代码核查（已抽样复核关键条目），格式 `路径:行`。

## 一、评审标准

一个"重要业务过程对象"要支撑生产落地，需具备：

| 维度 | 要求 |
|---|---|
| 状态机 | 草稿(DRAFT) / 已发布(PUBLISHED) / 下线(OFFLINE) 显式状态，编辑草稿不影响线上 |
| 版本模型 | 版本号 + 不可变历史版本表（**全量快照**，非指针） |
| 发布/回滚 | publish / offline / rollback(activate 历史版) 端点；回滚=追加新版本(append-only) 而非抹历史 |
| 前端 | 版本历史列表、版本查看/diff、回滚按钮、发布前影响面提示 |

## 二、能力矩阵（21 个核心对象）

| 对象 | 模块 | 状态机 | 版本表 | 发布 | 回滚 | 版本 UI | 结论 |
|---|---|---|---|---|---|---|---|
| 大屏 | digital-screen | DRAFT/PUBLISHED | ✅全量快照 | ✅ | ✅ append-only | ✅ | **完整（范式）** |
| 仪表盘 | dashboard | 双指针草稿/已发布 | ✅按 version_id 分行 | ✅ | ✅ restore | ✅ | **完整** |
| 开发任务 | data-development | 草稿/修订双表 | ✅ revision 全量 JSON+checksum | ✅ publish | ✅ activate/{no} | ✅ | **完整** |
| 模型(表) | modeling | DRAFT/PUBLISHED | ✅ structure_json 全量 | ✅ | ⚠️ 有但"回滚即发布" | ✅ | **部分** |
| 工作流定义 | workflow | DRAFT/ONLINE/OFFLINE(字符串) | ✅ 全量引擎 JSON | ✅ online | ❌ 无端点 | 只读抽屉 | **部分** |
| 实时同步作业 | sync-realtime | DRAFT/PUBLISHED | ✅ definition_version | ✅ publish | ❌ 仅 apply 最新版 | ❌ 无列表 | **部分** |
| 数据服务节点 | data-development | 草稿→Revision→Runtime | ✅ | ✅ | ❌ 不能切历史上线 | 列表只读 | **部分** |
| 数据集 | dataset | 仅 ONLINE/OFFLINE | ✅ 只追加 | ✅ publish | ❌ 无回切 | 下拉预览 | **部分** |
| Dataset 节点 | data-development | — | ✅(dataset 模块) | ✅ | ❌ | 版本列表 | **部分** |
| 数据标准 | semantic | ENABLED/DISABLED | ⚠️ "改前"快照 | ❌ | ❌ | 抽屉自陈不可回滚 | **部分** |
| 质量监控 | quality | 仅 enabled 布尔 | ⚠️ revision 存在但只服务执行 | 保存即生效 | ❌ | ❌ 0 处 | **部分偏无** |
| 主数据配置 | mdm | DRAFT/ACTIVE/DISABLED | ❌ 就地自增 version | PUT 即改 | ❌ | 仅数字 | **部分偏无** |
| 主数据审批 | mdm | PENDING/APPROVED/… | 变更快照 JSON | submit/approve | ❌ | ❌ **前端整体未接** | **假闭环** |
| 指标 | metric | 仅 ENABLED/DISABLED | ⚠️ 自动快照流水 | ❌ 无发布态 | ❌ | 列表不渲染快照 | **无（变更流水）** |
| 离线同步作业 | sync-offline | ONLINE/OFFLINE 字符串 | ❌ definitionJson 原地覆盖 | ✅ 开关 | ❌ | 仅上下线 | **无** |
| TTL 策略 | lifecycle | ENABLED/DISABLED | ❌ | PUT 即生效 | ❌ | ❌ 0 处 | **无（最高风险）** |
| 分析物 | analysis | 无 | ❌ 就地覆盖 | 无 | 无 | 无 | **无** |
| 资产 | asset | 状态齐(镜像台账) | ❌ 变更记录表空转 | ❌ 无端点 | ❌ | ❌ | **无** |
| 业务过程/域/分层/标准字段 | semantic | 多数无 | ❌ | ❌ | ❌ | ❌ | **无** |
| 资源文件 | resource | 无（version=围栏） | ❌ 自陈非历史存储 | 覆盖 | ❌ | 仅 v{n} | **无（可接受）** |
| 数据源连接 | datasource | 无 | ❌ | PUT 即改 | ❌ | ❌ | **无（判定不建版本，需补 diff 审计）** |

**总评**：全项目仅 3 个对象（开发任务、大屏、仪表盘）具备"草稿→发布→历史→回滚"真闭环；modeling/workflow/dataset 有版本骨架但回滚或缺义或不可用；**lifecycle 与离线同步是"编辑即覆盖线上"的最高风险点**。全库无版本 diff 能力。

## 三、P0 · 流程断点（影响生产正确性，需先修）

1. **TTL 策略编辑直接生效、不可逆**（lifecycle）
   - `TtlPolicyService.java:196` `updateById` 就地覆盖，审计 before/after 为空 `Map.of()`（:200，已核实）；无版本表、无 publish/rollback（`TtlPolicyController.java:70,90`）。
   - 改错 `destroy_days` 一旦被下发即清生产分区且无法一键回退策略。DRIFT 检测（`ModelTtlBindingService.java:199-214`）只兜"存储端"，兜不住"策略被写坏"。
   - 前端 `PolicyEditDrawer.tsx:154` 直提，无"影响 N 个绑定模型、需重新下发"提示。

2. **离线同步作业保存即覆盖线上定义**（sync-offline）
   - `OfflineJobDefinitionPO.java:25` 仅 `releaseState` 字符串；`version` 字段是乐观锁；`definitionJson/jobSpecJson` 原地覆盖，无任何 revision 表 → 同步逻辑改错无法回滚。

3. **modeling 草稿不隔离 + 回滚丢弃草稿**
   - 保存结构就地覆盖活表并置回 DRAFT（`ModelStructureService.java:126`），`published_version_id` 仍指旧版**但消费方（血缘/指标/派生）直读活表** → 未发布草稿实际生效，破坏"发布态=生产态"。
   - 回滚实现（`ModelVersionService.java:104-128`，已核实）：恢复结构后**立即置 PUBLISHED**，当前草稿被覆盖且无二次确认；快照只含结构，不含模型名/描述/分层等元数据。
   - 前端发布按钮（`detail.tsx:1358`）未先保存结构 → 静默漏发旧内容。

4. **工作流有版本无回滚 + 枚举漂移**
   - `DefinitionState.java:4-8`（DRAFT/PUBLISHED/OFFLINE）代码零引用，实际用字符串 `DRAFT/ONLINE/OFFLINE`（`WorkflowDefinitionManager.java:204,343,368`）。
   - `WorkflowVersionPO` 快照完备，但**无任何按 versionNo 回切端点**（仅 online/offline，`WorkflowDefinitionController.java:117,126,146`）；版本抽屉只读（`WorkflowToolbar.tsx:213-279`）。

5. **MDM 审批链路前端整体未接（假闭环）**
   - 后端 submit/approve/reject/withdraw 齐（`MdmApprovalController.java:45-83`），菜单 V2028 已注册，但 `U/services/mdm/api.ts` 无任何 approval/version 调用、导航无审批页 → 用户走不完流程。
   - 实体/属性/清洗规则配置侧 PUT 直接生效（`MdmEntityController.java:70`），零版本。

## 四、P1 · 业务闭环缺口

- **metric**：现版本表是"创建/更新/状态变更自动快照"的**变更流水**（`MetricCatalogService.java:138,210,247`），非发布触发；无 DRAFT/PUBLISHED；`version` 字段乐观锁与版本号语义混用；快照漏 `compositions`（`toJsonSnapshot:570-592`）；前端版本 Tab 不渲染 snapshot、`getVersion` 端点无人调用。
- **semantic 标准**：`payload_json` 存的是**修改前**状态，语义倒置（当前版本永无快照）；无回滚端点；`StandardVersionsDrawer.tsx:25` 自陈"仅查看"。业务过程/业务域/分层/标准字段完全无状态无版本。
- **dataset**：版本只追加（`DatasetVersionWriter.java:71`）但缺"把 `current_version_id` 指回旧版"的回切端点与 UI；上下线与版本未绑定。data-service/Dataset 节点同理。
- **quality**：`yak_quality_monitor_revision` 全量快照已存在（V2 迁移）但仅服务 workflow 执行固定，无查询端点、无 UI、不可回滚；规则本体就地覆盖。
- **realtime sync**：无 `GET /versions`、无按版回切，前端只有发布/应用最新版。
- **asset**：`yak_asset_change_record` 有表无写入方（mapper 零调用），变更记录空转；无上下架端点、前端无调用。
- **task-catalog**：定位是 current-revision 指针登记表，本身不需要回滚端点（由发布中心 activate 代理）——现状合理，文档标注即可。

## 五、P2 · 交互与前端缺口

1. 版本 UI 覆盖率低：仅开发任务/工作流(只读)/仪表盘/大屏/指标(不渲染) 5 处；modeling 有面板但缺发布前"先保存"约束与二次确认。
2. **全库无版本 diff**：所有版本抽屉要么裸 `JSON.stringify`（`StandardVersionsDrawer.tsx:41`）要么不展示内容。
3. 破坏性操作提示缺失：lifecycle 编辑无影响面提示；modeling 回滚无"将丢弃当前草稿"确认；符合"能选择就不填、明示后果"的交互底线还差影响计数 + 确认两步。
4. 一键初始化/批量类入口（lifecycle 已有）可作为其他模块批量发布/批量回滚的交互范式。

## 六、P3 · 统一范式建议（供后续实施立项）

1. **契约统一**：状态枚举一律 `DRAFT/PUBLISHED(或 ONLINE)/OFFLINE` 落到共享枚举，禁字符串字面量（先收敛 workflow、sync-offline）。
2. **版本表规范**：`<biz>_version(biz_id, version_no, payload_json 全量快照, checksum, created_by/at)`，只追加；发布态指针 `published_version_id` 存主表；当前草稿与已发布快照**物理分离**（modeling 反例）。
3. **端点命名统一**：`POST /{id}/publish`、`POST /{id}/offline`、`GET /{id}/versions`、`GET /{id}/versions/{no}`、`POST /{id}/versions/{no}/activate`（activate=以历史版内容追加新版本并置发布态，即 digital-screen 范式，`DigitalScreenPublisher.java:51-70`）。
4. **审计**：写 `*_UPDATE` 类审计必须带脱敏 before/after diff（lifecycle/datasource/asset 现状为 `Map.of()` 或缺失）。
5. **前端**：沉淀公共 `VersionHistoryPanel` 组件（列表+查看+diff+回滚确认），modeling `ModelVersionPanel.tsx` 与 dashboard 抽屉合并抽象。
6. **明确不做版本化的对象**：datasource 连接参数（回滚旧参数有安全风险，改补 diff 审计+改密强制重测连通）、资源文件、系统环境变量——在各自 docs 标注判定依据，避免反复评审。

## 七、建议落地顺序

| 优先级 | 事项 | 对象 |
|---|---|---|
| P0 | 策略/定义版本化 + 编辑影响面提示 | lifecycle TTL 策略（以 modeling V16 + Controller 为模板）、sync-offline 作业 |
| P0 | 消费方读已发布快照而非草稿 | modeling（血缘/指标依赖隔离）、回滚二次确认、发布前强制保存 |
| P0 | 补回滚端点 | workflow `versions/{no}/activate`、mdm 审批前端接通 |
| P1 | 回切能力 | dataset / data-service 节点 / realtime sync |
| P1 | 版本语义纠偏 | metric（改发布触发快照）、semantic 标准（改当前版快照） |
| P2 | diff 视图 + 公共版本面板 | 全模块 |
