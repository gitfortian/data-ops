# 01: 指标消费接线(usage 断链修复)

**对应需求:** 指标中心盘点 §5.4/§6(P0)| 阶段: P0

**What to build:** 全仓 grep 证实 dataset/dashboard/data-service **没有任何一方** import 或调用 `MetricUsageApi`,且其数据模型里没有指向指标中心的引用字段(dataset 的 "metrics" 是字段级聚合绑定 `DatasetMetricBinding(fieldId, aggregation)`,data-service 的是运行时监控量)。usage SPI/表/聚合/删除阻断四件套齐备但等一个不来的上报方——使用统计恒 0、删除保护永不触发、影响面评估悬空,③→⑤价值闭环断裂。本单把"消费方引用指标"从契约变成事实:消费方保存时能**显式选择指标中心定义的指标**并落引用/上报 `MetricUsageApi.record`,编辑/删除时撤回或更新;usage 明细(`GET /{id}/usage`,当前无前端封装)随之补上页面。

**模块归属:** **跨模块**——metric(SPI 实现与消费视图,已备)+ dataset + dashboard + data-service(三侧消费方各建绑定入口)

**Blocked by:** 无;建议按消费方拆三张子卡分批落地,**dataset 先行**(有保存/编辑闭环、离消费最近)

**Status:** 部分完成(2026-09-22，dataset 先行切片已落地；dashboard/data-service 同构子卡未开工；端到端实测随批次统一验收)

**硬性约束(不可打破):** 遵守 [gap-backlog 批次约束](gap-backlog-2026-09.md);消费方**只经 api 包 `MetricUsageApi` SPI** 上报,禁止直读 yak_metric* 表;上报 fail-open(记录失败不阻断消费方保存,与血缘登记同风格);引用需可区分来源类型(DATASET/DASHBOARD/API)与消费方主键,支撑反向影响面。

- [x] 先立裁决:绑定形态定稿=**显式选择**(不做口径映射猜测,能选不填),已落 05 乱象清单 R-10;绑定事实复用 yak_metric_usage 按 (usage_type, usage_id) 全量替换,SPI 增 syncBindings/revoke/boundMetricIds,fail-open
- [x] dataset 侧:DatasetNodeEditor 属性页"引用指标"多选,保存成功后 `PUT /nodes/{id}/dataset/metric-refs` 同步;节点删除(`DevelopmentNodeController.delete`)联动 revoke 防孤儿 usage 永久阻断指标删除;metric 依赖入 data-development pom(optional,同 semantic 风格)
- [ ] dashboard、data-service 侧同构跟进(可各自拆子卡;注意 data-service "metrics" 是运行时监控量,接线前先确认其确有引用指标的业务语义)
- [x] metric 侧:详情页"使用情况"补 usage 明细列表(`getMetricUsageList` 封装 + 类型/使用方/上报时间表)+ 数据集统计卡(UsageSummary 增 datasetCount)
- [ ] 验收:创建引用→使用统计五卡非 0→删除被阻断的端到端用例通过(需后端重启+页面实测)
- [x] 04 图 MTR→DBH/SVC 边标注更新为"被数据集/看板/API引用"(dataset 已实、DBH/SVC 待接,§5.4 复核行记"半实"),盘点 §3.8/§5.4 同步
