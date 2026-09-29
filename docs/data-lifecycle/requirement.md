# 数据生命周期（TTL）功能 —— 完整需求文档

> 模块：`data-ops-business-lifecycle`（新建）
> 版本：v1.1（2026-09-19 评审优化：D1~D8）
> 状态：需求基线
> 依赖：`semantic`（分层配置：库名/数据源/旧 lifecycle_days）、`modeling`（模型、表名、分区、方言）、`datasource`（SQL 执行通道）、`audit`（操作审计）、`data-schedule`（失败重试 / 存储快照定时）
> 设计来源：产品规划《数据生命周期管理（Doris + Paimon 路线）》，经数据治理评审后按本文决策修订。

---

# 第一部分：需求文档

## 一、背景

### 1.1 现状

- 语义中心分层配置已有单值字段 `lifecycle_days`（空=永久），但只"记"不"管"：无粒度、无热冷分段、不下发。
- 建模已持久化物理表信息（表名、分区列、分区表达式、方言），知道"哪张表、怎么分区"，但分区保留策略没有出口。
- 平台已有统一 SQL 执行 SPI（`DataSourceExecutionProvider`）与 Doris 数据源插件（MySQL FE 协议，具备 `SQL_EXECUTION` 能力），`ALTER TABLE … SET("dynamic_partition.*")` 今天就能执行。

### 1.2 问题

数仓分区数据无限增长：无统一保留策略、无"策略→物理表"的下发通道、无生效状态可视，治理人员只能口头约定"ODS 留 90 天"，实际无人核对。

### 1.3 本功能解决什么

**平台管策略，存储管执行。** 平台负责：定义 TTL 策略（热/冷/销毁）、按模型生成分区保留语句、预览影响、下发到 Doris/Paimon、记录与监控生效状态。分区的实际删除由 Doris 动态分区 / Paimon partition expiration 自治执行，平台不自建清理引擎。

## 二、需求目标

| 目标 | 说明 |
|---|---|
| 策略统一 | 分层默认策略 + 模型级覆盖，一处定义多处继承 |
| 语句自动化 | 按目标表真实方言自动生成 Doris/Paimon TTL 语句，用户零手写 |
| 变更可控 | 下发前必须经过预览确认（哪些分区将消失），支持批量 |
| 状态可见 | 已应用/有漂移/下发失败实时可视，失败自动重试 |
| 存储可算 | 各层存储量、热冷分布、30 天趋势、月度成本估算 |

## 三、核心需求

### 3.1 TTL 策略配置

- 三段保留期（自分区时间起算，天）：热 hotDays ≤ 冷 coldDays ≤ 销毁 destroyDays；销毁是删除边界，空=永久。
- 两类策略：`LAYER_DEFAULT` 分层默认（每层至多一条，可修改不可删）、`CUSTOM` 自定义（模型引用，被引用不可删）。
- 分区粒度：DAY/MONTH/YEAR，默认 DAY。
- 策略**不含**存储类型字段（D2）：语句按目标模型真实方言自动选择。
- 交互：新建策略选"适用分层"即自动预填三段值与名称（自动生成 `{层名}策略`），粒度默认日。

### 3.2 分层默认策略

- 一键初始化按预置模板生成分层默认策略；若分层已配置 `lifecycle_days`，以它为销毁值（D1）：

| 分层 | 热 | 冷 | 销毁 |
|---|---|---|---|
| ODS | 7 天 | 30 天 | 90 天 |
| DIM | 永久 | — | — |
| DWD | 30 天 | 180 天 | 730 天 |
| DWS | 90 天 | 365 天 | 1095 天 |
| ADS | 365 天 | 730 天 | 永久 |

- 预置值用户可改（改的是策略行本身）。

### 3.3 模型绑定策略

- 模型默认**继承**所在分层的分层默认策略（无绑定行=继承，D1 单一事实源）；可绑定任意策略**覆盖**。
- 模型详情 →「生命周期」Tab：展示生效策略、继承来源（分层默认/自定义覆盖）、三段值、粒度、下发状态；操作【修改策略】【预览】【下发】。
- 未配置且所在层也无默认策略时，Tab 给出醒目引导（一键按分层模板创建）。

### 3.4 TTL 语句生成

