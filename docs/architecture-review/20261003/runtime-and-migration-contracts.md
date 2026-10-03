# 运行、迁移与交付支持边界

## 用户与结果

用户是平台运维、任务开发者和数据治理人员。问题是公共持久化归属不清、执行容量无显式预算、终态句柄无限保留和发布产物可能混版。
能力复用现有 Project Space、Task/Workflow、Datasource、Flyway namespace 和发行 assembly。用户旅程为定义任务 → 固定版本执行 → 查询真实结果 → 发布同版本系统。
期望结果为持久化 owner 清楚、资源有界、项目隔离、不可用可见、构建一致。业务定义、版本、批次和执行生命周期仍由各业务域拥有。
Job 生产通用运行结果，Workflow/Development 等消费者读取该结果；终态 journal 不拥有新的业务状态机。

## 插件运行矩阵

| 能力 | Owner / 身份 | 预算 | 幂等 / 重启 | 取消 / 超时 |
| --- | --- | --- | --- | --- |
| SQL | Job runtime / SQL executionId；Datasource 解析运行连接 | 每类型 32 个活跃执行，限制并发连接使用 | 项目+类型+幂等键；已持久终态可跨 adapter 重建读取 | 保留插件原取消与结果映射；任务配置和插件拥有超时 |
| Python / Shell / Java | Job runtime / 对应 executionId | 每类型 32 个活跃执行，限制执行器和子进程数量 | 同上；进程内活跃 handle 不跨 JVM 恢复 | 不把取消请求转换为新业务状态；插件拥有外部取消动作 |
| Offline Sync | Offline revision/batch/execution；向 Job 注册能力 | 使用其现有执行认领和 connector 预算 | 不可变 revision、checkpoint、批次与重试仍由 Offline 拥有 | 沿用其外部引擎证据与取消确认 |
| Workflow | Workflow ledger / execution + attempt + dispatch | 沿用 dispatch、恢复、超时协调器 | 持久恢复仍由 Workflow owner 执行 | 不确定外部结果不伪造成功 |
| Agent | 持久 turn 队列 / CAS claim | 固定工作池占用直到异步推理完成或挂起；有界队列（默认16）、独立合并唤醒 | 投递拒绝释放 dispatched；QUEUED 记录可由下一次扫描认领 | 进程关闭释放上游订阅；持久 RUNNING 仍由下次启动的 orphan→INTERRUPTED 规则收敛 |
| Asset reconcile / Alert | 既有 owner | 沿用单进程互斥和插件投递机制 | 不能据 JVM 互斥宣称分布式唯一认领 | 沿用 owner 的现有失败证据 |

`yak.job.runtime.max-concurrent=32`、`max-retained-handles=4096`、`terminal-retention-millis=300000`。预算按执行类型、按 JVM 生效。
活跃句柄不淘汰。终态只有成功写入 journal 后才按 TTL/容量压力移出内存；同键继续读取持久终态。
数据库不可用时保留内存证据，失败写入有重试节流；容量耗尽时拒绝新执行。未装配 journal 的测试/嵌入运行同样有界，达到保留上限后拒绝，避免遗忘幂等键后重放。

支持单个活跃插件执行 JVM。journal 的终态唯一键不是跨 JVM 的执行租约；不能同时启动多个插件执行 owner 并宣称 exactly-once。
JVM 在插件执行中崩溃时 journal 不伪造终态，也不自动重放外部副作用；消费者依其既有 ledger 进入恢复/核对流程。
终态数据库证据不自动 TTL 删除，因为删除会改变幂等窗口；归档需由运维明确重试窗口、备份和业务证据保留策略。
`output` 使用现有 TaskExecution 的运行输出契约，数据库访问必须按项目与现有权限治理；不记录连接凭证，不把完整输出写入日志。

## 数据库支持路径

- 新安装：当前各 namespace baseline，独立 history 表；Job runtime 新增自己的 namespace。
- 支持起点：main `9138491e` 已合并的当前 baseline。现有 baseline SQL 不修改；本轮只新增 Job runtime 表，既有关键数据与 schema 保持。
- 当前 baseline 同库重启：Flyway 正常 validate，不执行 repair，不重复 seed。
- 合并前旧 V/B 历史库：尚无部署版本与备份证据，不能声明支持直接升级。先导出所有 history、schema 和关键业务计数，匹配确切 Git 版本，在副本验证显式升级脚本后登记支持起点。禁止自动 repair、清 history 或执行合并生成器掩盖 checksum 差异。

`migration-baselines.json` 固定当前脚本摘要；`check-migration-history.mjs` 拒绝删除/重写。Source 注释是旧 SQL 的证据分段，不是当前还会执行的版本文件。
已退役的契约转换脚本不再批量改写测试。迁移内容检查继续由 owning 模块 Maven tests 执行。

## 装配和发行

共享业务 DataSource、SqlSessionFactory、SqlSessionTemplate、transaction manager 由 Boot 装配，兼容 alias 不变。`yak.database.enabled` 控制共享存储；`yak.datasource.enabled` 控制数据源功能。
各业务 Mapper/Flyway 仍独立，Security 保留自己的池。关闭数据源功能不关闭共享池；模块级业务依赖仍遵循既有功能开关契约，不推断所有功能可在缺少 Datasource Reader 时独立运行。

干净 checkout 依次执行 `yarn install --frozen-lockfile`、UI test/type gate、`yarn build`、根 Reactor `mvn verify`。
UI manifest 记录 sourceRevision、sourceDigest、assetDigest、版本、Java release 和 schemaPolicy。Maven dist validate 必须确认这些字段；发行 tar 包包含同一 manifest。
历史 Java 8 / Boot 2 data-job 保持独立构建，不纳入当前 JDK21 Reactor。

## 可执行证据

- BusinessDatabaseConfigurationTest：池/事务唯一性、所有兼容 DataSource alias、类型 alias、rollback、关闭共享库。
- PluginContractClasspathTest：五个公共 SPI/API 在不含业务、Spring、ORM 的 classpath 加载。
- DatabaseMigrationSmokeTest：CI 独立 MySQL schema 的默认应用安装、validate、同库重启；本地 Windows JVM loopback 限制另记执行证据。
- SqlTaskExecutorAdapterTest / JdbcTaskExecutionJournalTest / AgentTurnDispatcherTest：容量、终态保留、幂等、项目隔离、拒绝后重投。
- Architecture Checks：所有 PR 运行 backend/frontend/distribution 并通过固定 Architecture gate；未执行或失败不能表示成功。
