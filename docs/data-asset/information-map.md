# 数据资产 —— 信息来源矩阵与业务闭环

> 配套：[requirement.md](./requirement.md) §3.7/§八、[design.md](./design.md) §二/§八。本文是"全方位观察一个资产"的**信息从哪来、怎么读、拿不到怎么办**的总账，以及资产中心与各治理域如何互相反哺形成闭环。所有接口名均已对照代码核实（2026-09-19）。

---

## 一、三层读取模型（先定纪律，再谈来源）

| 层 | 定义 | 服务场景 | 时效 |
|---|---|---|---|
| **L1 对账快照** | provider 对账时写入台账列（name/description/layer/domain/security_level_code/content_hash） | 列表、搜索、过滤、卡片 | T+1（对账频率） |
| **L2 实时聚合** | 详情页打开时并行 fan-out 读源域 SPI，逐块容错 | 360° 详情各分区 | 实时 |
| **L3 派生缓存** | 本模块定时计算的派生列（health_score/grade/view_count_30d） | 排序、健康环、概览 KPI | 每日重算 + 关键动作即时重算 |

**判定规则（三条，违反即设计错误）**：
1. 需要进 `WHERE / ORDER BY` 的信息 → 必须是 L1 或 L3 列（禁止实时跨域过滤）；
2. 仅展示用的信息 → 一律 L2 实时，**不复制进台账**（D1 管目录不管内容）；
3. 源域已有聚合/快照的 → 复用其成果加读接口，**禁止自建第二份采集**（如存储量）。

## 二、资产信息全景矩阵

### A. 身份与业务语义

| 信息 | 唯一事实源 | 读取方式（层） | 失败降级 |
|---|---|---|---|
| 名称 / 描述 | 源域（provider descriptor） | L1 快照 + L2 实时对照；台账可编辑覆盖（编辑的是编目文案） | 显示快照 + "源域不可用"角标 |
| 资产类型 / 来源域 | 资产中心（source_type 由 provider 声明） | 自有 | — |
| 数仓分层 | modeling 模型归属层；非模型资产无 | L1 快照（源域改层 → META_CHANGED） | 空 = 不过滤该维 |
| 业务域 / 主题 | semantic 业务域字典 + 源域归属 | L1 快照 | 空 |
| 负责人 | **资产中心自有**（D4；登记时继承源域创建人） | 自有 | — |
| 目录归属 / 业务标签 | **资产中心自有** | 自有 | — |
| 上架状态 / 上下架时间 | **资产中心自有**（状态机） | 自有 | — |

### B. 结构元数据

| 信息 | 唯一事实源 | 读取方式 | 失败降级 |
|---|---|---|---|
| 字段清单（名/型/注） | MODEL→`yak_modeling_model_column`（含 field_role/聚合元数据）；DATASET→冻结的 DatasetVersion schema | L2 经 provider.fetchDetail | 字段块"暂不可用"，不显示假空表 |
| 主键 / 粒度 / SCD 策略 | modeling 模型属性 | L2 | 同上 |
| 字段"标准关联"（类型/单位/码值） | semantic 标准字段库 | L2（P2 接入，先列不填） | 列隐藏 |

### C. 技术属性

| 信息 | 唯一事实源 | 读取方式 | 失败降级 |
|---|---|---|---|
| 物理表 / 库 / 方言 / 分区列 | modeling（表结构聚合元数据已持久化） | L2 | 块级"暂不可用" |
| **存储量（字节）** | **lifecycle `yak_lc_storage_snapshot`（表粒度每日 SHOW DATA，已核实）** | L2 经 lifecycle 新增只读 `StorageStatsQueryApi.byTable(...)`——复用快照，不自建采集 | 显示"—" |
| 更新频率 / 产出任务 | task-catalog `yak_task_asset` + 血缘边（表←SQL_TASK） | L2 经 lineage `upstream(assetKey,1)` 过滤 TASK | 空 |
| TTL 保留窗口 / 下发状态 | lifecycle（policy 继承解析 + dispatch_record） | L2 经 `TtlPolicyApi`（**lifecycle 已预留未实现，缺口 G2**） | 块"暂不可用" |

