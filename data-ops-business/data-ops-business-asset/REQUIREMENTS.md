# Asset Requirements

> 只描述模块需要什么，不描述怎么实现。按 ticket 追加；行为变更先改本文件再写代码。

## Ticket 90：模块骨架 + 菜单权限

- Maven 模块接线（bom/business/boot），Flyway `yak-asset` V1 建 9 张表，yak-security 菜单迁移 V2032（数据资产组：概览/目录/盘点上架/目录与标签），错误码 48001~48016，权限码 `data-asset:read/create/update/delete`，调度 namespace `yak-ops-asset`。
- 契约文件集（本目录 6 份）创建。

## Ticket 91：资产台账 + 手工登记

- 手工登记（MANUAL）：名称必填、类型默认 DOC、asset_key=`manual:{自动生成 code}`、owner 默认当前用户；登记即 PENDING。
- 台账编辑（名称/描述/访问入口）、负责人变更、软删（仅 OFFLINE/SOURCE_GONE 可删）。
- 列表分页（项目隔离、状态/类型/负责人筛选）。

## Ticket 92：目录树 + 模板初始化

- 目录 CRUD：dir_code 自动生成可改；物化路径维护；移动防环（48006）；有子/有资产不可删（48007）。
- `init-template`：按 semantic 分层+业务域一键初始化 builtin 目录，幂等（48008）。
- 批量移目录。

## Ticket 93：业务标签

- 标签字典 CRUD（tag_code 自动生成可改，重复 48009）；颜色预置色板下拉。
- 资产打标/去标（批量、事务内、幂等）。

## Ticket 94：AssetProvider SPI + MODEL/METRIC provider

- `api/`：`AssetProvider`（sourceType/cursorList≤500/refresh）、`AssetDescriptor`（sourceId,name,description,assetType,layer,domain,suggestedOwner,updatedAt,contentHash,extra）。
- **契约测试先行**：MODEL/METRIC provider 产出的 asset_key 必须与 lineage 既有登记键逐字符一致（小写前缀式），复用源域生成器不复制逻辑。
- provider 实现放源域模块（只读本域 Service），注册为 Spring Bean。

## Ticket 95：对账引擎 + 编目规则

- 编排：全局互斥（48012）、逐 provider 容错（失败可见不影响他人，48011 入口报错）；游标分批 ≤500。
- upsert 三分支：新→PENDING+NEW 变更+套规则+继承 owner；IGNORED→仅刷 reconciled_at（hash 变化回 PENDING）；存在→hash 变→META_CHANGED（不动台账展示列）。
- SOURCE_GONE 窗口判定（默认 7 天，存 setting）；REAPPEARED 恢复原状态待确认。
- 编目规则 CRUD + dry-run（命中数+前 50 样例）；未试跑不可启用（48010）；apply-again 重应用。

## Ticket 96：状态机 + 预检 + 变更确认

- precheck（ids）→ 逐项缺口（负责人/描述/目录/定级建议）+ 5 分钟 token；publish 必带 token（48005），支持一键补默认与"带风险上架"（风险写审计 detail）。
- offline 必填原因（48013）→ 受影响预览（降级可）→ 确认。
- ignore/unignore：仅 PENDING/OFFLINE（48016）。
- 变更确认（覆盖快照列+审计）/忽略（已处理 48014 拒绝重复）。
- 状态非法流转 48003；全部写操作 fail-open 落审计 `ASSET_*`。

## Ticket 97：搜索 + 360° 详情

- 列表搜索：keyword/类型/层/状态/标签/目录/等级组合筛选；默认排序 §6.5 公式（纯台账列）。
- 详情聚合：item 本体+并行 fan-out（源域属性/字段/血缘 1 跳/质量/安全/TTL/趋势/变更），逐分区 OK|UNAVAILABLE。
- DATASET/DASHBOARD/TASK provider。
- 浏览上报（同用户同资产 5 分钟去重）+ 趋势查询。

## Asset Governance Hub MVP：独立 Section 查询

- `GET /api/v1/assets/{id}/sections/{sectionType}` 按 OVERVIEW、TECHNICAL_METADATA、QUALITY、SECURITY、LINEAGE、USAGE、LIFECYCLE、GOVERNANCE 单独读取，不要求慢分区阻塞其它分区。
- 返回 F-001-A 五态、事实 Owner、查询来源、能力状态和原因；单个源域异常只将自身分区降级为 UNAVAILABLE。
- Security、Metadata、Quality、Lifecycle 分区额外校验对应域权限；拒绝时不返回摘要或证据。质量摘要同时需要 `quality:monitor:read` 与 `quality:execution:read`。
- Quality 仅适用于物理表，使用 Quality-owned `SectionProvider` 查询是否纳管、监控状态和最近执行摘要；Metadata 物理表复用 AssetProvider 的源域坐标。确认无监控为 EMPTY，定位失败或服务异常为 UNAVAILABLE。
- Lifecycle 仅适用于 MODEL，复用 `AssetStatusTtlFacts`；未命中策略为 EMPTY，解析失败为 UNAVAILABLE。
- Usage 分别呈现 Asset 页面活动、Lineage 下游结构引用与消费域业务消费，标明来源、范围和状态；Metric 复用 MetricUsageApi，缺少其它对象稳定消费读侧时明确 UNAVAILABLE，不猜成 0，也不新建 Usage Truth。
- Section 记录查询状态、总耗时、失败类别及 Provider 可用状态；日志不得包含治理摘要或敏感事实。

## Ticket 98：健康度 + 驾驶舱

- `HealthScorer` 纯函数：完整性 40（描述10/负责人10/目录5/标签5/注释覆盖率10）· 可信度 40（质量15/血缘10/定级10/变更确认5）· 活跃度 20（浏览10/新鲜度5/下游引用5）；N/A 剔分母；失败依赖记 0 并标注。
- 每日 03:00 全量重算 + 上架/打标/确认后即时单资产重算；浏览聚合与 view_record 90 天清理同任务。
- 驾驶舱 `/overview`：KPI + 类型/层×目录/健康度/状态漏斗分布 + 待办计数（可直达过滤）+ 最近动态；固定 ≤8 查询。

## Ticket 99~100：前端

- 目录页（左树右表/卡片+搜索）、360° 详情页（Tab 分区+血缘块复用）、上架向导（预检→补默认→确认）。
- 盘点页三段 Tab + 目录与标签页；概览驾驶舱。
- 交互总原则：**能选择就不填、能默认就不留空**；危险操作必经预览确认。
