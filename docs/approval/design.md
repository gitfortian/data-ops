# 通用审批流 —— 设计方案

> 版本: v1(简单版) · 日期: 2026-09-19 · 配套需求: [requirements.md](./requirements.md)
> 核心原则：**流程与业务解耦(D2)；同事务回调(D5)；在途单唯一(D6)**

---

## 一、模块定位

**职责**：审批流程的定义、发起、待办、通过/拒绝/撤销、步骤流水、终态回调。

**不职责（归业务模块）**：
- 不存业务事实（模型结构/标准内容/权限明细）→ 发起时只存展示用快照 `payload`
- 不做"申请单"本体（权限申请的资源/期限/理由表单归 security）
- 不做通知触达（站内信/IM，P2）
- 不接 `workflow`(Airflow 编排)、不引 Flowable/Activiti

## 二、核心设计决策

| # | 决策 | 理由 |
|---|---|---|
| D1 | 新模块 `yak-ops-business-approval`,表前缀 `yak_approval_*` | 与 asset/lifecycle 同构;命名空间隔离 |
| D2 | 依赖方向反转:审批中心零业务依赖,业务实现其 SPI `ApprovalFlowHandler` | 复用 `AssetProvider` 已验证范式;杜绝循环依赖 |
| D3 | 三级单据模型:flow(定义) / instance(单) / step(级快照行) | 三表即可支撑 1~2 级顺序审批;step 行天然就是审批记录,不另设历史表 |
| D4 | 发起时按 flow 配置**快照**审批人到 step 行 | 流程后续改配置不影响在途单(审批基准=发起时点) |
| D5 | 终态回调与审批操作**同事务同步执行**;handler 抛错→整体回滚,审批动作失败可见 | 最简且无僵尸态;异步+重试表 P2 再加 |
| D6 | 在途唯一:instance 持 `active_flag`(PENDING='Y',终态 NULL)+ 唯一键 `(project_id,flow_code,biz_type,biz_id,active_flag)` | MySQL 唯一索引允许多 NULL,历史单可重复、在途单全局唯一(49003) |
| D7 | v1 每级 ANY(任一审批人处理即定级),级间**串行**;任一级拒绝→整单 REJECTED | 单级否决语义,与"两级=先业务后管理"直觉一致 |
| D8 | flow_code 由流程管理员在审批中心配置,业务代码只引用常量 | 防各模块绕开自建流程实例(需求 R2) |
| D9 | 拒绝必填意见;通过意见可选;意见落 step.comment | 审计可解释性 |

## 三、数据模型（Flyway `db/migration/yak-approval` V1）

通用列所有表都有:`project_id BIGINT NOT NULL`、`created_by`、`create_time`、`updated_by`、
`update_time`、`deleted TINYINT DEFAULT 0`。无物理外键,关联列建索引。

### 3.1 `yak_approval_flow` 流程定义

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AI | |
| flow_code | VARCHAR(64) | 项目内唯一,如 `MODEL_PUBLISH`。唯一键不含 deleted;**软删时将 code 改写为 `{code}#del#{id}` 释放编码**(避免"同 code 多条软删行撞唯一键"的经典坑) |
| flow_name | VARCHAR(128) | |
| description | VARCHAR(512) | |
| steps_json | JSON | `[{"level":1,"approvers":["zhang","li"]},{"level":2,"approvers":["admin"]}]`,1~2 级,每级 1~10 人 |
| enabled | TINYINT | 停用后不可再发起,在途单不受影响 |

### 3.2 `yak_approval_instance` 审批单

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AI | |
| flow_code / flow_name | VARCHAR | flow_name 为发起时快照 |
| biz_type | VARCHAR(64) | `MODEL`/`STANDARD`/`ACCESS_REQUEST`… |
| biz_id | VARCHAR(64) | 业务对象标识(字符串兼容多形态主键) |
| title | VARCHAR(256) | 待办列表展示 |
| payload_json | JSON | 审批依据快照(发起时业务组装,≤64KB);审批人只读 |
| applicant | VARCHAR(64) | 发起人(服务端上下文取) |
| status | VARCHAR(16) | `PENDING / APPROVED / REJECTED / CANCELED` |
| current_level | TINYINT | 当前所在级(终态无意义) |
| active_flag | VARCHAR(1) | PENDING='Y',终态 NULL(D6 防重) |
| finish_time | DATETIME | 终态时间 |

索引:uk_active(project_id,flow_code,biz_type,biz_id,active_flag)；
idx_todo(approver 维度经 step 表 join)、idx_applicant(project_id,applicant)、idx_biz(project_id,biz_type,biz_id)。

