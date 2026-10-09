# P0 Data Service Consumer / API Key / IP 策略的来源失败与 Project 隔离

## 主线核查

- 截至 2026-10-09，PR #460 已合并，Data Service 集市、API 详情和 Console 等消费视图已处理失败空态与 Project 读取乱序，但 **调用方与密钥管理** 页仍有另一套较早实现。
- Consumer 主列表与可选 Access Overview 用 `Promise.all` 绑在一起，一条辅助清单失败导致整个页面失联；失败后可能保留旧调用方、旧选中项和四项由旧记录计算的统计。无显式错误界面，表格仍可能宣称暂无调用方。
- API Key 列表与 Consumer IP 策略读取失败时，原页面仅 toast 而保留上次的 Keys、模式和规则；既有轮换/停用/删除或 IP Mode 修改入口仍可能基于未核实的旧状态出现。
- Key 创建/轮换会显示一次完整 secret。它不应跨 Consumer/Project 传播、由旧异步读取复活、或因创建成功后随手重新取清单失败而被静默抛弃。

## 本批来源真相与隔离规则

1. Consumer 列表为主来源，Access Overview 仅是当前 Project 可选授权 API 投影。两条 GET 用 `Promise.allSettled` 单独判断；主列表成功且辅助失败时允许查看 Consumer 基础信息，但 API 授权范围选择器与保存必须禁用并给出警示，绝不按空列表提交覆盖。
2. 每次主列表重新读取立即清空旧 Consumer、旧聚合指标、上一次打开的管理 Drawer 与表单。403 与服务器/网络失败独立提示且支持显式重试；列表尚未成功时禁止新建和使用旧目标，真实成功空列表才显示“暂无调用方”。
3. Project ID 与 permissionCodes 构成最外层页面的 React 生命周期边界；Consumer ID 是子管理面板的生命周期键。已卸载/过期 HTTP 不允许覆盖当前列表。旧 Key/Source IP 状态不跨 Project 或 Consumer 复用。
4. Key / IP 子面板统一采用最新请求 epoch，先清旧 rows/policy，失败只呈现权限或不可用 Alert + 重试，阻止启停、轮换、删除和更改模式；不把未知状态宣称成“暂无 Key”或“不限制”。
5. 新建/轮换返回的一次性 secret 仅由本地受控 Secret Modal 展示，不持久化、不进入验收日志、不调用持久化存储；创建后列表读取失败时仍由当前 Consumer 页面显示该一次性 secret，直至用户手动关闭；手工刷新主动清除旧 secret，Project/Consumer 卸载也清除。

## 测试与仍待验收

- Jest 页面级合同：Consumer 主列表 403/重试与旧抽屉消失、API 投影失败时管理器 API 操作关闭、跨 Project 新旧请求隔离，Key/IP 读取失败后不再展示旧写操作、Consumer 切换后原 IP/CIDR/Key 请求的迟到结果丢弃。
- CI / Jest 是受控 Mock 与源码契约，不等同真实浏览器或 DB。#336 仍需真实两 Project / 两角色、Key 轮换后的旧密钥拒绝、受限 IP 拒绝、Source Invocation Audit 与 Usage 一致、部署 artifact/commit/实例日志证据。
- Scope：仅前端读状态/禁用操作/已返回一次性 secret 的显示生命周期；不改变后端 Consumer、Key、IP 规则的鉴权、存储、业务语义、Schema、AI 模块或未批准 Product Decision。
