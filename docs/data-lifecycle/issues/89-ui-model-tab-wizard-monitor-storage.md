# Ticket 89：前端 · 模型生命周期 Tab + 下发向导 + 监控/存储页

**对应需求：** 3.3 / 3.5 / 3.6 / 3.7 / 3.8 | **阶段：** P2 | **模块：** yak-ops-ui + lifecycle

**What to build：** 建模工程师在模型详情"生命周期"Tab 看到生效策略卡（继承来源 Tag、三段、粒度、状态色点、SQL 预览可复制），点【预览分区】进入两步向导（热/冷/将删红 Tag 清单 + 不可恢复确认勾选 → 逐表下发结果）；批量下发从 TTL 监控页勾选模型发起同一向导；监控页 KPI 卡+状态过滤列表+流水表（行内重试按钮）；存储页分层柱图+30 天趋势折线+成本（未配置单价显示【去配置】弹窗）。

**Blocked by：** 83~87

**交互硬约束：** 将删分区必须红 Tag 全列（>50 折叠"等 N 个"）；确认勾选未打勾时【下发】disabled；批量结果部分失败时用"成功 n / 失败 m"摘要+逐行原因；漂移行高亮并置顶提示【重新下发】；所有列表空态给下一步动作引导。

**验收清单**
- [ ] `components/ModelLifecycleTab.tsx`（嵌入 modeling 详情页 Tabs，只读引入，不改 modeling 契约文件）+ 详情页挂载点接线
- [ ] `components/TtlDispatchWizard.tsx`（预览→确认，单个/批量共用，消费 confirmToken）
- [ ] `pages/data-lifecycle/monitor/index.tsx`、`pages/data-lifecycle/storage/index.tsx`（@ant-design/charts 复用现有图表依赖）
- [ ] services 增补 preview/dispatch/monitor/storage API 封装
- [ ] tsc 199 基线；浏览器 E2E：初始化模板→改 DWD 策略→模型 Tab 预览→真实/降级下发→监控见 APPLIED/DRIFT→存储页出图
