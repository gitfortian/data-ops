# R2 已批准范围实施与验收（2026-10-04）

性质：Delivery Evidence。依据 ACCEPTED [PD-002](../../decisions/PD-002-governed-consumption-contract.md)
和 APPROVED [F-004](../../features/F-004-governed-data-consumption.md)。不修改它们的状态，
不关闭 #100 / #104，不将后述 PROPOSED Decision 当作实现指令。

## 用户、旅程与事实归属

- User：需要实际查询 Dataset 的消费者、使用 Consumer API Key 的外部集成方及治理 Owner。
- Problem：Canonical 的治理区块仍只有来源缺口，独立 SQL Dataset 无 Producer 回链，
  Data Service 缺少 Asset 投影；Dataset action RBAC 被误当作最终物理列允许。
- Capability：已有 Data Product 的治理只读投影、稳定回链，以及独立 Subscription / Usage / Impact。
- User Journey：J3 / J5；生产发布 → Asset → Canonical → 实际 Query / Invoke → Usage → 影响与生产回链。
- Expected Outcome：两种真实消费保留源版本、稳定 Consumer 和精确证据；无权限/故障不显示成功事实。
- Truth Owner：Dataset / Data Service 定义与发布版本；Quality / Security / Lineage 治理证据；
  Asset 台账；Consumption 的 Subscription 和 normalized Usage。
- Producer / Consumer：来源 Publisher、治理 Section Provider 和消费日志提供事实；
  Canonical / Impact / 用户只读消费这些事实。
- Existing capabilities：ProjectContextScope、AssetProvider、DataServiceReader、DevelopmentDatasetFacade、
  Dataset 物理列安全执行门禁、Consumer grant / API Key、已有 Usage normalize 与 retry。
- E2E acceptance evidence：专用 Golden Sample 的真实 Query / Invoke、精确 Usage 引用、重复订阅、
  Asset / Producer 回链、无效 Key、跨项目隔离及源服务下线恢复。

## 实现

Canonical 通过权限检查后的 AssetDiscoverService 读取 quality / security / lineage，逐区隔离失败，
保留 READY / EMPTY / UNAVAILABLE / FORBIDDEN / NOT_APPLICABLE。不可读区块清空事实与时间；
可读区块标记 indexed source asset 的范围，不把上游物理表质量继承为产品通过。

独立 SQL Dataset 的 Producer 使用所属 Development Node；Data Service 使用所属 Reader 投影为
`data_service:<id>` Asset。游标最多 500 条、读取依赖 trusted CurrentProject，投影排除 SQL、连接参数和 Key。
手动 Asset 对账的后台线程用既有 ProjectContextScope 恢复已验证项目，避免丢失项目上下文。
Asset / 来源模块没有反向依赖 Consumption，也没有第二份来源 Definition / Publication。
两个来源详情增加“查看消费与治理”入口，复用既有 ProductKey 路径生成器；不增加一级导航。

Dataset action RBAC 拒绝仍为 FORBIDDEN；grant 只说明具备查询动作权限，最终物理列裁决保留
UNAVAILABLE 和 exact-query 下一步。执行继续走现有来源安全门禁；不凭预览/权限/订阅生成成功 Usage。

真实查询暴露 Yak 登录上下文未投影为 Servlet Principal，导致 Dataset 安全门禁与 Subscription
无法获得主体。Boot 的窄 Filter 仅在 Dataset / Consumption Console API 将已验证 CurrentUser
投影为 MVC Principal；角色码从所属 RoleService 读取，读取失败拒绝继续，不猜测 ID / 名称。
匿名请求不借用其他 Servlet Principal，客户端 Header 不提供身份；外部 API Key Invoke 保持独立平面。
Boot 的 Console 登录例外仅增加现有 `/api/v1/data-service/runtime/**` 调用路径；管理 API
仍要求登录，公开调用继续由已有 Consumer grant / Key / IP policy 裁决。

`consumption.py` 通过来源 API 管理专用样本，先持久化新节点身份再配置。冲突拒绝复用；
重复运行保留业务身份，仅轮换专用标记 Consumer Key，真实执行历史正常追加。
Data Service Golden runner 验证实际 Consumer grant、真实 API Key invocation identity，
不以旧 source authMode 代替 effective consumer policy。
两个 Node 证据 runner 保留超出 JavaScript safe integer 范围的 JSON 整数 token，
避免来源 revision ID 舍入；Canonical 的版本 identity 本来就是字符串，不重写来源事实。

## 运行证据

