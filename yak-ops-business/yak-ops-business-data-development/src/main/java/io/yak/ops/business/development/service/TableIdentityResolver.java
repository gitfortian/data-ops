package io.yak.ops.business.development.service;

import io.yak.ops.common.util.metadata.PhysicalTableAssetKey;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Resolves SQL table references into stable physical identities.
 *
 * <p>The resolver centralizes database/schema/default context handling so table-level
 * lineage, column lineage and future metadata sync do not create conflicting assets.
 */
@Component
public class TableIdentityResolver {

  public PhysicalTableIdentity resolve(
      SqlTableLineageParser.TableRef table,
      ResolutionContext context) {
    if (table == null) {
      throw new IllegalArgumentException("table 不能为空");
    }

    ResolutionContext actual = context == null ? ResolutionContext.empty() : context;

    boolean unqualified = table.databaseName() == null && table.schemaName() == null;
    String database = table.databaseName();
    String schema = table.schemaName();
    if (unqualified) {
      database = actual.databaseName();
      schema = actual.schemaName();
    } else if (database == null && schema != null && actual.dialect() == SqlDialect.MYSQL) {
      // JSQLParser exposes a two-part name as schema.table. Its SQL meaning is dialect-specific:
      // PostgreSQL keeps that interpretation, while MySQL treats the first part as database.
      database = schema;
      schema = null;
    } else if (database == null) {
      // A postgres two-part name omits only the database; leaving it empty would split the same
      // physical table into a second asset next to its unqualified spelling.
      database = actual.databaseName();
    }

    return new PhysicalTableIdentity(
        normalize(actual.dataSourceId()),
        normalize(database),
        normalize(schema),
        normalize(table.tableName()));
  }

  private String normalize(String value) {
    return value == null || value.isBlank()
        ? ""
        : value.trim().toLowerCase(Locale.ROOT);
  }

  public record ResolutionContext(
      String dataSourceId,
      String databaseName,
      String schemaName,
      SqlDialect dialect) {

    public ResolutionContext(String dataSourceId, String databaseName, String schemaName) {
      this(dataSourceId, databaseName, schemaName, SqlDialect.UNKNOWN);
    }

    public static ResolutionContext empty() {
      return new ResolutionContext(null, null, null, SqlDialect.UNKNOWN);
    }
  }

  /**
   * Lineage only needs SQL name-resolution families. Canonical task dialect parsing lives in the
   * shared SPI model so authoring, runtime and lineage do not maintain separate alias rules.
   */
  public enum SqlDialect {
    POSTGRESQL,
    MYSQL,
    UNKNOWN;

    public static SqlDialect from(String value) {
      final io.yak.ops.spi.task.model.SqlDialect dialect;
      try {
        dialect = io.yak.ops.spi.task.model.SqlDialect.parseOrGeneric(value);
      } catch (IllegalArgumentException ignored) {
        return UNKNOWN;
      }
      if (dialect.isPostgresFamily()) return POSTGRESQL;
      if (dialect.isMysqlFamily()) return MYSQL;
      return UNKNOWN;
    }
  }

  public record PhysicalTableIdentity(
      String dataSourceId,
      String databaseName,
      String schemaName,
      String tableName) {

    /** 键的拼法住在 common（元数据与血缘共用一把，见 {@link PhysicalTableAssetKey}）。 */
    public String assetKey() {
      return PhysicalTableAssetKey.of(dataSourceId, databaseName, schemaName, tableName);
    }
  }
}
