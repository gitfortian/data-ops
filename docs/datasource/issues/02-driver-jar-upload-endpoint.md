# Ticket 02：JDBC 驱动包上传后端端点（P1）

**对应需求：** 数据源缺失能力盘点 §6 第 2 行 | **优先级：** P1 | **模块：** data-ops-business-datasource

**What to build：** 补齐前端已在展示的断链功能：`POST /api/v1/data-source/plugin/driver/upload`。前端 `components/DriverManager/index.tsx` + `services/data-source/driver.ts:5` 已就绪（表单值期望拿到「驱动位置」字符串），后端全仓无此 Controller。

**设计：**
- 新 `DataSourceDriverController`（或并入 PluginConfigController）：`multipart file + dbType`，权限 `DataSourcePermissionCode.CREATE`。
- 落盘目录可配：`yak.datasource.driver.directory`（默认 `./data/drivers`，模板 `yak.resource.storage.local.base-directory`）；文件名清洗防路径穿越（参照 `LocalStorageOperator.java:263-264,306-310` 的 normalized-must-stay-under-base 守卫），仅收 `.jar`，大小走 spring multipart 上限。
- 运行时注册：新建 `DriverJarRegistry`——URLClassLoader 加载 jar，`ServiceLoader.load(java.sql.Driver.class, loader)` 找到驱动并 `DriverManager.registerDriver`（持有 classloader 强引用防 GC），按 dbType 记录，重复上传替换；jar 内无 Driver 实现则 41015 `DRIVER_INVALID`。
- 响应：`Result<String>`（落盘绝对/相对路径），与前端 `data?.path || data?.fileName` 兼容。
- 仓内无任何 URLClassLoader 先例（`ElasticsearchSdkIsolationTest` 只是源码扫描守卫），本票为绿地实现，保持最小：不做热卸载。

**验收清单**
- [x] 端点 + 校验（扩展名/大小/文件名清洗/路径守卫）
- [x] `DriverJarRegistry` 注册与替换；`DataSourceErrorCode` 增 `DRIVER_UPLOAD_INVALID(41015)`、`DRIVER_UPLOAD_FAILED(41016)`
- [x] `DataSourceProperties` 增 `driver.directory` 嵌套配置
- [x] 单测：文件名清洗拒绝 `../`、非法 jar 报错、用 `java.util.jar.JarOutputStream` 现造含 `META-INF/services/java.sql.Driver` 的最小 jar 验证注册成功
- [x] `./mvnw -q -o -pl data-ops-business/data-ops-business-datasource -am test -Dtest='*Driver*' -Dsurefire.failIfNoSpecifiedTests=false` 绿
- [x] 追加：`JdbcDriverLoader` 修复 3 处 `Class.forName` 硬失败——外置驱动经 `DriverManager.getDriver(url)` 回退可见（jdbc plugin 42/42 绿）

> 2026-09-22 落地：整模块 125/125 绿（含 Registry 3、Store 5、Manager 5、权限 3）；真机上传验证仍需用户重启后端后探活。

**验证边界：** 真机上传验证需用户重启后端（新端点探活法：404=旧构建）。
