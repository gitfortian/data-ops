# AgentScope Java 2.0.3 升级验证

日期：2026-10-07。类别：TECHNICAL / 工程验证证据。基线：`main @ 944da89a`。

本记录区分依赖兼容、数据库升级与真实业务验收。新场景建设内容见 [AI 总纲](../../AI_MASTER_PLAN.md)，固定版本依据与风险见 [框架复用调研](../../AGENTSCOPE_2_0_3_REUSE_RESEARCH.md)；两者均不替代活动 Feature。

## 1. 本次工程变更

- Agent 模块统一的 `agentscope.version` 从 2.0.2 升到 2.0.3，通过同一个 BOM 管理已使用的 core、harness、OpenAI、MySQL、PostgreSQL、Studio、AG-UI 扩展。
- `EffectiveConfigMiddleware` 记录的框架版本同步为 2.0.3，避免执行 trace 仍显示旧版本。
- 增加 `AgentStateStoreUpgradeMysqlTest`：在 CI 数据库中创建独立随机名称的旧 SDK 表，写入既有轮次预算，构造新版 Store 自动补 `version` 列；核验原 JSON 不变、版本 0、身份隔离、旧预算读取、保存后版本 1及新 Store 重新读取。只清理测试创建的表。
- 保留现有 ReActAgent、DB StateStore、Skill Repository / Middleware、预算与任务工具守卫、HITL 和精确轮次语义。未开启新的文件、脚本、工具组或业务写工具。

F-011 中的“复用 AgentScope Java 2.0.2”记录了该 Feature 建设时的工程版本。本次按照用户明确的依赖升级指令更新到 2.0.3，不改变其任务范围、预算、权限和源域规则；当前构件版本以 BOM 与本次验证为准，旧版本记载不作为继续锁定 2.0.2 的指令。

## 2. 工程验证

| 项目 | 结果与证据 |
| --- | --- |
| 2.0.3 编译兼容 | Agent 及上游 27 个模块编译通过 |
| 首次本地回归 | 上游通过；Agent 261 项中 13 项错误，均发生于测试 `HttpServer.create()` 的 JDK 管道初始化，尚未调用模型；不是业务断言失败 |
| 本机环境定位 | Oracle JDK 21.0.12 / Windows；纯 `Selector.open()` 复现 `UnixDomainSockets.connect: Invalid argument`；设置短目录 `jdk.net.unixdomain.tmpdir` 后最小复现通过 |
| 本地回归重跑 | 28 个 reactor 模块测试成功；Agent 263 项，失败 0、错误 0、跳过 2（上述两个依赖隔离 MySQL 环境的用例）；耗时 3 分 48 秒 |
| 依赖收敛 | `dependency:tree -Dincludes=io.agentscope:*` 通过；当前使用的 7 个构件全部为 2.0.3，无 2.0.2 混用 |
| MySQL 旧表兼容 | 由标准架构 CI 的隔离 MySQL 8 执行；本机未配置 CI 数据库时跳过，不连接用户数据库 |
| 产品基线与后端/前端边界检查 | 本地通过；未增加产品入口、跨域真相或依赖走廊 |
| 最终 PR CI | 提交后以 PR 对应 SHA 的 Product Guard、Backend、Frontend、Distribution 和 Gate 为准 |

本地重跑命令（仅当前测试 JVM 参数，未写入生产配置）：

```powershell
New-Item -ItemType Directory -Force target/uds-probe
.\mvnw.cmd -B -ntp -pl data-ops-business/data-ops-business-agent -am '-DargLine=-Djdk.net.unixdomain.tmpdir=D:/tianxy/code/data-ops/target/uds-probe' test
```

环境定位不是普遍 JDK 缺陷结论，也不表示所有 Windows 主机都需要这个参数。CI 使用标准 Linux/JDK 21 环境，不附加该参数、不跳过模型协议假服务测试。

## 3. 上线与真实验收待办

SDK 2.0.3 的 SQL StateStore 构造会处理 `version` 列，即使 `createIfNotExist=false`。MySQL 缺列时会执行 ALTER，首次多副本补列有竞争窗口；PostgreSQL 构造会执行 `ADD COLUMN IF NOT EXISTS`，已有列也不能假设无 DDL 权限要求。旧行初始版本为 0；ReActAgent 默认冲突策略 OVERWRITE 保持不变，本次不切换 FAIL。

以下项目保持 **PENDING**，不因代码/CI 通过标记为真实验收完成：

- PostgreSQL 旧表、最小权限账号和部署启动兼容实测。
- 生产实际 SDK 表名、备份、账号 DDL 权限、受控补列及多副本升级顺序核验；本次没有修改生产库。
- 脱敏生产历史会话及挂起工具轮次在新版下继续执行的验收；新增 MySQL 测试证明预算槽兼容，不代表所有历史状态形状已覆盖。
- 配置真实模型的登录态治理任务、工具澄清/恢复/停止、Skill 更新与撤权验收，以及准确性、费用和用户收益。

真实模型验收按用户已确认的“先代码与 CI，真实模型验收另记待完成”处理。后续场景选择、按轮 Skill 范围/版本、结构化候选与原编辑器交接仍是规划，不计入本次已交付功能。
