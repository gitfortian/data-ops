# 10: 指标市场/AI 推荐/metadata API 外部消费(规划承接)

**对应需求:** 指标中心盘点 §3.11/§6(P3,承接单)| 阶段: P3

**What to build:** 三件未开工规划合一登记:①指标市场(旧-工单 55)、AI 推荐(旧-工单 56)零代码,仅 README 规划条目;②`/api/v1/metrics/metadata/**`(MetricMetadataController,T54)注释称"复用 data-service 鉴权",**当前无法确认任何外部消费方**,属"消费端就绪前的货架"。盘点结论明确:**先修 01 断链再谈市场/AI**,本单在 01 落地且 usage 数据真实之前不启动实施,仅作占位与开工条件记录。

**模块归属:** metric(市场/AI 承接)+ ai-agent 侧(AI 推荐若立项)+ data-service(若 metadata API 鉴权接线)

**Blocked by:** 01(硬前提);AI 推荐另依赖语义/资产数据密度,随智能问数侧统一排期

**Status:** backlog(未达开工条件)

**硬性约束(不可打破):** 遵守 [gap-backlog 批次约束](gap-backlog-2026-09.md);开工前先复核 metadata API 是否确有仓外直连流量(盘点标注"无法排除"),避免误废;市场页不得复制 manage 页统计卡造成第三份冗余(§7.8 噪音教训)。

- [ ] 开工条件核验:01 已上线且 usage 非零;metadata 消费方排查有结论
- [ ] 裁决:市场/AI 推荐排期或继续冻结,落 README 规划条目状态
- [ ] 实施拆单(市场浏览/订阅、AI 推荐口径建议)届时的独立 ticket 由开工时另起
