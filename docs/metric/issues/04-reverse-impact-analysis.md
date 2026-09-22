# 04: 影响分析反向落地(上游变更→波及指标清单)

**对应需求:** 指标中心盘点 §3.7/§6(P1)| 阶段: P1

**What to build:** /metric/impact 当前只有正向("我的上游变了没"),`MetricImpactService.findAffectedMetrics` 是返回空列表的**桩**(MetricImpactService.java:109-111),"影响分析"名不副实——改了模型/原子指标无法主动通知下游。dependency 表已建 `idx_yak_metric_dep_target(dependency_type, dependency_id)` 索引,按快照反查依赖方成本很低。本单:①去桩,实现按上游(MODEL/ATOMIC 指标/标准)反查受影响指标清单;②页面在 impact 工作台加"反向:谁受影响"视图(选上游对象→受影响指标表,可跳详情);③消费方波及面(usage)待 01 接线后自然增强,本单不阻塞于 01。

**模块归属:** metric(后端反查+前端反向视图);可选加 modeling 详情→指标影响页跳转入口

**Blocked by:** 无

**Status:** 已完成(2026-09-22)

**硬性约束(不可打破):** 遵守 [gap-backlog 批次约束](gap-backlog-2026-09.md);反查基于 dependency 快照即够(登记式口径),不承诺实时血缘图遍历;UNKNOWN 语义延续 M-4 修复口径,不假"待检查"。

- [x] `findAffectedMetrics` 实现+单测(按 dependency_type+dependency_id 命中索引)
- [x] REST 端点暴露反向查询(挂 MetricImpactController,权限对齐)
- [x] impact 页"谁受影响"视图:选模型/原子指标→受影响指标清单+跳详情
- [x] 停用/删除阻断文案与反向清单口径一致(复用 requireNotReferenced)
