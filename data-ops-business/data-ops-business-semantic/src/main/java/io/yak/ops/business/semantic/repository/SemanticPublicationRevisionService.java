package io.yak.ops.business.semantic.repository;

import io.yak.ops.core.project.CurrentProject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * R14-B2 T1 development implementation: advance the durable, project-scoped
 * semantic publication revision before obtaining any individual standard/field
 * row lock or performing relevant writes.
 *
 * <p>Requires the actual yakBusinessDataSource to participate in an existing
 * business transaction. The project ID comes exclusively from CurrentProject.
 * No user-supplied revision, project ID or approval token is accepted.
 *
 * <p>NOT a release fence: until every Semantic mutation source and a consistent
 * Modeling/authorization commit protocol is tested, the published revision
 * table MUST NOT be interpreted as an authoritative production publication
 * guarantee. This bean is not a publish endpoint.
 */
@Component
public class SemanticPublicationRevisionService {
  private static final String LOCK = """
      SELECT revision_no FROM yak_semantic_publication_revision
       WHERE project_id = ? FOR UPDATE
      """;
  private static final String ADVANCE = """
      UPDATE yak_semantic_publication_revision
         SET revision_no = revision_no + 1, update_time = CURRENT_TIMESTAMP
       WHERE project_id = ?
      """;

  private final DataSource businessDataSource;
  private final CurrentProject currentProject;
  private final JdbcTemplate jdbc;

  public SemanticPublicationRevisionService(
      @Qualifier("yakBusinessDataSource") DataSource businessDataSource,
      CurrentProject currentProject) {
    this.businessDataSource = businessDataSource;
    this.currentProject = currentProject;
    this.jdbc = new JdbcTemplate(businessDataSource);
  }

  /**
   * Lock project revision first, then advance within the caller's transaction.
   * Rollbacks discard the increment along with the corresponding business edit.
   */
  public long advanceBeforeMutation() {
    Long projectId = currentProject.requireProjectId();
    if (projectId == null || projectId <= 0 || !TransactionSynchronizationManager
        .isActualTransactionActive()) {
      throw new IllegalStateException("Semantic 修订必须在可信项目的业务事务中更新");
    }
    Connection connection = DataSourceUtils.getConnection(businessDataSource);
    try {
      if (!DataSourceUtils.isConnectionTransactional(connection, businessDataSource)) {
        throw new IllegalStateException("Semantic 修订未绑定实际业务数据源事务");
      }
      String vendor = connection.getMetaData().getDatabaseProductName()
          .toLowerCase(Locale.ROOT);
      if (vendor.contains("postgresql")) {
        jdbc.update("""
            INSERT INTO yak_semantic_publication_revision (project_id, revision_no)
            VALUES (?, 1) ON CONFLICT (project_id) DO NOTHING
            """, projectId);
      } else if (vendor.contains("mysql")) {
        jdbc.update("""
            INSERT INTO yak_semantic_publication_revision (project_id, revision_no)
            VALUES (?, 1) ON DUPLICATE KEY UPDATE revision_no = revision_no
            """, projectId);
      } else {
        throw new IllegalStateException("不支持的 Semantic 修订数据库方言");
      }
      Long current = jdbc.queryForObject(LOCK, Long.class, projectId);
      if (current == null || current <= 0 || current == Long.MAX_VALUE
          || jdbc.update(ADVANCE, projectId) != 1) {
        throw new IllegalStateException("Semantic 修订行不可用或无法推进");
      }
      return current + 1;
    } catch (SQLException failure) {
      throw new IllegalStateException("无法验证 Semantic 业务事务连接", failure);
    } finally {
      DataSourceUtils.releaseConnection(connection, businessDataSource);
    }
  }
}
