# P0 / #336 — Data Service 消费可观测性与 Console 请求真实性

## 当前主线复核

- 合并 #450 后 Asset Catalog/Inventory 已有 Project/权限切换隔离和 HTTP 失败语义，本轮不重复改资产模块。
- 最新 Data Service 调用记录页刷新失败仅 toast，保留之前 Project/筛选窗口的日志；运行概览页接口失败写回空白全零对象、将 UNAVAILABLE 显示为 0 调用；Debug 虽有文档加载取消标记，但测试调用的异步结果可能在切换 API 或参数后展示为另一个对象的 `200 OK`。三个页面在 Project/权限切换后还保留内部状态。

## 本次修复

1. 调用记录（最近 200）：刷新开始即清理旧记录，逐请求 epoch 只允许最新 HTTP 写入数据、错误或加载态。403 明确无权，500/超时明确不可用，失败提供显式重试。Project/权限变更 remount，旧 API Key、Consumer、调用证据不复用。
2. 运行概览：请求期间与读取失败时隐藏所有模拟零统计指标，错误与真实空结果区分。24h/7d/30d 并发、重试及切换 Project/权限都遵循新请求优先；数据成功返回后再显示真实 `0`。
3. API 调试：API 列表失败不当作成功空列表；参数文档读取失败不当作“当前 API 无请求参数”，并阻止文档不可用时发起可能不完整的请求。切换 API、编辑参数、切换 Project/权限或组件卸载都会作废旧调试返回，禁止错误结果绑定到新 API 显示 `200 OK`。仅采用既有 Console Test API，未改动外部 API Key 权限体系。

## 测试与独立验收

- Jest：共享 epoch/403 分类、调用记录重试与跨项目旧 HTTP、运行概览 403/重试/时间范围乱序/跨项目、调试参数文档失败/旧 API 测试应答/参数变更/跨项目。
- `Product Guard / Architecture / Data Development` 必须按最后 PR HEAD 实际执行通过；此处不宣称 CI 成功。
- 真实 J3/J5 验收仍需独立登录的管理员/受限用户、至少两个 Project、真实 API Key 403/过期/跨项目、成功 Invoke / exact source Audit / persisted Usage、实际 Console 浏览器与部署镜像 SHA。模拟 Jest 通过不等于部署真实通过，#336 保持 PENDING。
- Scope：只修改 Data Service UI 的显示、输入与请求竞态处理；不改变 Data Service、Dataset、Consumption 的 Truth Owner，不新增 RBAC/Schema/决策，不触碰 AI 模块。
