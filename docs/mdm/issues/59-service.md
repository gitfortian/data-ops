# 59: 主数据服务(API/订阅/缓存)

**对应需求:** requirement.md 3.7 主数据服务/design.md 3.7/menu.md 四(跳数据服务)|阶段:P1

**What to build:** 提供主数据查询 API(按 master_id 查询、按条件搜索),订阅管理(系统订阅实体变更,变更时通知订阅方),缓存(复用 data-service 缓存能力)。**无独立菜单**:API 管理/缓存复用 data-service,主数据订阅为 MDM 特有;查询/订阅界面位于实体详情页与总览(menu.md 五),对外 API 经 data-service 暴露。

**模块归属:** data-ops-business-mdm(+data-service)

**Blocked by:** [55 采集执行](./55-collect-execution.md)

**Status:** ready-for-agent

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— 契约先行(更新 mdm + data-service 契约:API 管理/缓存复用 data-service,MDM 只新建订阅与查询适配);project_id 服务端可信上下文,不建物理外键;查询 API 返回只含 ACTIVE 记录(不含 MERGED/DELETED);订阅通知基于变更事件(60 审批通过/55 采集更新),不轮询;缓存需处理失效(变更后失效)。

- [ ] 查询 API:`GET /api/mdm/entity/{entityCode}/record/{masterId}`(单条)、`POST /api/mdm/entity/{entityCode}/search`(条件搜索,分页);仅返回 ACTIVE 记录
- [ ] 查询 API 经 data-service 能力暴露/注册,复用其 API 管理与缓存
- [ ] 订阅管理:实体详情页"分发配置/订阅"区维护订阅方(系统名、订阅实体、通知方式),订阅列表服务端聚合
- [ ] 变更通知:主数据变更(采集更新/清洗合并/审批通过)后按订阅推送,记录通知日志
- [ ] 缓存:查询结果缓存,变更后失效/刷新(复用 data-service 缓存能力)
- [ ] 总览"分发状态"卡片展示订阅数(跳数据服务)
- [ ] 契约测试通过

**验证记录(待填):**