- 纯函数生成器，按目标方言输出（D3）：
  - Doris/StarRocks → `ALTER TABLE db.tbl SET ("dynamic_partition.enable"="true", "time_unit"=…, "start"=-销毁, "end"=3, "prefix"="p", "hot_partition_num"=热)`；永久 → `enable=false` 关闭语句（D4，可复制可审计）。
  - Paimon → `ALTER TABLE tbl SET ('partition.expiration-time'='Nd','partition.expiration-check-interval'='1 d','partition.timestamp-formatter'=按粒度)`；永久 → 空操作说明。
- 生成语句展示给用户、可复制；下发时执行的就是这份语句（所见即所发）。

### 3.5 TTL 预览

- 输入模型（或批量模型），输出：分区列表（Doris 走 `SHOW PARTITIONS` 真实枚举，D6）按三段归类——热分区（数量+范围）、冷分区、**将被删除**（逐个列名，红标）；预计下次清理时间。
- 无分区枚举能力的数据源（Paimon 目录等）降级为"边界推算"：给出截止分区名与推算删除数量，明确标注"预估"。
- 模型未绑定分区（非时间分区表）→ 明确提示"该表无时间分区，不适用 TTL"，不报错。

### 3.6 策略下发

- 预览确认后执行（单个或批量勾选多模型）：经 `DataSourceExecutionProvider` 对目标数据源执行 ALTER；每模型一条下发记录（语句、数据源、结果、错误、操作人、触发方式）。
- 失败自动重试：定时任务扫描失败记录重发，上限 5 次后置"终态失败"并出现在监控异常区（D5/D7）。
- 策略内容或模型绑定变更 → 相关模型自动标"漂移（未下发）"，监控一眼可见（D5）。

### 3.7 TTL 监控

- 模型 TTL 状态机：`UNSET 未配置 / APPLIED 已应用 / DRIFT 有漂移未下发 / FAILED 下发失败`；列表支持按分层/状态过滤，行内直达【预览下发】。
- 最近下发流水（近 N 条）与异常告警区（重试耗尽、数据源不可达）。
- 分区实际清理由存储引擎执行，"清理记录"以**分区数变化推算**展示（最近两次快照对比），标注推算来源。

### 3.8 存储统计

- 定时快照（每日）：对每个分层库执行 `SHOW DATA`（Doris）采集各表字节数入快照表。
- 页面：各层存储量（堆叠占比）、热/冷分布（按模型生效策略的 hot/destroy 边界对分区快照归段）、近 30 天趋势线、月度成本估算 = 存储量 × 单价（单价在设置里配置，未配置显示"—"）。

## 四、需求范围

| 模块 | 内容 |
|---|---|
| lifecycle（新） | 策略/绑定/生成/预览/下发/监控/存储统计全部后端与页面 |
| semantic | 分层默认策略初始化种子（lifecycle_days 兼容）；无代码变更 |
| modeling | 新增跨模块只读 SPI `ModelTtlQueryApi`（模型+表名+分区+方言）；模型详情页嵌入生命周期 Tab |
| datasource | 复用 `DataSourceExecutionProvider`；无代码变更 |
| audit | 策略增删改、下发操作走 `BusinessAuditService` |

## 五、预期效果

| 维度 | 效果 |
|---|---|
| 治理 | 每个分区表都有可审计的保留策略与生效状态 |
| 效率 | 策略→语句→下发全自动，批量一次完成 |
| 安全 | 删除前强制预览确认；平台永不手工 DROP PARTITION |
| 成本 | 存储量/趋势/费用可视化，销毁边界收敛可验证 |

## 六、用户角色

| 角色 | 场景 |
|---|---|
| 数据治理管理员 | 配置分层默认策略、审阅自定义策略、处理漂移 |
| 建模工程师 | 在模型 Tab 查看/覆盖本模型策略、预览下发 |
| 平台运维 | 监控页看下发失败与重试、存储趋势 |

## 七、关键决策

