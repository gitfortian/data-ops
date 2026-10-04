# R1 首批实施记录（2026-10-04）

性质：Delivery Evidence。依据 PD-001、F-001-A、F-007、F-008 的现行契约；不修改任何 Decision / Feature 状态，不关闭 #185。

## 已交付

[Golden Sample 工具与运行说明](../../../../scripts/product/golden-sample/README.md)提供三张合成来源表、两个独立 Project、一个 MySQL Datasource、一个停用采集任务、两个手动质量监控及三张物理 Asset 投影。初始化通过所属领域 API 创建业务对象，写入 SQL 仅限 `yak_golden_sample` 来源库。

脚本默认只展示计划，`--apply` 才初始化。已有对象按所有权标记和精确来源坐标复用，冲突或分页不完整会失败；重复初始化不重置已修改的来源行。

本批消费者为治理和验收人员；预期旅程为来源→采集→Asset→证据→专业回链。Datasource、Metadata、Quality、Security 继续持有各自事实，Asset 聚合显示；复用既有 API 与专业页面，没有新增导航、对象类型或状态机。

## 运行证据

本次 API 验收使用本机构建的独立后端实例 `http://localhost:18081`，连接现有本地业务库。原 `8080` 实例未重启。`18080` 不用于验收后端，避免占用 Link-Up 默认端口。

运行包 SHA256：`20B8137ADD85B10EE428B62E7216E826BE28AA7D2BCEAC1B48BE2D8E40865C5B`。验收清单记录的源码 HEAD 为 `9cff2b15e5dc345745f957b2169c8416c8948d19`；构建和工作期间 HEAD 有更新，未核实产物精确提交，因此 `deploymentCommit=null`。这些证据证明本地产物行为，不证明 main/CI 交付。

| 场景 | 结果 | 范围 |
|---|---|---|
| 初始化幂等 | 通过 | 项目、数据源、监控、Asset identity 保持稳定；修改客户备注后重跑仍保留，探针修改随后恢复 |
| 正常质量检查 | 通过 | 引擎 SUCCESS，检查 PASSED，Asset Quality 摘要与证据指向该 executionNo |
| 异常质量检查 | 通过 | 引擎 SUCCESS，检查 NOT_PASSED；不把检查不通过混同为引擎错误 |
| 无监控质量状态 | 通过 | 客户表已注册但未配监控，Quality EMPTY，无执行证据 |
| 八分区契约 | 通过 | 三张表逐分区读取，状态及 Truth Owner 合法；非 OK 有原因；物理表 Lifecycle NOT_APPLICABLE |
| 专业回链 | API 通过 | Quality 动作指向真实监控与执行号；浏览器点击与返回尚待验收 |
| Project 隔离 | Asset API 通过 | 对照项目 source lookup 为 NOT_INDEXED；三张表详情及八分区均返回 48001。账号为 root，受限 RBAC 未验收 |

原始日期快照：[物理表与隔离验收](physical-acceptance-20261004.json)、[初始化幂等证据](idempotence-20261004.json)。机器绑定清单保存在 `runtime-manifest.local.json`，不纳入版本控制。

## 工程验证

- Python 契约测试：8 项通过。覆盖同名歧义、分页不完整、身份重复、成功/失败样本区分、Owner/原因及负向隔离错误识别。
- 现有前端定向回归：6 suites / 34 项通过，覆盖导航契约、分区状态、空报告与手动执行后的日期切换。
- 现有 F-007 定向回归：4 classes / 29 项通过，包含 MdmRecordService、MdmChangeEffectService、MdmChangeApprovalHandler、MdmCollectService。
- 后端构建通过；Python 语法检查和 `git diff --check` 通过。测试不等于浏览器或完整业务 E2E。

## 发现与处置

1. 原 8080 进程在项目创建时出现 Caffeine `NoClassDefFoundError`；运行 JAR 被后续构建覆盖。通过复制构建产物后启动独立实例避开，没有修改缓存业务逻辑。
2. 本机新进程的 Windows Unix Domain Socket 回环连接失败。此次实例指定不存在的临时 socket 目录，让 JDK 的监听器创建逻辑回退到 TCP 后成功启动；仅影响该验收进程，未写入产品配置。
3. 同一用户同时填入项目负责人和普通成员时，当前 API 返回 HTTP 500，日志为 `uk_user_project_active` 唯一键冲突。样本项目只需负责人，因此用互不重复的输入完成初始化。接口校验与角色关系契约需另行核对，本批未修改安全表结构。
4. 初始化时发现采集通配符应为 SQL `%`，已修正工具并验证真实采集及 Asset 对账，不以空采集的 SUCCESS 当作样本成功。

## 尚未收口

| 工作 | 当前状态 |
|---|---|
| Phase7 物理表 | API 证据完成，浏览器旅程待执行 |
| Phase7 Model / Metric / Dataset | 样本与运行证据待补齐 |
| Phase7 权限 | 受限账号、刷新登录态及不可读证据待验收 |
| Phase7 故障隔离 | 未注入故障，不能据正常分区响应宣称通过 |
| Phase7 项目隔离 | Asset API 完成；其他领域与浏览器范围未完成 |
| F-007 | 定向单测通过；审批保护→重跑→恢复来源→再次重跑的运行旅程待完成 |
| F-008 | 定向前端回归通过；真实角色、登录态迁移和深链走查待完成 |
| F-004 | 按 A/B/C/D 剩余缺口进入后续批次，当前未宣布完成 |
| 可选优化 | 告警通知/复检回链、物理覆盖率口径待冻结；完整问题状态机、发布门禁、安全新映射和生命周期扩展按治理流程推进 |

PD-001 / Phase7 仍为 PARTIAL，F-007 / F-008 不据此标为 SHIPPED。规划中的 2~3 周 / 3~4 周是目标窗口，不能由本批结果换算为确定交付日期。
