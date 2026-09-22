# yak-ops-business-approval

通用审批流：**流程与业务解耦（D2）**。审批流程定义、发起、待办、通过/拒绝/撤销、步骤流水与终态回调；
业务事实（模型/标准/权限明细）一律留在业务侧，审批只持发起时快照 payload。

## 模块边界

- **拥有**：流程定义（yak_approval_flow）、审批单（yak_approval_instance）、审批级快照/记录（yak_approval_step）。
- **不拥有**：业务对象本体与状态（归各业务模块）；"申请单"表单语义（归 security 等）；通知触达（P2）；
  数据加工编排（workflow 是 Airflow 类，与审批无关，不复用）。

## 依赖方向（强制）

```
modeling/semantic/security ──实现──► ApprovalFlowHandler(approval.api) ──注册──► ApprovalFlowRegistry
approval ──(零业务依赖)──► common / audit / datasource(可选) / yak-security(RBAC 注解)
```

- 审批模块 **禁止 import 任何业务模块包**（方向反转，同 AssetProvider 范式，D2）。
- 无物理外键；发起时审批人快照到 step（D4）；终态回调与审批动作同事务（D5）。

## 文档

| 文档 | 内容 |
| --- | --- |
| [DOMAIN.md](./DOMAIN.md) | 领域概念、状态机、不变量、D1~D9 决策 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 分层与包结构 |
| [DEPENDENCIES.md](./DEPENDENCIES.md) | 依赖白名单 |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 需求基线（v1） |
| [REVIEW.md](./REVIEW.md) | 评审记录 |
| 仓库 docs/approval/ | requirement.md / design.md / dev-plan.md（ticket 101~107） |
