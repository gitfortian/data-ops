# 平台业务主线：资产状态机

> 2026-09-21 与产品负责人讨论定稿。本文是页面收口（M2）、首页工作台化、跨域集成契约的上位依据；与 [MENU_REDESIGN.md](./MENU_REDESIGN.md) 的关系是：菜单解决"功能在哪找"，本文解决"数据往哪流、停在哪个状态"。

## 一、一句话主张

**平台主线不是"接入→开发→治理→消费"的工序流水，而是"一张表从『存在』到『可信、可找、可用、可退』的状态账本"。** 以资产为账本、标准为契约、质量为体检、审批为闸口、生命周期为出口；各域不各自为政，而是共同推进同一台状态机。

## 二、三个已拍板的取舍

| # | 分叉 | 决策 | 理由 |
|---|---|---|---|
| 1 | 状态机挂什么对象 | **直挂表/字段**；指标/API/主数据不进同一本账，经血缘键（`PhysicalTableAssetKey`）引用其依赖表的状态 | 大一统会卡在状态语义对不齐（"定标"对表字段、指标、API 含义各异），硬套则每类对象开特例、状态机退化成摆设。用引用代替复制。 |
| 2 | 账本归属 | **资产域独占状态定义与推进**；质量/标准/审批/生命周期只"上报事实（盖章）" | 五域今天各说各话，用户拼不出"这张表能不能放心用"。单一账本 = 单一事实源；盖章方无需理解状态机全貌，只对接一个上报接口。 |
| 3 | 定标刚性 | **分层强制**：dwd/dws/ads 等加工层必须完成字段绑定标准字段才可进入"可上架"；ods 贴源镜像层免强制 | 全量强制会把存量表大面积挡在门外，上线初期可消费资产数难看；不强制则标准域退化为参考字典，沦为普通数据目录。分层既守契约刚性又不逼开发给贴源表做无意义落标。强制名单由"数仓标准体系-数仓分层"（/semantic/layers）的分层定义携带，不写死。 |

## 三、状态机定义

对象：**表级资产**（字段级仅记"落标率"子状态）。唯一推进方：数据资产域。

| 状态 | 进入条件（全部满足） | 事实来源（盖章方） |
|---|---|---|
| 1 已发现 | 元数据采集到该表的物理存在 | 元数据域：采集/对账任务 |
| 2 已描述 | 中文名、描述、Owner 非空 | 资产域自身（盘点确认） |
| 3 已定标 | 该表所属分层为强制层 ⇒ 字段标准绑定率 100%（豁免字段除外）；免强制层直接视为满足 | 标准+建模域：字段-标准字段绑定关系 |
| 4 已稽核 | 挂载 ≥1 条质量规则且最近一次执行通过；安全分级已完成 | 质量域：执行结果；安全域：分级打标 |
| 5 已上架 | 上架申请经审批通过，进入资产目录可见 | 审批中心：变更申请结果；资产域：编目 |
| 6 运营中 | 有消费引用（血缘下游/查询/API 调用）且无未闭环问题单 | 资产域：血缘 + 各域告警聚合 |
| 7 待归档/已销毁 | 命中生命周期策略且归档/销毁审批通过 | 生命周期域：TTL 评估；审批中心 |

迁移规则：

- **正向**：1→5 逐级推进，任一条件回退（如质量转不合格、标准解绑）使状态**回跳**到对应格并触发通知，不允许"带病停留在上架后"；
- **6 是运行时状态**，由消费事实自动进出，不经人工；
- **7 是出口**，销毁后账本保留墓碑行（审计需要）。

## 四、跨域引用规则（分叉 1 的落地口径）

- 指标详情页：显示"上游 N 张表，其中 M 张状态 <已稽核"的摘要 + 跳转，不自建状态格；
- API 集市/数据服务：出参表未处于"已上架"时给消费侧风险提示；
- 首页工作台的可信度指标：只统计表级状态分布，指标/服务另行计数，不混排。

## 五、对 M2 收口顺序的检验

按"先让账本成立，再让各页收敛"排程：

