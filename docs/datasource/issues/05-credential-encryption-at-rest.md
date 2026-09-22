# Ticket 05：数据源凭证静态加密（P1）

**对应需求：** 数据源缺失能力盘点 §6 第 5 行 | **优先级：** P1 | **模块：** yak-ops-business-datasource（+common 配置约定）

**What to build：** `connection_params` 与 `original_json` 两列 LONGTEXT 现为明文 JSON（库泄露=全量外部凭证泄露）。在仓储读写边界加对称加密，展示层脱敏链路（`DataSourceSecretCodec`）保持不变。

**设计：**
- 新组件 `CredentialCipher`（datasource 模块 config/gateway 层）：AES-256-GCM，密钥来自 `yak.datasource.credential.secret-key`（env `YAK_DATASOURCE_CREDENTIAL_KEY`，32 字节 Base64）。密文格式 `ENC:` + Base64(iv(12B) ‖ ciphertext ‖ tag)。
- **无密钥=透传（明文兼容模式）**，开发环境开箱即用不炸；读写都能识别 `ENC:` 前缀：读到非前缀按明文放行（惰性升级：下次 update 自然写密文），密钥缺失而数据已加密时抛 41017 `CREDENTIAL_KEY_MISSING`（fail loud，不静默吐密文）。
- 挂钩点：**实际落在 `DataSourceDaoImpl` 的读写口，而不是原设计的 `DataSourceRepositoryAdapter`**。理由：`sync-offline` 的 `LinkUpJobSpecFactory` 与 `sync-realtime` 的 `RealtimeDataSourceResolver` 直接注入 `DataSourceDao` 读取 `connection_params`，钩子在仓储层会漏掉这两条路径（拿到密文当明文用）。DAO 收口后"落库密文 / 内存明文"对所有消费者同时成立；`CredentialCipher` 端口因此放在 `config`（`dao→{config}` 是 DAO 唯一可用的依赖边），实现放 `gateway/adapter`（需要抛 `DataSourceException`）。加解密失败抛 `DataSourceException`。注意 `summary`/`page` SQL 聚合不触碰 LONGTEXT，无需改。
- 存量洗数：启动一次性 backfill（`CredentialMigrationRunner`，`ApplicationReadyEvent` + `ProjectContextScope` 按项目轮转，`yak.datasource.credential.migrate-on-startup` 默认 true）——仅在密钥已配置时执行；幂等：`DataSourceDaoImpl.encryptPlainCredentials` 刻意读原始行（绕过解密钩子），已带 `ENC:` 前缀的值被 `encrypt()` 直通、天然不产生 UPDATE。
- 全仓无任何 AES/Cipher 先例（只有 HMAC token），本票新建，不引第三方依赖（JCE 足够）。
- 密钥来源：`yak.datasource.credential.secret-key` 读 env `YAK_DATASOURCE_CREDENTIAL_KEY`，缺省回落 `.env.example`/compose 里早已存在但从未接线的 `YAK_OPS_DATASOURCE_MASTER_KEY`；32 字节 Base64 按原始密钥使用，其它长度字符串走 SHA-256 派生，使既有部署改一行 env 即可启用而不必重新生成密钥。

**验收清单**
- [x] `CredentialCipher`：round-trip、错误密钥、明文兼容、缺密钥读密文报错 —— `AesGcmCredentialCipherTest` 9 项（含每次加密 IV 不同、不二次加密 `ENC:`、非 Base64/过短/被篡改密文、任意长度 master key 派生）
- [x] 读写边界挂钩（两列全覆盖）—— `DataSourceDaoImplCredentialTest` 4 项：`addDataSource`/`editDataSource` 写密文，`selectById`/`selectByIds`/`selectPage`/`selectAll` 还原文本，无密钥时保持明文
- [x] 启动 backfill（幂等、可关）—— `CredentialMigrationRunnerTest` 4 项：无密钥/开关关闭时零交互、按项目轮转、单项目失败不阻断其它项目；`credentialBackfillStaysProjectScoped` 守住仓储侧项目窄化
- [x] `DataSourceErrorCode` 增 41017/41018；`DataSourceProperties` 增 credential 嵌套配置（`secret-key` + `migrate-on-startup`）
- [x] `.env.example`/application.yml 注释补 `YAK_DATASOURCE_CREDENTIAL_KEY` 说明（默认空=明文模式）
- [x] 单测 + 模块 `mvn test` 绿 —— `./mvnw -o -pl yak-ops-business/yak-ops-business-datasource test`：Tests run 149, Failures 0, Errors 0

**风险记录：** 密钥丢失=凭证不可恢复，`DataSourceProperties.Credential` 注释里写明；密钥轮换不做（后续票）。真机验证待后端重启后跑一次：建库→查库两列为 `ENC:` 前缀→页面/同步模块读取正常。