运行实例 `http://localhost:18082` 使用独立本地产物，连接原 Golden Sample 项目 7 与对照项目 8。
原 8080 开发服务未重启；先前由本次工作创建的 R1 验收进程已停止，以遵守 Workflow 单实例锁。
未修改共享库迁移历史。运行包 SHA256：
`00B5076F3BC8E2B988141F10EB6F6E4F99A0690045613169833D760C6A03D89E`。
源码 HEAD 为 `6f0bbbcd02af7941e16c37dbfe5d2b53cf384792`，证据 runner 的整数精度修复在采集时尚未提交。
`deploymentCommit=null`；产物哈希证明本地运行包，不冒充远端 main / CI 部署。

| 场景 | 结果与事实 |
| --- | --- |
| Dataset Query | 3 行；发布版本 1；QueryPerformance 与 Impact 保留 exact queryId、root ConsumerRef |
| Data Service Invoke | 3 行；revision `2106611498130968577`；仅发送 API Key，无 Console Cookie / Project Header；Consumer 1、真实 invocation 与 normalized Usage |
| Canonical / Producer / Asset | Dataset 1 → Node `2106611110803771394` / Asset 4；Service 1 → Node `2106611496155451393` / Asset 5；版本、稳定回链和 Asset canonical resolution 一致 |
| 治理分区 | 两类 Quality NOT_APPLICABLE、Security EMPTY；Dataset Lineage READY，Service Lineage EMPTY；没有全区不可用，也没有继承物理表通过 |
| 重复订阅 | 两类 Subscription identity 分别为 1 / 2；连续提交不生成重复关系 |
| 运行幂等 | 重跑保留 Node / Dataset / Service / Version / Asset / Consumer / Subscription identity；真实 Query / Invoke history 追加；仅专用 Key 轮换 |
| 无效 Key / 匿名管理 | 两者均 HTTP 401；不以已登录 Console 测试冒充外部消费 |
| 跨项目 | 两类 Canonical 为 NOT_FOUND，Asset source lookup 为 NOT_INDEXED |
| 下线 / 恢复 | lifecycle 仍 PUBLISHED、版本不变、availability UNAVAILABLE；下线 Invoke HTTP 500，恢复 HTTP 200 |

原始无凭证日期快照：[消费验收](consumption-acceptance-20261004.json)、
[重复初始化](consumption-idempotence-20261004.json)。快照包含合成对象、样本 SQL、Key identity/prefix，
没有 raw Key、密码、Cookie 或连接参数。机器绑定与可恢复进度 `*.local.json` 不提交。

## 工程验证

- Consumption 全部 77 项测试、AssetReconcileService 15 项测试通过，含后台 Project 恢复回归。
- Boot Principal 5 项、公开/管理配置边界 2 项测试通过；后端 69 模块打包通过。
- 相关 Architecture / DependencyBoundary / GovernanceContract 定向测试通过。
- Python Golden Sample 12 项、Node lossless JSON 3 项通过；后者覆盖超大 ID、转义 SQL 文本及非法 JSON。
- 前端定向 2 suites / 12 项通过；类型债务门禁通过（既有 139 条诊断，未增加）。
- Product baseline / Product Guard parser、脚本语法及 `git diff --check` 通过。
- GitHub PR 的完整回归由现有 CI 执行；本地定向测试不冒充全部后端/前端回归或浏览器验收。

## 产品治理与剩余验收

新增三个提案：[PD-005 质量发布门禁](../../decisions/PD-005-quality-publication-gate.md)、
[PD-006 安全对象映射](../../decisions/PD-006-consumption-security-object-mapping.md)、
[PD-007 生命周期范围](../../decisions/PD-007-governed-source-lifecycle-scope.md)。均为 PROPOSED / NOT_STARTED；
须经过现有决策流程并补 APPROVED Feature 与来源契约后才能实施候选规则。

F-004 仍为 PARTIAL：来源 owner / visibility owning read contract、exact-query 预裁决、受限角色、
浏览器完整路径，以及来源故障、撤销/过期 Key、IP/配额、normalize 故障重试等完整矩阵尚未完成真实验收。
两种产品 Quality 当前合法为 NOT_APPLICABLE，Lineage 无注册事实时为 EMPTY，不能宣传为质量已通过或血缘完整。
非 Canonical Data Service transport 的前端 numeric BIGINT 契约仍需后续梳理；本批只修正证据 runner 精度。
源域没有冻结 DEPRECATED / RETIRED command；runtime disable 只改变 availability，不改变 release lifecycle。
现有服务下线请求可能返回 HTTP 500，验收须记录实际结果，不声称满足 HTTP 503。
Impact 是限定窗口内已知消费者，不能声明覆盖所有外部依赖。
