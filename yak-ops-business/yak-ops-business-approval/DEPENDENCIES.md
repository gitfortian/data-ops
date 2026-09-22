# Dependencies

## 允许（白名单，超出即评审否决）

| 依赖 | 用途 | 约束 |
|---|---|---|
| yak-ops-common | PO/枚举/权限码/CurrentProject | 只读 |
| yak-ops-business-audit | BusinessAuditService 留痕 | 强依赖 |
| yak-ops-business-datasource | BusinessDatabaseConfiguration | optional（持久化条件装配） |
| yak-security starter | @RequiresPermission / CurrentUserProvider | 注解式 |
| mybatis-plus / flyway / web / validation / tx / lombok | 基座 | 版本走 bom |

## 禁止

1. **任何业务模块**（modeling/semantic/security/mdm/…）——方向反转：业务依赖本模块 api 包。
2. yak-ops-business-workflow（Airflow 编排与审批无关）。
3. Flowable/Activiti 等外置流程引擎。
4. 跨进程调用（回调只允许同事务 Bean 调用）。

## 被依赖方（业务接入姿势）

业务模块 pom 引 `yak-ops-business-approval` → 实现 `ApprovalFlowHandler`（声明 flowCode）
+ 注入 `ApprovalApi` 发起。审批模块对实现类零感知（Spring 收集 List<Handler>）。
