# 01: 发布审批前端发起入口

**对应需求:** 模型工作台盘点 §6 缺失能力（P0）| 阶段: P0

**What to build:** 后端闭环完整——`POST /api/v1/modeling/models/{id}/publish-approval` + `ModelPublishApprovalService/Handler`（flowCode=MODEL_PUBLISH，批准回调同事务自动发布，在途单唯一由审批中心 uk 保证）；但**前端零入口**——`services/modeling/api.ts` 无该端点调用，`detail.tsx` 只有直发 `/publish` 按钮。审批闸门形同虚设，任何人可直发版本。补齐：详情页按流程配置展示"提交审批 / 直接发布"二选一，并呈现"审批中"态。

**模块归属:** **跨模块**——modeling（详情页+服务层） + approval（已注册 MODEL_PUBLISH 流，无需改）

**Blocked by:** 无（后端已就绪；生效依赖环境中 MODEL_PUBLISH 流程已配置，开工前先探活确认）

**Status:** 已实现（2026-09-22 实测通过，见下；待提交）

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md)；审批接入沿用项目既有"存量代码+审批中心开关"风格（开关关时保持直发，不双轨）；`ModelStatus` 不新增"审批中"状态，审批中经审批域实例反查展示。

- [x] `services/modeling/api.ts` 补 publish-approval 调用与审批状态查询（提交端点 + 复用 `findByBiz`/`listFlows`）
- [x] 详情页发布区按流程配置出"提交审批/直接发布"二选一入口（开关=本项目 MODEL_PUBLISH 流已启用；实测 model 54/63 双按钮出）
- [x] 审批中态展示（含审批单跳转/进度），在途禁重复提交、禁直接改状态（"审批中"标签+查看审批单链接；提交/直发/保存、版本面板回滚全部冻结）
- [x] 批准回调后详情页版本/状态自动刷新为 PUBLISHED（实测：两级批准→15s 轮询内页面翻"已发布 V1/19 列"+提示；驳回→"已拒绝"解冻、保持草稿）
- [x] 审批开关关闭时维持现有直发行为（`publishFlowEnabled` 默认 false，探测失败/未配置静默降级只出"发布"；root 无第二空间权限，未做双空间实测）

**实现注记（2026-09-22）:**
- 落点：`services/modeling/api.ts`(+submitModelingPublishApproval)、`pages/modeling/detail.tsx`(开关探测/在途反查/15s 轮询终态/冻结+入口)、`components/ModelVersionPanel.tsx`(在途禁回滚)、`pages/modeling/constants.ts`+`unified/index.tsx`(`MODELING_PUBLISHED_EVENT` 发布联动刷新型)。
- 后端零改动；`ModelStatus` 三态不变；在途唯一由审批中心 uk 兜底（重复提交后端 49003）。
- 复验补记（2026-09-22 重启 dev server 后）：模型 53 免刷新型全链路实测通过——提交"审批中"冻结 → 两级批准 → 15s 轮询内 detail Tab"已发布"+"已通过"、**统一视图头部**同步翻"已发布"（`MODELING_PUBLISHED_EVENT` 联动）、toast"发布审批已通过，已自动发布为新版本"。