1. **M2-1 资产详情页状态条**：定义状态枚举 + 各域事实上报接口（复用现有质量执行记录、标准绑定、审批结果），先只读展示——这一条不完成，后续都没有锚点；
2. **M2-2 目录三合一**：资产目录/元数据目录/盘点"目录与标签"收进资产域，因为分叉 2 判定目录是账本的一部分，只能有一本；
3. **M2-3 血缘统一视图**（带 assetKey/metricId 参数）：血缘是状态回跳和跨域引用的通路，依赖 M2-1 的键归一；
4. **M2-4 首页工作台化**（待办/告警/运行汇聚 + metrics 卡片）：工作台是状态机的驾驶舱，需要 M2-1 的状态分布数据才有意义；
5. **M2-5 定标闸门上线**：最后启用（先跑一个只读观察期，统计各层绑定率，避免一刀切卡死存量）。

### M2-1 落地记录（2026-09-21）

只读状态条已落到资产详情页（`StatusFlowStrip`，数据来自详情接口新增的 `statusFlow` 分区），与本文定义的对齐/偏差：

- **状态枚举**：不新增持久化状态列。七格结论每次按事实实时推导（`AssetStatusFlowService`），台账 `status` 仍是唯一落库状态；`currentStageKey` = 第一个未通过格，全过为 `COMPLETE`。
- **"各域上报"改为"资产域拉取"**：按分叉 2「资产域独占账本」，盖章方不感知状态机——资产域定义只读 SPI（`AssetStatusModelFacts`/`AssetStatusTtlFacts`，由建模/生命周期实现），质量走可选直接依赖，血缘/安全复用既有查询服务。依赖缺失或查询异常一律降级 `UNKNOWN/NA`，**绝不伪造 PASS**。
- **状态 5「已上架」**：审批闸口未接入，暂以台账 `PUBLISHED` 代打（note 中如实标注），M2-5 接审批后替换事实源。
- **状态 6「运营中」**：M2-1 只判"血缘 1 跳有下游消费"；「无未闭环问题单」条件待告警聚合（M2-4）落地后补齐。
- **分层强制口径**：暂按 `layerCode` 前缀 `ODS` 免强制；强制名单迁到 /semantic/layers 分层配置属 M2-5。

### M2-2 落地记录（2026-09-21，A 阶段：页面一本账）

调研结论：所谓"三套目录"在数据层只有一本——目录树唯一（`yak_asset_directory`），`/data-metadata/catalog`「目录浏览」实为对 `yak_metadata_asset` 的类型下钻检索，无自有树表。因此 M2-2 A 阶段做的是**入口归一**而非数据迁移：

- 资产目录新增「台账资产 / 元数据实体」双视图（`?view=entity` 深链），吸收目录浏览 + 统一搜索两颗能力为一颗（`AssetExplorer` 复用，无第二套检索逻辑）；
- 两旧入口 redirect 一跳（同 mdm-approval 先例），菜单项经 V2037 退役（visible/active=0），侧栏元数据组只剩「概览 / 采集与对账」；
- 盘点页确认无目录操作，「目录与标签」本就读写资产域唯一树，无需动。

**B 阶段（后续工单）**：物理表/列尚未进资产台账（`source_type` 无 METADATA、无对应 AssetProvider），「已发现」格对真实物理表空转；打通「直挂表」入账（含归属四列语义统一）是状态机覆盖全量表的前置，独立排期。

### M2-2 落地记录（2026-09-21，B 阶段：直挂表入台账）

B 阶段按上述前置排期落地，切口是「METADATA 供给通道」：

- `AssetSourceType` 新增 `METADATA`（无 DDL：台账 `source_type` 为 VARCHAR，reconcile 主循环按注册 provider 自动覆盖新枚举）；
- metadata 模块新增 `metadata/asset/MetadataTableAssetProvider`：只读共表 `yak_metadata_asset` 上 `source_type='METADATA' ∧ asset_type='TABLE' ∧ gone_at IS NULL` 的行，游标按行主键升序；`asset_key` 逐字符直通（它本就是 `PhysicalTableAssetKey` 归一键，与血缘同源），sourceId 用行 id（表列 `source_id` 是数据源 id，不能当资产身份）；
- 指纹只取目录侧会被人工刷新的四列（display_name/summary/layer_code/entity_status），物理结构变化不进指纹——那是采集 CHANGED 的口径，两条通道各报各的；
- 诚实降级：domainCode 首版留空（`domain_ids` 是 id 列表，按 metric 先例不为此引 semantic）；suggestedOwner 直通 `owner_user`，采集行该列 NULL 就是"没人认领"，入台账后由指派规则补；
- 共表新增一条**只读**语句窗口（`CatalogTableAssetProviderMapper`），不碰写契约；metadata 模块分层守卫显式登记 `asset` 包方向与 `asset.api.*` 放行；
- UI 三点位：sourceType 联合类型、来源标签「物理表」、源对象跳转暂指 `/data-asset/catalog?view=entity`（实体视图暂不支持按 id 定位）。

