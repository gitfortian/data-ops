# P0 / #336 — Asset 目录与盘点页异步读取隔离复核

当前主线 Asset Detail 已区分 NOT_FOUND、403 和 UNAVAILABLE；Inventory 三 Tab 和 Catalog 也已有 loadError Alert，不应机械重复历史缺陷。

本次确认的缺口是异步时序：Project/permissionCodes 更换时页面未重置自身记录、筛选、选择和弹窗；在同一 Project 快速更换筛选或分页时，较早 HTTP 响应可能晚到并覆盖新结果，甚至让旧记录成为批量操作对象。

修复范围：Catalog、PendingPool、ChangeConfirm、SourceAccess 四个读列表统一使用 request epoch，仅最新请求允许写入结果、错误和 loading；切换条件或重试先清空旧 rows/total/selected rows，失败提示和空数据不混淆，HTTP 403 直接说明无权。Project 与 permissionCodes 作页面实例 key，切换角色或 Project 销毁旧实例所有数据、筛选、编辑弹窗。

测试：Jest 验证晚到请求无法提交、组件卸载后拒绝提交、403 / 500 / 未知异常不能冒充成功空结果。

边界：只改前端显示与选择状态，不放宽业务鉴权。真实浏览器的跨 Project、权限撤销及网络延迟仍需 #336 Golden E2E 环境实测，离线 CI 不等于浏览器 PASS。不修改其他 Agent 的 AI 功能。
