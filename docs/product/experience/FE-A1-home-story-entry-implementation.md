# FE-A1（首段）｜首页用户故事任务入口实施说明

> 日期：2026-10-14。关联 [#513](https://github.com/gitfortian/data-ops/issues/513)、[#550](https://github.com/gitfortian/data-ops/issues/550)、[#559](https://github.com/gitfortian/data-ops/issues/559)、[PR #555](https://github.com/gitfortian/data-ops/pull/555)。  
> **本 PR 为生产 React/UI 的独立 Draft 实施，不代表 UX 全域签收、合同已冻结或 G1/G2 已完成。未改 API、路由、菜单码、数据库及后端。**

## 交付的完整用户动作（有限范围）

- 已授权用户进入 `/home` 时，第一屏可选择 **建设可信数据 / 使用已有数据 / 处理运行异常**，不被四种技术任务创建入口抢占优先级；按 `currentProject` 与当前登录身份展示。
- 从建设进入当前角色**确实可访问**的数据源、Metadata 采集或既有建模工作台（按次序）；从消费进入 Consumption Product（如果受限则降级到仍具有既有公开路由合同的 Dataset）；从异常进入 Quality 或 DataDev 执行记录（如果受限则降级到已有 Workflow 实例）。
- 指标、安全、业务过程/标准、MDM、AI 专业入口**按需展开**；Agent 受现有服务器 application-features 可用性控制。其 CTA 仅导航，不声称已有获授权的建设 Agent、可信问答响应或完整模型→任务交接。
- 首页项目名取 `SecurityProjectContext.currentProject.projectName`；计数只使用已存在 `useHomeCockpit` / `/api/v1/home/cockpit` 的 DataSourceCount、runningCount，并严格将 `available=false` 展示为“暂不可用”，未知为“—”，零才显示 0。
- 没有有效项目空间时只展示受限说明，不泄露上一项目的待办/任务 CTA；原首页的 HomeHeader、QuickCreatePanel、DataCenter、Consumption、Approval、Quality、资产、运行、通知/调度等**全部保留**，不删除历史专业路由。

## API / RBAC 合同清单

| 入口 | 路由候选（按原有权限回退） | Owner / 保持原合同 |
|---|---|---|
| 建设 | `data-source` → `data-metadata-collect` → `modeling-workspace` | DataSource / Metadata / Modeling |
| 消费 | `consumption-catalog` → `dataset-management` → `data-service-api` | Consumption / Dataset / Service |
| 异常 | `data-quality-overview` → `data-development-execution` → `workflow-instances` | Quality / DataDev / Workflow |
| 其它 | `metric-manage`、`data-security-overview`、`semantic-processes`、`mdm-overview`、`ai-agent` | 各 Owner / Agent Feature + 现有导航 |
| 首页统计 | Home Cockpit `header.dataSourceCount/runningCount`、availability | Home 原域只读 |

实现直接复用 `appRoutes` + `canAccessNavigationRoute`（权限、菜单码、隐藏页的原父路由授权），**不在新 UI 创建第二套 PermissionMapper**；所有跨域逻辑只做导航，无新事实写入。后端仍为最终权限裁决。

## 验收与当前证据边界

新增 `homeJourneyEntries.test.ts`：root / 无授权 / menuCode 缺失 / 建模专家 fallback / Agent flag / Story ID 与真实路由映射反例。需在 PR head CI 执行：

```bash
cd data-ops-ui
npx jest src/pages/home/components/homeJourneyEntries.test.ts --runInBand
npm run tsc
npm run build
```

手工浏览器必须分别检查 320/390/768/1024/1440px，首页首屏主任务按钮、项目切换/注销、异常/消费角色、只读/无项目、键盘 Tab/焦点，以及旧首页所有卡片行为。运行真实双 Project 执行与真实消费 E2E 仍属 #336 与 #559 后续门禁；不能将单纯导航测试改记真实跨域 PASS。

## 后续 FE-A1 工作（不在这个提交内宣称完成）

1. 由各 Owner 读模型聚合 Model / Metric / Asset / Run/Quality / Consumer 规模，避免重复网络调用或把失败冒充 0；项目切换要取消迟到请求。
2. 按已签署的 #550 D1–D5 设计，落实 20 默认业务任务入口与专业工具可发现性，**保持 106+4 路由、MenuCode、Flyway/RBAC 完整兼容**；由专业 Role 浏览器做真实验收。
3. 与 #555 UX 人工签收同步，确保可回退，且不新增 DR-06/07/08 未批准的正式写操作。

**当前状态**：CODE_PROPOSED / UX_SIGNOFF_PENDING / CI_PENDING / MAIN_UNCHANGED。

### PR 元数据与持续集成门禁

本变更分类为 **PRODUCT / Product Behavior Changed: Yes**，PR 必须具备 Change Classification、Product Impact、Truth & Ownership 以及 Acceptance 四节。原域权威事实不变；CI 失败以当前 PR head 与具体 job 日志为准，不复用原型 #555 的自动回归结果。
