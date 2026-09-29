# Datasource Migration Contract

Datasource owns an independent Flyway namespace:

```text
location: classpath:db/migration/yak-datasource
history:  yak_datasource_schema_history
```

Current first-release baseline:

```text
src/main/resources/db/migration/yak-datasource/
└── V1__baseline_datasource.sql
```

The baseline directly creates the current final Datasource-owned schema:

- `yak_ops_data_source` including Project ownership/indexes;
- `yak_ops_sql_execution`;
- `yak_ops_sql_statement_execution`.

The previous development-time `V1/V2/V8/V11` chain has been squashed because Yak Ops is still in its first release and the current development database is disposable.

After the first formal release:

1. new Datasource changes use `V2`, `V3`, ... in this directory;
2. released migrations are immutable;
3. Data Service migrations must never be placed in this namespace;
4. Data Service owns `db/migration/yak-data-service` and `yak_data_service_schema_history` independently.

For the one-time local database reset required by this squash, see the Data Service `MIGRATIONS.md`, which lists both Datasource and Data Service development tables/history tables to clear before restarting.