### D. 血缘

| 信息 | 唯一事实源 | 读取方式 | 失败降级 |
|---|---|---|---|
| 上下游局部图（1 跳） | lineage `LineageQueryService.getAssetByKey + graph(assetId, direction, depth)`（**已核实存在**） | L2；asset_key 与血缘键同源直达（D6） | 块"暂不可用" |
| 下游引用数（可信度评分 / 下架影响预览输入） | 同上 `downstream(assetId,1)` 计数 | L2 | 评分该项 N/A（不计 0，见 §五） |
| 全屏图谱 | lineage 自有页面 | 前端跳转带 assetKey | — |

### E. 质量

| 信息 | 唯一事实源 | 读取方式 | 失败降级 |
|---|---|---|---|
| 是否挂质量监控（可信度评分输入） | quality 表资产注册（table-config） | L2 经 `TableQualitySummaryApi.registeredBy(tableKey)`（**缺口 G1**） | 该项 N/A |
| 质量分 / 规则通过率 / 最近执行 | quality 执行记录 | L2 同 API 摘要方法 | 块"暂不可用" |

### F. 安全

| 信息 | 唯一事实源 | 读取方式 | 失败降级 |
|---|---|---|---|
| 表/列安全等级、分类、打标来源 | security `SecurityClassificationQueryApi`（**已实现，已核实**） | L1 快照（仅过滤维度）+ L2 实时（详情以实时为准，与快照不一致时标"快照滞后"） | 块"暂不可用"；过滤用快照不阻塞 |
| 脱敏策略摘要 | security | L2 | 同上 |

### G. 消费与使用（两类语义，分列不混算）

| 信息 | 唯一事实源 | 读取方式 | 失败降级 |
|---|---|---|---|
| 资产页浏览（治理关注度） | **资产中心自有** `yak_asset_view_record` | 自有 L3 聚合 view_count_30d | — |
| 指标被引用/使用 | metric `MetricUsageApi`（**已存在，已核实**） | L2 | 块"暂不可用" |
| 数据集/表被消费（分析/服务调用） | dataset 消费方 + data-service 调用统计 | L2（data-service 读接口 **缺口 G4**，P2 前仅 lineage 下游计数兜底） | 用血缘下游数替代并标注口径 |

### H. 成本（预留位）

| 信息 | 来源组合 | 说明 |
|---|---|---|
| 单资产月成本估算 | lifecycle 存储快照 bytes × lifecycle_setting 单价 | 纯读组合，零新事实；资产详情页"技术属性"块一行展示；未配置单价显示"—"（对齐 lifecycle 口径） |

## 三、业务闭环（四流一屏）

```
             ┌──────────────── 流入（对账） ────────────────┐
 modeling / metric / dataset / dashboard / task-catalog     │
   ──AssetProvider──▶ 台账 item（L1 快照 + 规则预编目）      │
                        │                                   │
             ┌──────────▼──────────┐            ┌───────────▼──────────┐
             │   聚合（360° + 健康度）│            │  待办（治理缺口清单）  │
             │  L2 实时读 B~G 各块   │            │ 未定级/无质量/无TTL/…  │
             └──────────┬──────────┘            └───────────┬──────────┘
   流出（消费发现） ◀────                                   │
   目录搜索/详情/上架状态 ──▶ 分析师/治理员              反哺（跳转源域处置）
                                   │                       ├ 未定级 → security 分级页
                                   ▼                       ├ 无质量监控 → quality 表配置
                    回流（使用/变更事实）                    ├ 无 TTL → lifecycle 策略页
                    view_record / META_CHANGED             ├ 血缘未登记 → 开发/建模挂任务
                    健康度重算 → 待办收敛                    └ 描述缺失 → 本域编辑快照 或 源域补
                                   │
                                   ▼
                    反哺各域：AssetCatalogApi → home KPI / data-service 引用展示
                              AssetHealthApi（预留）→ modeling 详情健康角标
```

