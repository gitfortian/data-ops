# 审批能力接入 —— 开关式方案（最终选型）

> 版本: v2 · 日期: 2026-09-20 · 前置: [design.md](./design.md)(工单 101–107 已交付)
> 核心原则：**存量审批代码不动；"要不要走审批"变成审批中心的项目级配置开关**
>
> v1(descriptor 统一接入方案)已降级为备选，见文末。

---

## 一、要解决的问题

modeling / semantic / security 三个功能已按各自 `XxxApprovalService + XxxApprovalHandler +
自定义审批端点` 接入审批（design.md 八）。诉求是：**固定这几个功能点，不再为每个功能定制开发，
由审批中心统一配置"哪些功能开启审批"；关闭时不走审批直接生效，开启时走审批。**

## 二、核心设计决策

| # | 决策 | 理由 |
|---|---|---|
| K1 | 开关判断放**服务端提单入口**：`XxxApprovalService.submit` 开头查开关，未启用→直接调既有生效方法并返回 `directExecuted` 结果；启用→照现有审批链路 | 判断放前端会出现"两个按钮两套调用"；放服务端则前端永远一个按钮 |
| K2 | 前端按钮常驻：业务页只挂"提交审批/发布(走审批)"一个入口，文案由配置下发；响应 `directExecuted=true` 提示"已生效"，否则"已提交审批" | 开关只改服务端行为，页面零分叉 |
| K3 | 新增配置表 `yak_approval_target`（项目级）：`biz_type` → `enabled` / `flow_code` / `button_text`；审批中心新增"审批接入配置"页 CRUD；`GET /api/v1/approvals/targets` 供前端拉取 | 与 flow 同为项目作用域(CurrentProject) |
| K4 | 开关语义：**只作用于新提单时点**。关闭时已存在的在途单继续审完、不受影响；允许随时关 | 与 D4 快照原则一致（审批基准=发起时点） |
| K5 | 直连老端点（`POST /models/{id}/publish` 等）**不加守卫**（v1 立场）：本开关定位为"流程便利"而非"管控红线"，关闭态下直发本就合法 | 若日后要升级为红线，补一个 `approvalGuard.requireNotTakenOver` 到三个直连端点即可(各一行)，是本方案的已知扩展点而非前提 |
| K6 | 生效动作复用现有 handler/service 方法，不新写执行逻辑；开关关闭路径**不产生审批单、不占在途唯一键(D6)** | 存量代码零删改，只加"未启用→直接执行"分支 |

## 三、数据与接口

### 3.1 `yak_approval_target`（Flyway `yak-approval` V2）

通用列同其余审批表(`project_id`/`created_by`/…/`deleted`)。

| 列 | 类型 | 说明 |
|---|---|---|
| biz_type | VARCHAR(64) | MODEL / STANDARD / ACCESS_POLICY，与 `(project_id, biz_type)` 唯一 |
| enabled | TINYINT | 1=走审批，0=直接生效 |
| flow_code | VARCHAR(64) | 开启时使用的流程；提单前校验流程存在且启用(缺失→49001 语义扩展) |
| button_text | VARCHAR(32) | 前端按钮文案，默认"提交审批" |

种子数据：三个 biz_type 各插一行，`enabled` 默认与现状一致（开）。

### 3.2 提单入口改造（每模块约 5 行）

```java
// ApprovalSubmitResult: { boolean directExecuted; ApprovalInstanceView instance; }
public ApprovalSubmitResult submit(Long bizId, String operator) {
  if (!approvalTargetConfig.isEnabled(BIZ_TYPE)) {   // K1
    modelService.publishDirect(bizId, operator);      // 复用既有生效方法
    return ApprovalSubmitResult.direct();
  }
  ... // 现有链路不变：防重→快照→submit
}
```

controller 返回类型从 `Result<ApprovalInstanceView>` 改为 `Result<ApprovalSubmitResult>`
（三个 `/publish-approval`、`/apply-approval` 端点同步）。

### 3.3 审批中心新增

- `GET/PUT /api/v1/approvals/targets`（读=data-approval:read，改=data-approval:manage）；
- 健康检查随 targets 列表返回：`enabled=1 但 flow 缺失/停用` 标红（防配置漂移）。

## 四、前端改动

1. 审批中心新增"审批接入配置"页：三行开关 + 流程下拉 + 按钮文案 + 健康状态列；
2. 三个业务页把现有审批按钮文案/显示改为读 `targets`（一次缓存即可）；语义/模型页保留直发
   入口不动（K5）；
3. 提单响应 `directExecuted` 分支提示；`ApprovalBizTag` 已通用，不变。

## 五、已知边界（明示）

- 开启态下仍可被直连端点绕过（K5，定位=便利）；需升级为管控时按 K5 扩展点加守卫；
- 只覆盖已接入的三个功能；接入**新**功能仍需一套 `XxxApprovalService + Handler`（约百行）——
  若功能数继续增长再评估 v1 descriptor 方案；
- 错误提示需覆盖"开关开但流程未配置/已停用"（49001，文案指向审批中心配置页）。

## 六、工作量

一张票：后端 V2 迁移 + targets CRUD + 三处 submit 分支 + 单测（开/关/配置缺失三态）；
半张票：前端配置页 + 按钮接 targets；半张票：三链路 E2E 回归（含关闭态直接生效路径）。

---

## 附：v1 备选 —— descriptor 统一接入（已降级）

以 `ApprovalTargetDescriptor`（bizType/flowCode/权限点/payload/onApproved 一体）+ 统一
`POST /approvals/submit` 取代各模块三件套，并配执行侧守卫 G4 将审批升级为管控红线。
增量价值在"接入第 4、5 个功能时省掉五件套"，代价是对已交付三模块做迁移重构。
当前只服务固定三个功能点，故降级为备选；完整决策表见 git 历史中本文档 v1。
