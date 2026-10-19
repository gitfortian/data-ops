package io.yak.framework.security.service.impl;

import io.yak.framework.security.config.YakSecurityProperties;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * R14-B2 T3 development foundation: Security-owned, monotonic committed
 * authorization revisions. Must be called INSIDE an existing Security
 * transaction and BEFORE any affected authorization relationship write.
 *
 * <p>A project-scope revision covers only wired project-membership mutations.
 * Role/permission and user-role changes require the separate GLOBAL revision,
 * and all mutation sources must be integrated before it can be authoritative.
 *
 * <p>IMPORTANT: Security and Modeling use different DataSources. This row and
 * its lock DO NOT automatically protect an independent Modeling commit. No
 * release API or ModelVersion publication uses this service.
 */
@Service
@DependsOn("yakSecurityFlyway")
public class PublicationAuthorizationRevisionService {
  private static final String LOCK = """
      SELECT revision_no FROM yak_security_publication_auth_revision
      WHERE app_name = ? AND scope_kind = ? AND scope_id = ? FOR UPDATE
      """;
  private static final String INCREMENT = """
      UPDATE yak_security_publication_auth_revision
      SET revision_no = revision_no + 1, update_time = CURRENT_TIMESTAMP
      WHERE app_name = ? AND scope_kind = ? AND scope_id = ?
      """;

  private final DataSource securityDataSource;
  private final JdbcTemplate jdbc;
  private final YakSecurityProperties properties;

  public PublicationAuthorizationRevisionService(
      @Qualifier("yakSecurityDataSource") DataSource securityDataSource,
      YakSecurityProperties properties) {
    this.securityDataSource = securityDataSource;
    this.jdbc = new JdbcTemplate(securityDataSource);
    this.properties = properties;
  }

  /** Project member/owner membership and project-status changes. */
  public long advanceProjectMutation(Long projectId) {
    if (projectId == null || projectId <= 0) {
      throw new IllegalArgumentException("Security project revision requires a real project");
    }
    return advance("PROJECT", projectId);
  }

  /** Global role, permission and user-role mutations affecting any project. */
  public long advanceGlobalMutation() {
    return advance("GLOBAL", 0L);
  }

  private long advance(String kind, long id) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Security authorization revision requires an active transaction");
    }
    String app = properties.getApplicationName();
    if (!StringUtils.hasText(app) || app.length() > 64) {
      throw new IllegalStateException("Security authorization revision app scope unavailable");
    }

    Connection connection = DataSourceUtils.getConnection(securityDataSource);
    try {
      if (!DataSourceUtils.isConnectionTransactional(connection, securityDataSource)) {
        throw new IllegalStateException("Security revision must use the actual Security transaction");
      }
      String vendor = connection.getMetaData().getDatabaseProductName()
          .toLowerCase(Locale.ROOT);
      if (vendor.contains("postgresql")) {
        jdbc.update("""
            INSERT INTO yak_security_publication_auth_revision
              (app_name, scope_kind, scope_id, revision_no) VALUES (?, ?, ?, 1)
            ON CONFLICT (app_name, scope_kind, scope_id) DO NOTHING
            """, app, kind, id);
      } else if (vendor.contains("mysql") || vendor.contains("mariadb")) {
        jdbc.update("""
            INSERT INTO yak_security_publication_auth_revision
              (app_name, scope_kind, scope_id, revision_no) VALUES (?, ?, ?, 1)
            ON DUPLICATE KEY UPDATE revision_no = revision_no
            """, app, kind, id);
      } else {
        throw new IllegalStateException("Unsupported Security revision database");
      }
      Long current = jdbc.queryForObject(LOCK, Long.class, app, kind, id);
      if (current == null || current <= 0 || current == Long.MAX_VALUE
          || jdbc.update(INCREMENT, app, kind, id) != 1) {
        throw new IllegalStateException("Security authorization revision cannot advance");
      }
      return current + 1;
    } catch (SQLException error) {
      throw new IllegalStateException("Security revision JDBC transaction unavailable", error);
    } finally {
      DataSourceUtils.releaseConnection(connection, securityDataSource);
    }
  }
}
