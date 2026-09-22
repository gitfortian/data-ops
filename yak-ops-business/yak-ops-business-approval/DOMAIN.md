# Approval Domain

## 核心概念

### 流程定义（Flow）

每项目若干 `flow_code`（如 MODEL_PUBLISH），1~2 级串行审批，每级静态审批人名单
（steps_json，1~10 人/级，任一人处理即定级=ANY，D7）。业务代码引用编码常量，
编码由流程管理员在审批中心配置（D8）；停用只挡新发起，在途单不受影响。
软删时编码改写为 `{code}#del#{id}` 释放唯一键。

### 审批单（Instance）

一次发起=一张单：flow 快照（code+name）+ 业务定位（biz_type/biz_id）+ 标题 +
审批依据 payload（≤64KB，只读）+ 发起人。**在途唯一（D6）**：同一业务对象同流程
至多一张 PENDING 单（active_flag 技巧）。终态 APPROVED/REJECTED/CANCELED 不可逆。

### 审批步骤（Step）= 审批记录

发起时按流程配置**逐人展开快照**（D4），每级 N 人 N 行：
WAITING（未到级）→ PENDING（轮到）→ APPROVED/REJECTED/SKIPPED（同侪已定级或整单终态）。
**不变量**：单据终态时所有剩余 WAITING/PENDING 统一 SKIPPED ⟹ step=PENDING ⇔ 待办，
待办查询免 join instance。

### 回调（Handler）

终态时同步调用业务注册的 `ApprovalFlowHandler`（onApproved/onRejected/onCanceled），
与审批动作**同事务（D5）**：handler 抛错 → 整体回滚 → 49009，审批人可见可重试。
APPROVED 回调=业务生效唯一时点。

## 不变量

1. **项目空间归属**：全部行带 `project_id`，只取 `CurrentProject`。
2. **在途唯一**：uk(project_id, flow_code, biz_type, biz_id, active_flag)。
3. **级内乐观并发**：`UPDATE instance SET ... WHERE status='PENDING'` 条件更新，
   0 行=49005；同级双人同时批，只成一单。
4. **拒绝必填意见（D9）**；通过可选。
5. **可见性**：审批单详情仅发起人/该单任一级审批人/manage 权限，否则 49008。
6. **审批不改业务**：审批中心永不写业务表；生效动作发生在业务 handler 内。
7. 所有操作走 `BusinessAuditService` 留痕（SUBMIT/APPROVE/REJECT/CANCEL/FLOW_*）。

## 决策（D1~D9）

D1 独立模块 yak-ops-business-approval（不塞进 workflow/mdm）；D2 依赖方向反转，
业务实现审批 SPI；D3 flow/instance/step 三表，step 即历史记录；D4 审批人发起时快照；
D5 同事务同步回调（异步+重试为 P2）；D6 active_flag 在途唯一；D7 级内 ANY、级间串行、
单点否决；D8 流程定义集中配置，业务只引用；D9 拒绝必填意见。
