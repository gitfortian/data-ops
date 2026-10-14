# FE-A1（首段）｜首页用户故事任务入口实施说明

> 日期：2026-10-14。关联 [#513](https://github.com/gitfortian/data-ops/issues/513)、[#550](https://github.com/gitfortian/data-ops/issues/550)、[#559](https://github.com/gitfortian/data-ops/issues/559)、[PR #555](https://github.com/gitfortian/data-ops/pull/555)。  
> **本 PR 为生产 React/UI 的独立 Draft 实施，不代表 UX 全域签收、合同已冻结或 G1/G2 已完成。未改 API、路由、菜单码、数据库及后端。**

## 交付的完整用户动作（有限范围）

- 已授权用户进入 `/home` 时，第一屏可选择 **建设可信数据 / 使用已有数据 / 处理运行异常**，不被四种技术任务创建入口抢占优先级；按 `currentProject` 与当前登录身份展示。
- 从建设进入当前角色**确实可访问**的数据源、Metadata 采集或既有建模工作台（按次序）；从消费进入 Consumption Product（无受治理目录权限则隐藏消费任务入口，不误进生产者 Dataset）；从异常进入 Quality 或 DataDev 执行记录（如果受限则降级到已有 Workflow 实例）。
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

## FE-A1 第二段（2026-10-14）｜当前项目真实规模与运行概况

新增 `HomeProjectPulse`，在任务入口后显示最多 6 个真正可用且有权的指标卡；不能把业务 Owner 的不同事实加总成统一“数据资产成功数”，也不把设计、执行、消费混为一个发布状态。

| 卡片 | 原 Owner + 已有读接口 | 身份/权限门禁 | 不能误读为 |
|---|---|---|---|
| 数据源 | Home Cockpit `header.dataSourceCount`、`dataSourceAvailable` | DataSource 原 route/menu | 无法读取时 0 |
| 物理模型 | Modeling `pageModelingModels({pageNo:1,pageSize:1}).pagination.total` | `modeling:read` + 模型菜单码 | Logical 模型数/已部署表数 |
| 指标定义 | Metric `getMetricStats().total` | `metric:read` + 指标菜单码 | 已发布指标数 |
| 表资产 | Asset `homeAssetOverviewApi.overview().data.dataset.tableAssetCount` | `data-asset:read` + Asset Catalog 菜单码 | 已上架治理资产数 |
| 今日质量异常表 | Quality `homeQualityOverviewApi.overview().data.todayIssueTableCount` | `quality:execution:read` + Quality Overview 菜单码 | 所有域的统一运行异常数 |
| 运行中任务 | Home Cockpit `header.runningCount`、`runningAvailable` | Home 当前项目已授权只读汇总，不链接不存在的通用 Run 页面 | DataDev 独占执行数、成功运行数 |

- 项目/用户/原菜单授权组成 `pulseScopeKey`；切换时直接屏蔽旧 scope 快照，Promise 回调只写当前 scope；某一个 Provider 失败仅降级该卡，其他领域仍可读取。
- `safeOwnerCount` 只接受非负安全整数。0 是真实 Owner 返回的 0，`null/undefined/NaN/负数/字符串` 全部保持未知；失败显示“暂不可用”，不把未知填 0。
- 首页既有 `useHomeDataAssets` 与新概况复用 `readSharedHomeAssetOverview(scopeKey)`，只合并同项目、同用户和同授权组合同时在途的请求，Promise settle 后清理，不跨项目缓存上一次业务值。
- HomeTaskStarter 不重复显示统计，原 HomeHeader、DataCenter、审批/资源/消费等面板仍保留。没有项目/权限不发敏感 Owner 查询、不呈现跨项目数据卡。
- 新增 `projectPulseContract.test.ts` 校验负值/未知、项目与角色分离、逐卡独立权限；还需实际浏览器 320/390/768/1024/1440 与双项目部署联调证明完整 UX。

**仍未实施/不能宣称已完成**：正式导航的 20 任务入口冻结、Model/Metric 的完整状态分布、指标发布/资产上架精确计数、真实跨域 G1/G2 E2E 和 D1–D5 产品签署。所有未批准写合同仍禁用。

### Build 经验与修复

2026-10-14 首轮真实 Umi TypeScript 构建发现 `HomeProjectPulse.tsx` 与 `homeProjectPulse.ts` 名称仅大小写不同，bundler 将纯函数导入解析到组件自身。现统一更名为 `projectPulseContract.ts` 及对应测试，删除旧同名文件；必须以新提交的完整 CI 为准，旧失败不能误标通过。项目切换的资产卡片与近期资源列表在 React 首次 render 即通过完整权限 scope 遮蔽旧快照，不再等待 effect 清理。
