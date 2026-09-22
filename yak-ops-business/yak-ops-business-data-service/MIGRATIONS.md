# Data Service Migration Contract

Data Service 和 Datasource 使用完全独立的 Flyway namespace。`V1` 是两个模块各自的第一版完整 baseline；从 Data Service IP Access Policy 开始，Data Service schema 通过独立的增量版本继续演进，不再改写已经进入 `main` 的 V1 checksum。

## 1. Namespace ownership

```text
Datasource
  location: classpath:db/migration/yak-datasource
  history:  yak_datasource_schema_history

Data Service
  location: classpath:db/migration/yak-data-service
  history:  yak_data_service_schema_history
```

固定规则：

- Datasource migration 只能存在于 `yak-ops-business-datasource/src/main/resources/db/migration/yak-datasource`；
- Data Service migration 只能存在于 `yak-ops-business-data-service/src/main/resources/db/migration/yak-data-service`；
- Data Service 模块禁止再创建 `db/migration/yak-datasource`；
- 两个模块拥有独立 version sequence，不共享版本号和 history table。

## 2. Baseline 与当前版本

当前 Data Service migration：

```text
yak-ops-business-data-service
└── db/migration/yak-data-service
    ├── V1__baseline_data_service.sql
    └── V2__ip_access_policy.sql
```

`V1__baseline_data_service.sql` 创建首版完整结构，包括：

- Data Service API definition / source revision / Project ownership；
- API Key / auth mode / rate-limit policy；
- pagination / local cache / circuit policy / runtime generation；
- API documentation；
- invocation audit / Project-scoped indexes；
- cluster rate-limit minute window；
- hourly invocation rollup。

`V2__ip_access_policy.sql` 只新增 Data Service 自有的来源访问控制结构：

- `yak_ops_data_service_ip_access_policy`：每个 API 的 `NONE / ALLOWLIST / DENYLIST` 当前模式；
- `yak_ops_data_service_ip_access_rule`：规范化 IP/CIDR、名单类型、enabled、expiresAt、description。

V2 不回写、不 ALTER V1 业务字段，也不借用 Datasource migration namespace。

## 3. Flyway ordering

`DataServiceFlywayConfiguration` 必须：

```text
@DependsOn("opsDataSourceFlyway")
```

启动顺序：

```text
Datasource V1
    ↓
Data Service V1
    ↓
Data Service V2
```

这是初始化顺序，不代表两个模块共享 schema history。

## 4. Development database reset

如果本地数据库来自 V1 baseline squash 之前的旧开发链路，仍建议一次性清理旧开发 schema history 后重建。当前完整 reset 顺序：

```sql
DROP TABLE IF EXISTS yak_ops_data_service_ip_access_rule;
DROP TABLE IF EXISTS yak_ops_data_service_ip_access_policy;
DROP TABLE IF EXISTS yak_ops_data_service_call_log_hourly;
DROP TABLE IF EXISTS yak_ops_data_service_rate_window;
DROP TABLE IF EXISTS yak_ops_data_service_documentation;
DROP TABLE IF EXISTS yak_ops_data_service_api_key;
DROP TABLE IF EXISTS yak_ops_data_service_call_log;
DROP TABLE IF EXISTS yak_ops_data_service_api;

DROP TABLE IF EXISTS yak_ops_sql_statement_execution;
DROP TABLE IF EXISTS yak_ops_sql_execution;
DROP TABLE IF EXISTS yak_ops_data_source;

DROP TABLE IF EXISTS yak_ds_schema_history;
DROP TABLE IF EXISTS yak_datasource_schema_history;
DROP TABLE IF EXISTS yak_data_service_schema_history;
```

然后重新启动 Yak Ops，让两个 namespace 从各自 V1 开始按版本顺序创建当前结构。

> Reset 只用于历史开发数据库迁移到稳定 baseline。已经运行 V1 的数据库不需要为了 IP Access 重置；Flyway 会直接执行 Data Service V2。

## 5. 后续版本规则

1. Datasource 后续继续使用其自己的 `V2`, `V3`, ...；
2. Data Service 下一个 schema 变更从 `yak-data-service/V3` 开始；
3. 已进入 `main` 的 migration 不改名、不改内容、不改 checksum；
4. 不允许再次跨模块共享 migration location/history table；
5. schema ownership 变化必须先更新本文件和对应 Flyway contract test。
