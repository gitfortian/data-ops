# 第二版自动化与待验收记录

日期：2026-10-05。真实模型验收按用户确认单独待完成；F-010 保持 IMPLEMENTING。

## 自动化检查

- 后端：`mvn -pl data-ops-boot -am -Dtest=Agent*Test,Governance*Test,ToolContractGuardTest,Quality*Test,Asset*Test,DataSource*Test,DatabaseMigrationSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test`。Windows 本地为 Mockito 附加 `-DargLine=-Djdk.net.unixdomain.tmpdir=D:/tianxy/code/data-ops`。
- 前端：完整 `npx jest --runInBand`、`node scripts/check-type-baseline.mjs`，沿用已有 139 条类型债务基线，不增加新诊断。
- 边界：Java/前端 architecture boundaries 与 product baseline；PR 完整 CI 以 GitHub 实际检查为准。
- 新增确定性场景：源读取前拒绝权限；非法模板、字段、百分比、SQL/未知属性、旧定义、DDL 漂移；锁定后重读再拒绝旧定义且无写副作用；Asset 条件 SQL 只更新快照字段；服务器值忽略模型自报数值；保留 JSON 块伪造拒绝；候选最终事件/官方历史一致；有效配置不含密钥；人工带入/失败拒绝/重复防护/切目标迟到/HITL 恢复。
- 真实数据库场景：Architecture CI 设置 `ARCHITECTURE_MYSQL_URL` 后，`DatabaseMigrationSmokeTest.concurrentAssetEditorsCannotOverwriteTheFirstCommittedDescription` 使用生产 Mapper、AssetAppService 和独立事务并发写入同一旧定义，要求后提交者冲突、先提交描述保留。该条件测试本地无隔离数据库时跳过。

本地执行结果：后端 431 项通过、2 项数据库条件测试跳过（未设置 CI 隔离 MySQL 地址）；前端 123 个套件、553 项全部通过；类型债务闸门通过（139 条既有诊断、零新增）；生产构建及前端源码/产物校验通过；Java/前端依赖边界与产品治理基线通过。PR 完整 CI 与真实数据库检查以关联 PR 的检查记录为准。测试替身与真实数据库 CI 分别报告，不混算成真实模型通过率。

## 真实模型与产品旅程：待完成

需要配置模型的测试部署、登录账号、Project、资产及已注册表的监控。逐项执行 NEXT_PHASE_BACKLOG G1～G12，记录版本、有效配置、真实 trace、截图、保存审计、运行结果、权限拒绝与并发冲突；禁止记录密钥或敏感样本。

没有执行登录态产品页面与真实模型回答验收，没有采集准确率/成本/耗时、专家语义评分或试点反馈。不得据此标记 SHIPPED。
