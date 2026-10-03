package io.yak.ops.boot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class BusinessDatabaseConfigurationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(BusinessDatabaseConfiguration.class)
      .withPropertyValues("yak.datasource.enabled=false", "yak.database.url=jdbc:h2:mem:assembly;DB_CLOSE_DELAY=-1",
          "yak.database.driver-class-name=org.h2.Driver", "yak.database.username=sa", "yak.database.password=");

  @Test
  void sharedPersistenceSurvivesDatasourceFeatureDisableAndPreservesAliasesAndRollback() {
    runner.run(context -> {
      assertThat(context).hasNotFailed().hasSingleBean(DataSource.class).hasSingleBean(PlatformTransactionManager.class);
      DataSource datasource = context.getBean("yakBusinessDataSource", DataSource.class);
      assertThat(context.getBean("opsDataSource")).isSameAs(datasource);
      assertThat(context.getBean("opsResourceDataSource")).isSameAs(datasource);
      assertThat(context.getBean("offlineSyncDataSource")).isSameAs(datasource);
      SqlSessionFactory sessions = context.getBean(SqlSessionFactory.class);
      for (String name : new String[] {"opsDataSourceSqlSessionFactory", "opsResourceSqlSessionFactory", "offlineSyncSqlSessionFactory"})
        assertThat(context.getBean(name)).isSameAs(sessions);
      for (String name : new String[] {"opsDataSourceTransactionManager", "opsResourceTransactionManager", "offlineSyncTransactionManager"})
        assertThat(context.getBean(name)).isSameAs(context.getBean(PlatformTransactionManager.class));
      Object template = context.getBean("yakBusinessSqlSessionTemplate");
      for (String name : new String[] {"opsDataSourceSqlSessionTemplate", "opsResourceSqlSessionTemplate", "offlineSyncSqlSessionTemplate"})
        assertThat(context.getBean(name)).isSameAs(template);
      assertThat(sessions.getConfiguration().getTypeAliasRegistry().resolveAlias("DataSourcePO").getPackageName())
          .isEqualTo("io.yak.ops.business.datasource.dao.model");
      assertThat(sessions.getConfiguration().getTypeAliasRegistry().resolveAlias("SystemEnvVarPO").getPackageName())
          .isEqualTo("io.yak.ops.business.job.dao.model");
      JdbcTemplate jdbc = new JdbcTemplate(datasource);
      jdbc.execute("CREATE TABLE IF NOT EXISTS contract_probe (id INT)");
      new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
        jdbc.update("INSERT INTO contract_probe VALUES (1)");
        status.setRollbackOnly();
      });
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contract_probe", Integer.class)).isZero();
    });
  }

  @Test
  void sharedDatabaseCanBeExplicitlyDisabled() {
    runner.withPropertyValues("yak.database.enabled=false").run(context ->
        assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class).doesNotHaveBean(SqlSessionFactory.class));
  }
}