入账后新表走既有链路：对账判 NEW → PENDING 入盘点池 → 指派目录 → 状态条「已发现」格对物理表给出真实 PASS（行本就是血缘节点）。列级（COLUMN）暂不入账，粒度先停在表。开发库现可供给 2 张 HARVESTED 表，待后端重启触发对账做在线验收。

alarm 页接口对齐（/api/v1/alarm vs /api/v1/alert）随 M2-4 一并处理。

### M2-3 落地记录（2026-09-21：血缘统一视图 + 跨域引用摘要）

- 统一工作台入口参数化（`LineageWorkspace`）：URL 从"仅挂载时读一次"改为跟随式——`assetKey` 主键，别名 `metricId`→`metric:{id}`、`datasetId`→`dataset:{id}`（键与后端登记侧 `MetricLineageRegistrationService#metricAssetKey`/`DatasetLineageSynchronizer#datasetAssetKey` 同口径，dataset 详情页坏掉的 `?datasetId=` 跳转由此免费接通）；`direction/depth` 也入 URL，且 direction 真正送服务端（`getLineageGraph` 不再硬编码 BOTH），深链可直接落"该指标上游 3 跳"。
- 跨域引用摘要（后端）：asset 域新增 `AssetLineageSummaryService` + `GET /api/v1/assets/lineage-summary?assetKey&depth`，产出"上游 N 张表（按质量监控四元组口径去重），其中 M 张未稽核"；判据与状态条第 4 格同一——定位不了四元组/未挂监控/最近执行非 PASSED 都算未稽核；任一事实源缺失一律 `available=false`、计数按 -1，不伪造通过。摘要放资产域实现，因为它是唯一已持有血缘+质量可选依赖的模块（metric 直连 quality 属越界）。
- 消费侧接入：指标详情新增摘要条（未稽核>0 红、0 绿，事实源不可读时如实显示原因而非 0），并一键跳 `/data-analysis/lineage?metricId={id}&direction=UPSTREAM`。
- 刻意不做：① data-service「消费侧风险提示」——数据服务暂无结构化出参表（只有 responseSchemaJson 自由文本 + 源对象引用），"该 API 依赖未稽核表"无从判定，待出参登记补齐再排；② `/metric/lineage` 页退役——它有统一视图没有的 CALIBER/UNIT 依赖清单，等依赖可视化对齐后再迁；③ 新增血缘 REST 端点——by-key + graph 两段请求已够用，避免动血缘公共接口契约。
- 验证：asset 模块 16 类 117 用例全绿（含摘要服务新增 9 例）；tsc 199 回到基线、触碰文件零错误。在线验收与 M2-2 B 的 2 张物理表对账入账合并到下次后端重启后一次做完。

### M2-4 落地记录（2026-09-21：首页工作台 + alarm/ai-agent 缺口收口）

