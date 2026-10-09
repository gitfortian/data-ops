# 面向用户任务的菜单重构验收记录

日期：2026-09-30
对应 Feature：`F-008`（当前状态：IMPLEMENTING）
依据：GitHub Issues [#288](https://github.com/gitfortian/data-ops/issues/288)、[#289](https://github.com/gitfortian/data-ops/issues/289)

## 自动化证据

- 前端导航、菜单目录、系统导航和侧栏交互：5 个 Jest suites、33 项通过。
- 前端生产构建：`npm run build` 成功。
- MySQL 8.0 隔离迁移：原 V2042（现 V2045）连续执行两次；幂等性、旧叶子 ID/路径/权限、角色授权保留、消费目录最小授权及应用隔离断言通过。临时数据库在脚本结束时删除。
- `git diff --check` 未发现空白错误。

TypeScript 全仓检查仍报告仓库中其他文件的既有诊断；对本次改动文件的输出筛查未发现诊断。

## 页面走查

使用本地构建和模拟 root 登录态打开数据产品目录：

- 完整侧栏显示首页、我的待办、五个业务域和平台设置；目录页面激活数据消费与服务域。
- 消费域展开后先显示数据产品目录、数据集管理，再显示 API 服务、分析展示分组；API 服务浮层包含目录、调用方与密钥、调试、运行概览、调用记录。
- 资产治理组以资产目录和资产概览开头，随后显示资产管理、技术元数据、血缘、质量、安全、生命周期和主数据专业方案。
- 折叠侧栏后仅显示入口图标；业务叶子从无障碍树移除，可通过入口浮层继续访问。
- 浏览器控制台没有 error/warn。

模拟登录仅用于导航呈现走查，不代表真实认证或业务 API 验收。页面数据为空是模拟 API 的预期响应。

## 尚未完成的验收

真实登录态下的 J1、J2、J3、J4、J5 业务链路仍需在集成环境按 #289 清单走查，重点覆盖不同角色授权、刷新登录态、直达深链和项目空间上下文。未取得这些证据前，F-008 保持 IMPLEMENTING；本记录不宣称业务旅程闭环或真实用户效果已验证。

## 2026-10-09 P0 D-04 复核（只收口，不改验收结论）

- [PR #296](https://github.com/gitfortian/data-ops/pull/296) 已在 **2026-09-30 MERGED**（merge commit `c792ee3426fe1ab98f989333cc9a5918e511b692`）；五个业务顶层菜单、可见 Consumption Catalog、隐藏详情 parentId、Platform Settings 已见于 `main@5b5318c5e8705e6d3795e9dafcfdae59b95d0dcf` 的 [navigation.ts](https://github.com/gitfortian/data-ops/blob/5b5318c5e8705e6d3795e9dafcfdae59b95d0dcf/data-ops-ui/src/config/navigation.ts#L55-L101)。历史“仍未实施导航重组/消费入口”不再适用。
- 现存 [navigationMenuContract.test.ts](https://github.com/gitfortian/data-ops/blob/5b5318c5e8705e6d3795e9dafcfdae59b95d0dcf/data-ops-ui/src/config/navigationMenuContract.test.ts) 检查菜单码、父子目录、路由与权限；[数据库菜单迁移回放脚本](https://github.com/gitfortian/data-ops/blob/5b5318c5e8705e6d3795e9dafcfdae59b95d0dcf/scripts/security/test-task-oriented-menu-migration.py) 指向最终归档 **V2045**。上文 5 suites / 33 tests、本地生产构建及 MySQL 8 结果均为**2026-09-30 原历史取证**；本次没有重新运行。
- **真实登录 J1～J5 菜单/深链/角色/Project 浏览器验收未提供**。本次仅核销实现与历史测试的位置，不产生 `E2E_PASS`。F-008 保持 `Status: IMPLEMENTING`；[#289](https://github.com/gitfortian/data-ops/issues/289) 继续 OPEN 直到 QA/Product 签收。
- 具体 D-04/D-05 责任、验收及 P1 只读证据移交详见 [P0 退出与 P1 交接记录](p0-product-exit-p1-evidence-handoff-20261009.md)。
