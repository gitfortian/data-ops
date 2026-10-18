package io.yak.ops.business.semantic.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.business.semantic.dao.mapper.SemanticStandardMapper;
import io.yak.ops.business.semantic.dao.model.SemanticStandardPO;
import io.yak.ops.core.project.CurrentProject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Database-backed project-isolation regression for semantic standard reads.
 *
 * Architecture Checks supplies a disposable MySQL database through
 * ARCHITECTURE_MYSQL_URL. Outside that CI environment this test is skipped.
 * A MySQL temporary table shadows the production table on this test connection,
 * so the test neither depends on nor mutates persistent project data.
 */
class StandardRepositoryAdapterProjectIsolationIntegrationTest {
  private static final long CURRENT_PROJECT_ID = 7L;

  @Test
  void standardAndCodeSetReadsCannotObserveAnotherProjectsRows() throws Exception {
    String url = System.getenv("ARCHITECTURE_MYSQL_URL");
    Assumptions.assumeTrue(url != null && !url.isBlank(),
        "requires the disposable MySQL database configured by Architecture Checks");
    String username = System.getenv().getOrDefault("ARCHITECTURE_MYSQL_USERNAME", "root");
    String password = System.getenv().getOrDefault("ARCHITECTURE_MYSQL_PASSWORD", "");

    Connection connection = DriverManager.getConnection(url, username, password);
    SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
    try {
      createIsolatedTemporaryTable(connection);

      MybatisConfiguration configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      TableInfoHelper.initTableInfo(
          new MapperBuilderAssistant(configuration, ""),
          SemanticStandardPO.class);
      configuration.addMapper(SemanticStandardMapper.class);

      MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
      factoryBean.setDataSource(dataSource);
      factoryBean.setConfiguration(configuration);
      SqlSessionFactory factory = factoryBean.getObject();

      try (SqlSession session = factory.openSession(true)) {
        CurrentProject currentProject = mock(CurrentProject.class);
        when(currentProject.requireProjectId()).thenReturn(CURRENT_PROJECT_ID);
        StandardRepositoryAdapter repository =
            new StandardRepositoryAdapter(session.getMapper(SemanticStandardMapper.class), currentProject);

        // Direct ID lookup must not disclose a row owned by project 8.
        assertTrue(repository.findById(71002L).isEmpty());

        // Same code-set key exists in both projects. Only current-project members are returned.
        Set<Long> memberIds = repository.listByCodeSetCode("SHARED_STATUS").stream()
            .map(standard -> standard.id())
            .collect(Collectors.toSet());
        assertEquals(Set.of(71001L, 71003L), memberIds);

        // A foreign-only code set must not appear enabled to the current project.
        assertFalse(repository.existsEnabledByCodeSet("FOREIGN_ONLY"));

        // A disabled foreign member must not contaminate the current project's all-enabled set.
        assertTrue(repository.existsEnabledByCodeSet("SHARED_STATUS"));
      }
    } finally {
      dataSource.destroy();
    }
  }

  private static void createIsolatedTemporaryTable(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TEMPORARY TABLE yak_semantic_standard (
            id BIGINT NOT NULL PRIMARY KEY,
            project_id BIGINT NOT NULL,
            kind VARCHAR(32),
            std_code VARCHAR(128),
            std_name VARCHAR(255),
            status VARCHAR(32),
            version INT,
            sort_order INT,
            is_preset BOOLEAN,
            description VARCHAR(255),
            scope VARCHAR(64),
            layer VARCHAR(32),
            rule_expr TEXT,
            example TEXT,
            type_code VARCHAR(128),
            std_type VARCHAR(128),
            source_mapping TEXT,
            code_set_code VARCHAR(128),
            code_value VARCHAR(128),
            code_label VARCHAR(255),
            unit_code VARCHAR(128),
            unit_type VARCHAR(64),
            caliber_code VARCHAR(128),
            cal_rule TEXT,
            business_desc TEXT,
            level_code VARCHAR(64),
            mask_rule TEXT,
            created_by VARCHAR(128),
            create_time DATETIME,
            update_time DATETIME
          )
          """);
      statement.execute("""
          INSERT INTO yak_semantic_standard
            (id, project_id, kind, std_code, std_name, status, version,
             sort_order, is_preset, code_set_code, code_value, code_label)
          VALUES
            (71001, 7, 'CODE', 'SHARED_STATUS_CREATED', 'Created', 'ENABLED', 1,
             1, FALSE, 'SHARED_STATUS', 'CREATED', 'Created'),
            (71002, 8, 'CODE', 'SHARED_STATUS_CANCELLED', 'Cancelled', 'DISABLED', 1,
             2, FALSE, 'SHARED_STATUS', 'CANCELLED', 'Cancelled'),
            (71003, 7, 'CODE', 'SHARED_STATUS_PAID', 'Paid', 'ENABLED', 1,
             3, FALSE, 'SHARED_STATUS', 'PAID', 'Paid'),
            (71004, 8, 'CODE', 'FOREIGN_ONLY_ACTIVE', 'Foreign only', 'ENABLED', 1,
             1, FALSE, 'FOREIGN_ONLY', 'ACTIVE', 'Active')
          """);
    }
  }
}
