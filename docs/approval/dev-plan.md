# 通用审批流 —— 开发计划（Issue 拆解）

> 配套: [requirements.md](./requirements.md) · [design.md](./design.md)

## 0. 硬性开发约束

1. **契约先行**：`yak-ops-business-approval` 根目录六件套（README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW）先于代码。
2. **依赖方向单向**（D2）：审批中心只依赖 common/audit/datasource(可选)；业务模块依赖 approval 的 `api` 包实现 handler；审批不得 import 任何业务模块。
3. `project_id` 只取服务端 `CurrentProject`；控制器 `@ProjectScope(PROJECT_REQUIRED)`。
4. 无物理外键；Flyway 自持链 `db/migration/yak-approval`（历史表 `flyway_schema_history_approval`）；菜单注册 yak-security 链 **V2033**。
5. 错误码 49001~49099；权限码 `data-approval:read/create/approve/manage`。
6. 回调与审批动作同事务（D5）；handler 异常→整体回滚并回 49009。
7. 在途唯一键 D6 不可放松；列表一律分页；终态清理规则（step 全 SKIPPED）保证待办查询免 join。
8. 前端 `yak-ops-ui` 下 `.md` 只读；菜单码变更需同步 `securityMenuCodes.ts` + 契约测试（ticket 107 一起做）。
9. 不提交 git，除非明确要求；后端由用户 IntelliJ 重启后验证（Flyway 才生效）。

## 1. 里程碑

| 里程碑 | 内容 | Ticket |
|---|---|---|
| A1 底座 | 模块可启动、菜单可见、三表就位 | 101 |
| A2 闭环 | 流程配置 + 发起/待办/批/拒/撤全链路 | 102~103 |
| A3 集成 | SPI 回调 + 模型发布端到端 | 104 |
| A4 铺开 | 标准发布 / 权限申请 / 前端 | 105~107 |

## 2. 状态总表

| 编号 | Ticket | 阶段 | 状态 |
|---|---|---|---|
| 101 | 模块骨架 + Flyway V1 + 菜单 V2033 + 权限/错误码 + 契约六件套 | P1 | ✅ 完成(重启后验证:三张 yak_approval_* 表已建、flyway_schema_history_approval v1 成功、V2033 菜单+5 权限码已落库) |
| 102 | 流程定义 CRUD + 启停 + steps_json 校验(软删码释放) | P1 | ✅ 完成(19 单测绿;REST 已联调:三条流程经 POST /flows 配置成功) |
| 103 | 发起/待办/通过/拒绝/撤销核心闭环 + 在途唯一 + 乐观并发 + 审计 + by-biz | P1 | ✅ 完成(34 单测绿;E2E 实测:待办/计数、49003 在途防重、49005 终态再批、49006 拒绝缺意见、撤销后可重发、终态步骤 SKIPPED、by-biz 正确) |
| 104 | ApprovalApi/ApprovalFlowHandler SPI + 注册表 + modeling MODEL_PUBLISH 接入 | P1 | ✅ 完成(SPI 随 103 交付;modeling 新增 /publish-approval + handler,35 modeling 单测绿。E2E:模型 55 两级连批后真实发布 latestVersionNo=1。备注:独立端点分流,直发接口保留不动) |
| 105 | semantic 标准发布接入（STANDARD_PUBLISH） | P2 | ✅ 完成(semantic 新增 /{id}/publish-approval + StandardPublishApprovalHandler,onApproved→changeStatus(ENABLED),5 新用例 + semantic 全量绿。备注:直发接口保留不动,分流同 104) |
| 106 | security 权限申请（申请单本体 + ACCESS_GRANT） | P2 | ✅ 完成(调研发现申请单本体已存在:AccessPolicy 自带 PENDING→decideApproval 流,范围收窄为分流。新增 /{id}/apply-approval + AccessGrantApprovalHandler(onApproved/onRejected→decideApproval,onCanceled 保持 PENDING),6 新用例 + security 全量 35 绿) |
| 107 | 前端：待办中心/详情/流程配置 + ApprovalStatusTag + 菜单码登记 | P1 | ✅ 完成(pages/approval/{todo,detail,flows} + services/approval + ApprovalStatusTag/UserSelect 组件;securityMenuCodes/navigation/契约测试登记 V2033,契约 5 项全绿、tsc 199 基线。UI 实测:侧栏菜单、建流程(UserSelect 选人)、待办角标→详情→通过/拒绝(必填意见守卫)/撤销、终态按钮隐藏、软删流程。备注:业务页"提交审批"按钮嵌入未在本票范围;顺手修复既有红:新增 V2035 修正 metric 组行(重启后生效) |

## 3. 依赖图

```
101 ─▶ 102 ─▶ 103 ─▶ 104 ─▶ 105 / 106
                 └──▶ 107(前端,103 契约冻结后并行)
```

## 4. 验收口径

- **101**：`mvnw -o -pl yak-ops-business/yak-ops-business-asset,../yak-ops-business-approval test` 绿；全 reactor compile 绿；重启后 3 张表 + 审批中心菜单可见。
- **102**：流程 CRUD 单测过；停用流程发起 → 49002；软删后同码可重建。
- **103**：两级流程全链路单测：发起→一级批→二级批→APPROVED；任一环节拒绝/撤销→终态+剩余步骤 SKIPPED；同人重复批 → 49004/49005；并发双击只成一单。
- **104**：模型"提交审批"→ 待办出现该单 → 批准 → 模型真实 PUBLISHED（回调生效）；handler 抛错 → 审批动作失败且单据仍 PENDING（回滚可见）。
- 每个 ticket 完成即回填本表状态。
