# Requirements (v1 baseline)

来源：docs/approval/requirements.md（评审后修订稿）。

## 范围内（F1~F8）

| # | 能力 | 验收口径 |
|---|---|---|
| F1 | 流程定义 | 1~2 级、每级静态名单（ANY）、启停、软删码释放；steps_json 校验（级数/人数/同级去重，不校验用户存在性） |
| F2 | 发起审批 | ApprovalApi.submit + 校验 flow 启用（49002）、handler 已注册（49007）、payload≤64KB（49010）、在途唯一（49003） |
| F3 | 我的待办 | step 单表分页；/todo/count 固定预算单 COUNT |
| F4 | 审批操作 | approve 级推进、reject 必填意见整单否决；均条件更新防并发（49004/49005） |
| F5 | 撤销 | 仅发起人仅 PENDING；终态清理剩余步骤 |
| F6 | 审批记录 | 详情=单+payload+步骤流水；按业务对象查在途/最近一单（by-biz） |
| F7 | 回调 | 终态同事务同步回调；失败整体回滚 49009 |
| F8 | 审计 | SUBMIT/APPROVE/REJECT/CANCEL/FLOW_UPSERT/FLOW_TOGGLE 全留痕 |

## 范围外（P2 池，只列不做）

转办/加签/会签/委托/条件分支/角色动态审批人/超时提醒/消息通知/撤回重发/表单引擎/MDM 迁移。

## 工程基线

Flyway yak-approval V2；菜单 V2033；错误码 49001~49099；权限码 data-approval:read/create/approve/manage；
列表全分页；分页参数 pageNo ≥ 1、pageSize 1..200；project_id 服务端可信；无物理外键。
流程列表也必须分页；软删 tombstone 不依赖可变长 flowCode；撤销原因保存在审批实例并随详情返回。
终态 callback 失败时，审批单与 step 仍保持原状态，审计记录该次失败操作。