### 3.3 `yak_approval_step` 审批级快照行(=审批记录)

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AI | |
| instance_id | BIGINT | |
| level_no | TINYINT | 1 起 |
| approver | VARCHAR(64) | 发起时快照(D4);同级多人=多行 |
| status | VARCHAR(16) | `WAITING(未到级) / PENDING(待其处理) / APPROVED / REJECTED / SKIPPED(同侪已定级)` |
| comment | VARCHAR(512) | 拒绝必填 |
| handled_time | DATETIME | |

索引:idx_pending(project_id, approver, status)(待办主查询)、idx_instance(instance_id)。

## 四、状态机

```
instance:  PENDING ──末级通过──▶ APPROVED(回调 onApproved,业务生效点)
           PENDING ──任一级拒绝─▶ REJECTED(回调 onRejected)
           PENDING ──发起人撤销─▶ CANCELED(回调 onCanceled)

step 推进: approve 第 n 级 → 该级其余人 SKIPPED,第 n+1 级 WAITING→PENDING;
           无 n+1 → instance=APPROVED
终态清理: 单据进入任一终态时,所有剩余 WAITING/PENDING 步骤统一置 SKIPPED。
           推论:step.status=PENDING ⇔ 单据在途且轮到该行——待办查询只打 step 表,
           无需 join instance 过滤,列表分页天然正确。
```

并发防护:approve/cancel 用 `UPDATE ... WHERE status='PENDING'` 条件更新(乐观),0 行即 49005;
同级两人同时点,只有一人成功,另一人收 49005 后列表自然消失。

## 五、业务集成契约（`approval/api` 包,业务模块依赖它）

```java
/** 业务发起/查询入口(同事务;flowCode 未配置或停用直接抛错,不静默). */
public interface ApprovalApi {
  ApprovalInstanceView submit(ApprovalSubmitCommand cmd);   // flowCode+bizType+bizId+title+payload
  ApprovalInstanceView find(String flowCode, String bizType, String bizId); // 在途或最近一单
  void cancel(Long instanceId, String operator, String reason);             // 仅发起人
}

/** 业务模块实现的回调 SPI(按 flowCode 注册,同 AssetProvider 范式). */
public interface ApprovalFlowHandler {
  String flowCode();
  void onApproved(ApprovalDecision d);                       // 业务生效点,与审批同事务(D5)
  default void onRejected(ApprovalDecision d) {}
  default void onCanceled(ApprovalDecision d) {}
}

public record ApprovalDecision(Long instanceId, String flowCode, String bizType, String bizId,
    String payloadJson, String applicant, String lastApprover, String comment, LocalDateTime at) {}
```

规则:
- `ApprovalFlowRegistry` 收集容器内全部 handler Bean 按 flowCode 索引;启动不强制存在
  (允许 flow 先行配置),但**发起时** flowCode 无 handler → 49007(防批完没人生效)。
- handler 内禁止再调审批中心写接口(重入);禁止跨进程 HTTP。
- 业务查状态:直接注入 `ApprovalApi.find`,或 REST `GET /by-biz`(前端按钮态用)。

## 六、REST 契约（前缀 `/api/v1/approvals`,`PROJECT_REQUIRED`）

| 方法/路径 | 权限码 | 说明 |
|---|---|---|
| GET `/todo` | read | 我的待办:approver=我且 step PENDING,分页,倒序 |
| GET `/mine` | read | 我发起的(status 过滤,分页) |
| GET `/handled` | read | 我审批过的(step 有我终态行,分页) |
| GET `/todo/count` | read | 待办角标,单条 COUNT,固定预算 |
| GET `/{id}` | read | 详情:单+payload+step 流水;仅当事人(发起人/各级审批人)或 manage 权限可见,否则 49008 |
| POST `/{id}/approve` | approve | body:{comment?};校验当前级审批人=我(49004) |
| POST `/{id}/reject` | approve | body:{comment 必填 49006} |
| POST `/{id}/cancel` | read | 仅发起人(49005 语义细分) |
| GET `/by-biz` | read | ?flowCode=&bizType=&bizId= 业务页按钮态 |
| GET/POST/PUT `/flows`、PUT `/flows/{id}/toggle`、DELETE `/flows/{id}` | manage | 流程定义 CRUD+启停;删除=软删+编码释放(在途单不受影响,留痕) |

