# 对 02-产品功能与核心流程（v0.1）的 review（2026-09-21）

方法：逐条事实声明对代码核对（navigation.ts 路由、ApprovalFlowCodes、semantic/layers 源码、MetricEditModal、home 组件、metadata/dataset/workflow 模块、DataSourceDbType 枚举、data-ops-plugins 目录、docs/mdm/reuse-plan.md 实测记录）。

## 必须修的事实错误（3 处，各一行改动）

1. **L66 「定标强制」开关语义写错了。** 原文"该层表字段必须绑定标准字段才能上架"——两处偏差：
   - 开关管的是七格状态机的**「已定标」**格，不是「已上架」格。UI 表单原文即口径：*"强制=该层字段须 100% 绑定标准字段才可定标;贴源镜像层可免强制"*（semantic/layers/index.tsx L407）；
   - **当前是只读观察期，上架写路径不拦任何缺口**——分层页源码注释自述 *"M2-5 只读观察期:展示各层字段落标率,不做硬拦截"*；台账直发上架仍可用（双轨观察，见 PLATFORM_CORE_FLOW M2-5 落地记录）。
   照现在的写法，读者会以为没落标的表上架会被拒——不会。建议改为："每层带'定标强制'开关（该层字段须 100% 绑定标准字段才算**已定标**；观察期内不拦上架）"。

2. **L172 「四类已定义」过时了，现在是五类。** ApprovalFlowCodes 实测 5 个常量：MODEL_PUBLISH / STANDARD_PUBLISH / ACCESS_GRANT / MDM_CHANGE / **ASSET_PUBLISH**（M2-5 上架审批事实源接线已落主线，资产详情页新增「申请上架审批」入口，审批通过自动上架）。另建议补一句使用前提：flow code 是代码侧常量，真实流程需在审批中心 UI 配置；ASSET_PUBLISH 未配置前发起即报 49007（属预期，MODEL_PUBLISH 先例同）。

3. **L199 与第九节自相矛盾。** "① ② ⑤ 成熟"——但第九节自己给 ⑤ 打的是 ★★★☆☆（数据集/分析/仪表盘、API）和 ★★☆☆☆（问数）。建议改为："① ② 成熟；⑤ 资产链路（数据集→分析→仪表盘）完整，问数尚浅"。同一文档内"成熟"和"★★☆"不能同页并存。

## 建议修（引用与口径，不挡发布）

4. **L168 血缘键统一挂 S8 是错引。** 01 v0.6 里 S8 是"资产台账只记目录，不存内容"，与血缘键无关。统一生成对应的是 S6/S7 + M2-3 已落地部分（血缘 assetKey/metricId 参数化、物理表键归一）。删掉 S8 即可。

5. **L144 首页构成与代码对不齐。** HomePage 实际装配的是：数据中心（DataCenter）、工作台（待办/告警/质量健康）、资产总览、调度中心、快捷导航、资源中心、通知中心、快速创建。**"驾驶舱"不在首页**——它是资产概览页的东西（data-asset/overview 文案"治理驾驶舱"）；"质量总览"也不是首页独立卡片，是工作台内的质量健康块。建议按组件清单重写这一句，否则新人按文找页会扑空。

## 核实通过项（抽查证据在案）

- 22 个入口路由全部在 navigation.ts 真实存在（/data-source、/resource-management、/sync/batch-link-up 含单表/多表/脚本三种配置路由、/sync/realtime、/data-metadata 两页、/semantic 五页含 layers、/metric 四页、/modeling、/data-development 含发布中心/运行记录、/workflow/definitions+instances、/dataset、/dashboard、/digital-screen、/data-service 五页、/ai-agent、/data-quality 四页、/data-security 六页、/data-asset 四页、/approval/todo+flows、/data-analysis/lineage）。
- "20+ 数据源、插件扩展"属实：DataSourceDbType 枚举 26 种；data-ops-plugins 下 datasource(jdbc/elasticsearch/mongodb)、storage(local/minio/hdfs——L28 三种存储声明成立)、alert(dingtalk——L176 告警渠道成立)、task(sql/shell/python/java——L100 四类节点成立)。
- 任务目录"无独立页面、只存投影"：仅有 TaskCatalogController，无 UI 路由，口径准确。
- 工作流 SSE/触发/补数：WorkflowEventStream、schedule/trigger、WorkflowBackfillManager 均在。
- 数据集"查询性能诊断"：DatasetQueryPerformanceRecorder/Reader 在。
- 原子指标"选 DWD 模型"：MetricEditModal 以 `layerCode: 'DWD'` 拉候选，必填校验文案一致。
- 指标详情"版本历史/使用情况"：detail 页 tab 均在。
- "CRM 客户 3060 行实测"：docs/mdm/reuse-plan.md 2026-09-19/20 实测记录背书（sink_committed_record_count=3060）。
- 七格命名"归档策略"正确（上一轮对 01 的意见已被吸收）；「已发现→…→运营中→归档策略」与 AssetStatusFlowService 步骤 key 一致。
- 【实测】【梳理】【在建】三级可信度标注纪律好，元数据标"在建"与现状相符（采集/搜索/元模型后端在、UI 仅两页）。

## 结论

三处必须修都是单行改动（定标强制语义、审批五类、⑤成熟度自洽），修完 v0.1 即可作为 03 验收路线的素材库发布。整体质量高于 01 首稿：路由级事实全部对得上，未发现编造的"实测"声明。
