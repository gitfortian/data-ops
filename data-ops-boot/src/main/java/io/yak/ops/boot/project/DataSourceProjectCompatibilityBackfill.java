package io.yak.ops.boot.project;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Completes the DataSource Project Space cutover after Flyway has expanded the schema.
 *
 * <p>The real default Project ID must come from Yak Security rather than a migration constant, so
 * legacy rows are backfilled at application readiness. Runtime repositories are already
 * fail-closed; startup is rejected if any datasource or SQL execution audit remains unscoped.
 */
@Component
@DependsOn("opsDataSourceFlyway")
@ConditionalOnDataSourceEnabled
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DataSourceProjectCompatibilityBackfill {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DataSourceProjectCompatibilityBackfill.class);

  private final ProjectCompatibilityCoordinator projectCoordinator;
  private final JdbcTemplate jdbcTemplate;

  public DataSourceProjectCompatibilityBackfill(
      ProjectCompatibilityCoordinator projectCoordinator,
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    this.projectCoordinator = projectCoordinator;
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void backfillLegacyRows() {
    long defaultProjectId = projectCoordinator.ensureRequiredDefaultProject();

    int dataSources =
        jdbcTemplate.update(
            "UPDATE yak_ops_data_source SET project_id = ? WHERE project_id IS NULL",
            defaultProjectId);

    int inferredAudits =
        jdbcTemplate.update(
            "UPDATE yak_ops_sql_execution e "
                + "JOIN yak_ops_data_source d "
                + "  ON e.data_source_id REGEXP '^[0-9]+$' "
                + " AND d.id = CAST(e.data_source_id AS UNSIGNED) "
                + "SET e.project_id = d.project_id "
                + "WHERE e.project_id IS NULL AND d.project_id IS NOT NULL");

    int defaultAudits =
        jdbcTemplate.update(
            "UPDATE yak_ops_sql_execution SET project_id = ? WHERE project_id IS NULL",
            defaultProjectId);

    assertNoUnscopedRows("yak_ops_data_source");
    assertNoUnscopedRows("yak_ops_sql_execution");

    LOGGER.info(
        "DataSource Project Space backfill complete: defaultProjectId={}, dataSources={}, inferredAudits={}, defaultAudits={}",
        defaultProjectId,
        dataSources,
        inferredAudits,
        defaultAudits);
  }

  private void assertNoUnscopedRows(String table) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM " + table + " WHERE project_id IS NULL", Long.class);
    if (count != null && count > 0L) {
      throw new IllegalStateException(
          "DataSource Project Space cutover left " + count + " unscoped rows in " + table);
    }
  }
}