| # | 决策 | 说明 |
|---|---|---|
| D1 | 单一事实源 | TTL 策略表是生命周期唯一事实源；`semantic_layer.lifecycle_days` 作为旧字段仅初始化和兜底读取，新交互不再双写 |
| D2 | 存储类型不手选 | 原规划"新建策略→存储类型(Doris/Paimon)"取消：语句方言在生成时按模型真实 dialect/数据源类型判定，避免策略与目标表类型打架（双源冲突） |
| D3 | 生成与执行分层 | 生成器纯函数（单测覆盖全部边界）；执行仅走 datasource SPI。modeling 的 D3"永不执行 DDL"不变，lifecycle 是平台唯一合法 TTL DDL 执行出口 |
| D4 | 永久=显式关闭语句 | destroy 空 → Doris 输出 `dynamic_partition.enable=false`（可审计），Paimon 输出无操作说明 |
| D5 | 下发≠生效 | 引入状态机与漂移检测：策略/绑定变更自动标 DRIFT；失败重试上限 5 次 |
| D6 | 预览按能力降级 | 有 `SHOW PARTITIONS` 能力→真实枚举；否则边界推算并标注"预估"，绝不假装精确 |
| D7 | 业务表为准 | Quartz 内存态不保证跨重启补火；重试队列以 `yak_lc_dispatch_record` 表为事实源，定时器只是闹钟 |
| D8 | 交互原则强制 | 能默认就不填（粒度=日、end 预建=3、前缀=p）、能带出就不选（三段值随分层自动预填）、危险操作必过预览确认 |

## 八、和现有模块的关系

| 模块 | 关系 |
|---|---|
| modeling | 模型生命周期 Tab（前端嵌入其详情页）；`ModelTtlQueryApi` 只读取模型/表名/分区/方言 |
| semantic | `LayerConfigApi` 读分层（库名/数据源）；分层默认策略以 lifecycle_days 为种子 |
| datasource | 下发与分区预览经 `DataSourceExecutionProvider.open(datasourceId)` |
| job(yak-schedule) | 重试闹钟 + 每日存储快照（`YakScheduleNamespaces` 新增 LIFECYCLE） |
| audit | 策略 CRUD / 下发全部留痕 |

## 九、本期范围外

| 事项 | 说明 |
|---|---|
| 自建分区清理引擎 | 平台不下发 DROP PARTITION；清理由存储自治（原则即"存储管执行"） |
| PAIMON 数据源插件 | 本期 Paimon 语句可生成/复制，下发仅对具备 SQL 执行通道且类型匹配的 catalog 生效；独立 PAIMON 插件与元数据后续单独立项 |
| 非时间分区 / 行级 TTL | 仅支持时间分区粒度 |
| 冷存储物理下沉 | 三段是保留语义边界，不触发介质迁移（Doris 热冷由 hot_partition_num 表达） |
| Hive/CK 等多方言 | 生成器预留接口，本期 DORIS 系 + PAIMON 两组 |

---

# 第二部分：设计方案

## 一、整体架构

```
                 ┌────────────────────────────────────────────┐
                 │        data-ops-business-lifecycle          │
  策略管理页 ───▶│  policy   CRUD + 分层默认初始化 + 引用保护   │
  模型Tab    ───▶│  binding  继承解析(层默认)/覆盖绑定          │
  预览/下发  ───▶│  generate TTL 语句生成器(纯函数,双方言)      │
  监控/存储  ───▶│  preview  分区枚举/归类(SHOW PARTITIONS)     │
                 │  dispatch 下发+流水+重试(状态机/漂移)        │
                 │  stats    每日快照+聚合/趋势/成本            │
                 └───────┬───────────────┬────────────────────┘
                         │只读SPI         │SQL执行SPI
                   semantic.LayerConfigApi  datasource.DataSourceExecutionProvider
                   modeling.ModelTtlQueryApi        │
                                              Doris FE(MySQL协议) / Paimon catalog
                                              dynamic_partition / partition expiration
                                              ── 存储引擎自行清理过期分区 ──
```

## 二、功能设计（页面与交互）

### 2.1 策略管理页（菜单：数据生命周期 → 策略管理）

```
策略管理
├── [从分层模板初始化]（幂等；已存在的层跳过并提示）
├── 列表：名称 | 适用范围(分层默认·ODS / 自定义) | 粒度 | 热/冷/销毁 | 引用数 | 状态 | 操作
├── 新建/编辑（抽屉）：
│     适用分层▾(选后自动预填三段值) → 名称(自动生成,可改) → 粒度(默认日)
│     → 热▮7 冷▮30 销毁▮90（数字输入,带"永久"开关）
└── 删除：仅自定义可删;被绑定引用 → 400 提示引用模型清单前5个
```

### 2.2 模型生命周期 Tab（模型详情页内）

```
生命周期
├── 当前策略: DWD 默认策略 (继承自分层) [更换策略▾]     状态Tag: 已应用/漂移/失败
├── 保留: 热30天 · 冷180天 · 销毁730天   粒度: 日
├── 语句预览 [SQL代码块 + 复制]
└── [预览分区] [下发]   ← 无分区表时禁用并提示原因
```