- 首页工作台化：`HomeWorkbenchMain` 在数据质量面板上方新增两张卡——「审批待办」（复用存量 `GET /api/v1/approvals/todo`，行跳实例详情、查看更多跳待办中心，零后端改动）与「质量告警」（新读侧 `GET /api/v1/data-quality/monitor/alert-overview?limit`，近 24h 计数 + 最近事件，行跳监控详情、执行号兜底）。两卡与 home 契约同纪律：加载失败显示"暂不可用"而非 0，投递状态原样透传字符串、前端不扩枚举。
- 告警读侧（后端）：`yak_quality_alert_event` 无 `project_id`，项目边界经 monitor INNER JOIN 收口；`QualityMonitorReader#alertOverview` 钳制 limit 1..50，复用监控域的 `@ProjectScope`/`MONITOR_READ` 类级门。"运行"事实不进首页新建——DataCenter 三本 reader 已覆盖离线/工作流/质量执行。
- alarm 缺口判定：前端 `pages/alarm` 调用的 12 个 `/api/v1/alarm` 接口与后端 `/api/v1/alert` 只有 6 个端点，不是路径笔误而是「告警规则/通知记录」域契约整体缺失；渠道管理已由设置中心 `AlertSettingsPanel` 与 Link-Up 集成页覆盖（两者均正确走 `/api/v1/alert`）。决定：alarm 幽灵页保持不投放、不为其补假接口；待正式规划"告警规则+通知记录"域时以质量 `alert_event` 为事实源起点，不另立一本账。
- ai-agent「CORS」真因收口：`AgentCorsConfiguration` 本身没问题，病根是 `yak-ops-business-agent` 从未进 reactor（modules/bom/boot 三处皆缺，pom 里 starter groupId 也是旧的 `io.yak.framework`），前端为绕 SSE 代理缓冲直连 `:8080`，OPTIONS 打到无 handler 路径被报成 CORS。本次把模块接入 reactor（改 3 个 pom + groupId 修正），`yak.agent.enabled` 显式入 application.yml 且默认 false——缺省态零 Bean、零 Flyway 迁移，开 `YAK_AGENT_ENABLED=true` 后 `/api/v1/agent` 才装配，届时 CORS 按其既有 patterns 生效。
- 验证：quality 模块测试 52/52 绿（排除 3 个 HEAD 上即坏的 architecture 契约测试）；agent 模块与 boot 全量离线编译通过；tsc 回到 199 基线、触碰文件零新增错误。首页两卡在线验收并入下次重启清单（与 M2-2 B、M2-3 深链同批）。

### M2-5 落地记录（2026-09-21：定标闸门只读观察期 + 上架审批事实源接线）

- **A 强制名单迁配置**：「已定标」格不再按 `layerCode.startsWith("ODS")` 硬编码——`yak_semantic_layer`/模板新增 `std_mandatory`（V13 迁移，存量 ODS 行按 `LIKE 'ODS%'` 置 0，其余默认 1），创建/编辑在 /semantic/layers 表单直接勾选（缺省=强制，编辑留空=保持现状）；该事实经 `AssetStatusModelFacts.ModelFacts.stdMandatory` 传给状态条。分层编码未在「数仓分层」登记 → 该格 FAIL 大声报缺（同派生建模 `DeriveLayerPolicy` 纪律），不静默免强制。
- **B 绑定率只读观察期**：新增反向 SPI `LayerStdBindingReader`（semantic 定义、modeling 实现，同 `LayerUsageReader` 方向），按 layer_code 聚合项目内模型的字段总数/`std_field_id` 落标数；分层列表新增「落标进度」列（bound/total·%，满标绿、缺口橙、免强制层不着色）。**刻意不动 publish() 写路径**：观察期内标准字段绑定率只展示、不拦截，避免一刀切卡死存量；硬闸门待观察期数据出来后再拍板。
- **C 上架审批事实源**：审批域新增 flow 常量 `ASSET_PUBLISH`（流程本体照旧在审批中心配置，未配置时发起报 49007）；asset 新增发起服务 + 终态回调 handler（approval 依赖同 mdm 先例为常规 compile 依赖）——批准后由服务端自签预检 token 走**既有 publish 链路**，缺口（负责人/描述/目录）依旧阻断，抛错随审批回滚（49009），不代认风险；handler 只向下依赖 asset 内服务，不回依赖 `ApprovalApi`（防 bean 循环）。状态条「已上架」格经 `ObjectProvider<ApprovalApi>` 读审批事实：有通过单 → "审批通过后上架(单 #id)"；无记录 → 如实标"台账直接上架"；台账 PENDING 且在途有单 → FAIL 标"审批在途"。详情页新增「申请上架审批」按钮（直发「上架」按钮保留=双轨观察）。台账 `status` 仍是唯一落库状态，审批不另立一本账。
- 验证：asset 模块测试 127 全绿（含状态条 12、审批发起/回调各 3），semantic 模块 64 全绿，modeling 观察期 SPI 单测 2/2；modeling/asset/semantic 离线编译 + boot 全量编译通过；tsc 保持 199 基线。分层页/资产详情审批链路在线验收并入重启清单。

## 六、非目标

- 不给指标/API/主数据建模自有状态机（至少第一年前不做）；
- 不在本主线里动权限模型：状态可见性沿用现有项目空间 + menuCode 契约；
- 不做"数据质量分"之类的复合评分——状态格本身就是评分，避免再造一套对不齐的分数体系。