错误码 49001~:49001 FLOW_NOT_FOUND / 49002 FLOW_DISABLED / 49003 DUPLICATE_IN_FLIGHT /
49004 NOT_CURRENT_APPROVER / 49005 ILLEGAL_STATE_OR_OPERATOR / 49006 REJECT_COMMENT_REQUIRED /
49007 HANDLER_NOT_REGISTERED / 49008 NOT_INVOLVED / 49009 CALLBACK_FAILED(带业务原因) /
49010 PAYLOAD_TOO_LARGE(payload JSON 序列化后 ≤64KB,对齐 MDM 变更单先例)。

审计(`BusinessAuditService`):SUBMIT/APPROVE/REJECT/CANCEL/FLOW_UPSERT/FLOW_TOGGLE 六类。

## 七、界面设计（`yak-ops-ui/src/pages/approval/`）

```
approval/
├── todo/index.tsx       待办中心(Tab: 我的待办 | 我发起的 | 我审批过的),行内快捷进详情
├── detail/[id].tsx      审批单详情:左=审批依据(payload 键值渲染+title),右=级次时间线(Steps),
│                        底部操作区(通过/拒绝弹窗必填意见/撤销)——按角色与状态显隐
└── flows/index.tsx      流程配置(管理员):列表+抽屉表单(级数 1/2 单选,每级审批人多选用户,
                         能选择就不填:选人用既有 UserPicker)
```

- 菜单:一级"审批中心"(V2033),二级"待办中心/流程配置";详情页隐藏路由。
- 业务页嵌入:`ApprovalStatusTag`(轮询 `by-biz` 一次,展示 在途/已通过/已拒绝/无)+
  "提交审批"按钮 → 调各业务自己的发起接口(业务内部转 `ApprovalApi.submit`)。
- 空态文案区分"暂无待办"与"无权限"(不做伪造 0)。

## 八、四场景落地

| 场景 | 接入动作(业务模块侧) | 备注 |
|---|---|---|
| 模型发布 | modeling 新增 `ModelPublishApprovalHandler`(flowCode=MODEL_PUBLISH):onApproved 调既有 `ModelVersionService.publish`;publish REST 改为"提交审批"开关分流 | 版本/血缘链路零改动 |
| 标准发布 | semantic 同理 handler,生效后标准置 PUBLISHED | 草案编辑仍在 semantic |
| 权限申请 | security 先建"申请单"表+页(资源/期限/理由)→ 发起 ACCESS_GRANT;onApproved 生成 AccessPolicy | 申请单本体归 security,独立子 ticket |
| MDM | v1 不动;P2 评估将 `MdmApprovalService` 迁到通用(flow=MDM_CHANGE) | 避免双真相期贸然切换 |

## 九、工程基线

| 项 | 值 |
|---|---|
| Maven | `yak-ops-business/yak-ops-business-approval`(依赖 common/audit;业务模块依赖**它**,方向单向) |
| Flyway | 自持链 `db/migration/yak-approval`,V1 建三表;历史表 `flyway_schema_history_approval` |
| 菜单 | boot yak-security 链 **V2033__register_data_approval_menu.sql** |
| 权限码 | `data-approval:read/create/approve/manage`(常量类 `ApprovalPermissionCode`) |
| 错误码 | 49001~49099,枚举 `ApprovalErrorCode` |
| 定时 | v1 无;P2 超时提醒挂 `YakScheduleNamespaces`(新增 APPROVAL 常量) |
| 契约文件 | 模块根 README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW 六件套 |

## 十、落地路线

| Ticket | 内容 | 阶段 |
|---|---|---|
| 101 | 模块骨架: pom/装配/Flyway V1/菜单 V2033/权限码+错误码/契约文件集 | P1 |
| 102 | 流程定义 CRUD + 启停 + steps_json 校验(级数 1~2、每级 1~10 人、同级去重;v1 **不校验用户存在性**,与 asset 负责人自由串一致) | P1 |
| 103 | 发起/待办/通过/拒绝/撤销核心闭环 + 在途唯一 + 乐观并发 + 审计 | P1 |
| 104 | ApprovalApi/Handler SPI 与注册表 + **modeling 模型发布接入**(首个端到端场景) | P1 |
| 105 | semantic 标准发布接入 | P2 |
| 106 | security 权限申请(申请单本体 + ACCESS_GRANT 接入) | P2 |
| 107 | 前端: 待办中心/详情/流程配置 + ApprovalStatusTag | P1(与 103 并行) |
| P2 池 | 转办/会签/角色审批人/超时提醒/通知/MDM 迁移 | — |

关键验收(103+104):模型发起审批 → 审批人待办可见 → 两级依批 → APPROVED 后模型真实发布;
拒绝/撤销路径回调留痕;同模型二次发起被 49003 挡下。