### 2.3 预览 → 下发向导（两步弹窗，单个/批量共用）

```
第1步 预览：目标表清单(批量时逐表)
  dwd_trade_order_detail (Doris · 42 分区)
    热(7): p20260912~p20260918 | 冷(28): p20260815~p20260911
    将被删除(7): p20260808,p20260809,… [红Tag]
    预计首次清理: 明日 00:00 (dynamic_partition 自检周期)
  ⚠ 不可恢复提示 + 勾选确认
第2步 下发：逐表执行结果(成功✓/失败✗+原因) → [完成] 生成下发记录
```

### 2.4 TTL 监控页

```
TTL 监控
├── KPI: 已应用 n · 漂移 n · 失败 n · 未配置 n
├── 模型状态列表(过滤: 分层/状态)   行操作: [查看语句][重新下发]
├── 最近下发流水(分页)：时间|模型|数据源|结果|操作人|错误|重试按钮
└── 异常区：重试耗尽 / 数据源不可达
```

### 2.5 存储统计页

```
存储统计
├── 分层存储量(柱+占比) | 热/冷/待清分布
├── 近30天趋势(按层折线)
└── 月度成本估算: ¥xx（单价未配置时显示 [去配置]）
```

## 三、闭环

```
① 配置: 分层默认策略(初始化/修改) ──② 绑定: 模型继承/覆盖
③ 生成: 语句预览(所见) ──④ 预览: 分区归类(将删红标)──⑤ 下发: 执行+流水
⑥ 监控: APPLIED/DRIFT/FAILED ──⑦ 重试: 定时补发──⑧ 统计: 快照/趋势/成本
             ▲                                        │
             └────────── 策略漂移回⑤, 成本异常回① ◀────┘
```

## 四、菜单结构

```
数据生命周期 (data-lifecycle, section=task, sort 9)   ← ticket 80
├── 策略管理  /data-lifecycle/policy                   ← ticket 88
├── TTL 监控  /data-lifecycle/monitor                  ← ticket 89
└── 存储统计  /data-lifecycle/storage                  ← ticket 89
(模型生命周期 Tab 无独立菜单, 挂在模型详情页)
```

## 五、和现有模块的对接

| 模块 | 对接点 | 方式 |
|---|---|---|
| semantic | 分层清单/库名/数据源/lifecycle_days | SPI `LayerConfigApi`（已有） |
| modeling | 模型+表名+分区列+方言 | SPI `ModelTtlQueryApi`（modeling 新增，ticket 82） |
| modeling | 模型 Tab 嵌入 | 前端组件 import（modeling 详情页 → lifecycle 前端组件） |
| datasource | 执行 ALTER / SHOW PARTITIONS | SPI `DataSourceExecutionProvider`（已有） |
| yak-schedule | 重试与快照闹钟 | `YakScheduleNamespaces.LIFECYCLE` + Handler bean |
| audit | 留痕 | `BusinessAuditService` + `AuditTransactions` |

## 六、SPI 契约设计

| SPI | 方法 | 提供方→消费方 | 失败语义 |
|---|---|---|---|
| `ModelTtlQueryApi` | `TtlModelSource resolve(modelId)` / `List<TtlModelSource> listByProject()` | modeling → lifecycle | NOT_FOUND 异常 |
| `TtlPolicyApi`（预留） | `TtlSummary summaryByModel(modelId)` | lifecycle → 概览/血缘 | 返回 null |
| 复用 `LayerConfigApi` / `DataSourceExecutionProvider` | — | — | 数据源不可达→下发 FAILED |

## 七、落地路线

| 阶段 | Ticket | 内容 |
|---|---|---|
| P1 底座 | 80 | 模块骨架 + 契约文件集 + Flyway + 菜单权限 |
| P1 策略 | 81~83 | 策略 CRUD / 分层默认 / 模型绑定继承 / 语句生成器 |
| P1 通道 | 84~86 | 分区预览 / 下发+流水+审计 / 重试+漂移 |
| P2 可视 | 87 | 监控与存储统计 API（快照任务） |
| P3 界面 | 88~89 | 前端：策略页 → Tab/向导/监控/存储页 |

## 八、一句话总结

> 数据生命周期（TTL）：策略按分层预置可覆盖，语句按真实方言自动生成，下发必经分区预览确认，平台记状态与流水、存储引擎执行清理——**平台管策略，存储管执行**。