**四条流的责任边界**：
- **流入**只有一条路：provider 对账（手工登记是兜底，不是主路）——保证台账无孤儿事实；
- **聚合**只读不存（L2），任何块失败不伪造（home 契约语义）；
- **流出**的每个待办必须"可跳转、带上下文、处置后自动消歧"（下一轮对账/重算后待办计数收敛，闭环可验证）；
- **反哺**是资产中心对外的唯一出口（AssetCatalogApi/AssetHealthApi），其他模块**不得直连 `yak_asset_*` 表**。

**两个故事线验收（E2E 场景锚点）**：
1. 新表上线：建模发布 → 次日对账 NEW（自动预编目+继承 owner）→ 待上架池预检缺"定级"→ 一键带风险上架 → 目录可搜到 → 浏览产生 view → 健康度升 B。
2. D 级治理收敛：概览"D 级 22"→ 点进过滤清单 → 逐个看缺口（无质量监控/无描述）→ 跳 quality 补挂规则、回本域补描述 → 每日重算后 D→C→B，概览数字收敛。

## 四、SPI 缺口清单（谁提供、哪张票补、补前怎么降级）

| # | 缺口 | 提供方 | 归属 ticket | 补前降级 |
|---|---|---|---|---|
| G1 | `TableQualitySummaryApi`（是否注册 + 质量分摘要） | quality | asset 97 开工前由 quality 侧补 | 质量块不可用；评分该项 N/A |
| G2 | `TtlPolicyApi.summaryByModel`（lifecycle 设计已预留未实现） | lifecycle | lifecycle 票 87 顺带 or asset 97 前 | 生命周期块不可用（详情页占位） |
| G3 | `StorageStatsQueryApi.byTable`（读既有表粒度快照） | lifecycle | 同 G2 一并 | 存储量/成本行隐藏 |
| G4 | data-service 按资产消费统计读接口 | data-service | P2（ticket 100 后） | 用血缘下游计数替代并标注口径 |
| G5 | dashboard/chart、dataset、task 三个 AssetProvider | 各源域 | asset 97 | 首版（94/95/96）仅 MODEL+METRIC 两域跑通全链路，其余域逐批接入 |
| G6 | semantic 标准字段关联读（字段块"标准关联"列） | semantic | P2 | 列隐藏 |

## 五、评分输入与矩阵的对账（可信度 40 分逐项落点）

| 评分项 | 矩阵来源 | 拿不到时 |
|---|---|---|
| 质量监控覆盖与通过率 15 | E 块（G1） | N/A 剔分母（非物理对象类）/ 0 分标注"数据不可用"（适用但查询失败） |
| 血缘已登记 10 | D 块 `getAssetByKey` 存在性 | 同上 |
| 安全已定级 10 | F 块快照列（对账刷新） | 非表/数据集类 N/A |
| 变更已确认 5 | 自有 change_record OPEN 计数 | 自有，恒可算 |

> N/A（类型不适用）与 0 分（适用但未做）严格区分——前者不拉低分数，后者是真实治理缺口。

## 六、职责澄清（易混点，评审必读）

| 易混 | 澄清 |
|---|---|
| lineage `searchAssets` vs 资产目录搜索 | 前者搜**图节点**（含 COLUMN 等细粒度、未编目对象），后者搜**台账**（治理过的资产）。入口、权限、结果模型都不同，不合并、不互相代理 |
| quality"表资产注册" vs 资产台账 | 注册 = 监控对象清单（为跑规则服务）；台账 = 治理盘点（为发现/上架服务）。前者是后者的评分输入（G1），不是子集关系，不做双向同步 |
| task-catalog `yak_task_asset` vs 台账 TASK 行 | 任务编目本体仍在 task-catalog（它已有 revision 指针机制）；台账行只是"任务作为资产"的编目门面（状态/目录/健康），经 G5 provider 单向流入 |
| 快照列 vs 实时值不一致 | 规则固定：**列表用快照、详情用实时、两者并存时详情页标"快照滞后（对账于 X 时）"**；不做双写补偿，下一轮对账自然收敛 |
