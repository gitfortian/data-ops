# Asset Quality 分区验收交接清单

变更分类：TECHNICAL  
产品行为变化：No。该改动修正 Quality Section provider 的重复装配，使既有 Section 契约由 Quality 域现有实现提供；不新增状态、字段、入口或 Quality Truth。

## 自动化证据

- [x] Asset 模块目标单测：31 项通过。
- [x] Quality 模块目标单测：10 项通过。
- [x] Asset + Quality 目标 reactor 构建：通过。
- [x] 静态审阅：代码中 `QualitySectionSummary` 只有定义和工厂方法，没有消费者；未发现序列化或源码依赖，可删除。
- [x] 装配约束：Asset 不再注册 fallback；Quality 条件开启时只装配 Quality-owned `QualityAssetSectionProvider`。
- [x] 项目隔离：Asset 按可信 project ID + asset ID 查找；Quality DAO 查询使用当前 project 条件，相关目标测试通过。

基线编译修复另有独立 commit：Quality 的 optional execution summary Reader 委托，以及 `TableMonitorSummary` 旧构造器修正；各有回归测试。基线首次编译的两处错误已在该 commit 修复。

此前一次较宽的选测还触发了已有 `QualityProjectScopeContractTest` 历史迁移路径缺失（引用的 V6 migration 文件不存在）；最终目标命令未包含该过时测试，Asset 项目隔离由 `AssetAppServiceTest` 验证，Quality 项目隔离由 `QualityMonitorDaoProjectScopeTest` 验证。

## 运行时验收（待执行）

- [ ] 页面 / 浏览器检查：未执行，未通过。
- [ ] 登录 / 会话检查：未执行，未通过。
- [ ] 真实 API 验收：未执行，未通过。
- [ ] Quality 未装配场景：单元测试覆盖 `UNAVAILABLE`；运行时验收未执行，未通过。
- [ ] 权限拒绝与项目隔离：单元/源码级证据覆盖；运行时验收未执行，未通过。

本任务没有执行页面、登录、浏览器或真实 API 验收，也没有保存凭据。若后续需关闭产品验收，须在受控环境执行真实用户路径并记录脱敏证据；本交接清单不代表验收通过或 Feature 已 SHIPPED。
