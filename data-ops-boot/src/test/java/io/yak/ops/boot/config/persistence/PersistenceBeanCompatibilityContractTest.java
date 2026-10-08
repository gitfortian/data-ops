package io.yak.ops.boot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Architecture A2.2: protects the shared persistence contract for consumers.
 * This test runs against main's existing assembly and remains valid when A2's
 * session configuration split is later merged independently.
 */
class PersistenceBeanCompatibilityContractTest {

  private ApplicationContextRunner context() {
    return new ApplicationContextRunner()
        .withUserConfiguration(BusinessDatabaseConfiguration.class)
        .withPropertyValues(
            "yak.database.url=jdbc:h2:mem:a22contract;DB_CLOSE_DELAY=-1",
            "yak.database.driver-class-name=org.h2.Driver",
            "yak.database.username=sa",
            "yak.database.password=",
            "yak.datasource.enabled=false");
  }

  @Test
  void allHistoricalAliasGroupsResolveToOneAndTheSamePrimaryBean() {
    context().run(ctx -> {
      assertThat(ctx).hasNotFailed();
      assertThat(ctx).hasSingleBean(DataSource.class)
          .hasSingleBean(PlatformTransactionManager.class)
          .hasSingleBean(SqlSessionFactory.class)
          .hasSingleBean(SqlSessionTemplate.class);

      Object source = ctx.getBean("yakBusinessDataSource");
      for (String alias : new String[] {
          "opsDataSource", "opsResourceDataSource", "offlineSyncDataSource"}) {
        assertThat(ctx.getBean(alias)).as(alias).isSameAs(source);
      }
      Object transaction = ctx.getBean("yakBusinessTransactionManager");
      for (String alias : new String[] {
          "opsDataSourceTransactionManager", "opsResourceTransactionManager",
          "offlineSyncTransactionManager"}) {
        assertThat(ctx.getBean(alias)).as(alias).isSameAs(transaction);
      }
      Object factory = ctx.getBean("yakBusinessSqlSessionFactory");
      for (String alias : new String[] {
          "opsDataSourceSqlSessionFactory", "opsResourceSqlSessionFactory",
          "offlineSyncSqlSessionFactory"}) {
        assertThat(ctx.getBean(alias)).as(alias).isSameAs(factory);
      }
      Object template = ctx.getBean("yakBusinessSqlSessionTemplate");
      for (String alias : new String[] {
          "opsDataSourceSqlSessionTemplate", "opsResourceSqlSessionTemplate",
          "offlineSyncSqlSessionTemplate"}) {
        assertThat(ctx.getBean(alias)).as(alias).isSameAs(template);
      }

      assertThat(ctx.getBean(DataSource.class)).isSameAs(source);
      assertThat(ctx.getBean(PlatformTransactionManager.class)).isSameAs(transaction);
      assertThat(ctx.getBean(SqlSessionFactory.class)).isSameAs(factory);
      assertThat(ctx.getBean(SqlSessionTemplate.class)).isSameAs(template);
      assertThat(((DataSourceTransactionManager) transaction).getDataSource()).isSameAs(source);
      assertThat(((SqlSessionTemplate) template).getSqlSessionFactory()).isSameAs(factory);
    });
  }

  @Test
  void mybatisBehaviorAndDatasourcePoolContractStayCompatible() {
    context().run(ctx -> {
      assertThat(ctx).hasNotFailed();
      HikariDataSource source = ctx.getBean("yakBusinessDataSource", HikariDataSource.class);
      assertThat(source.isAutoCommit()).isTrue();
      assertThat(source.getPoolName()).isEqualTo("YakBusinessDatabasePool");

      var configuration = ctx.getBean(SqlSessionFactory.class).getConfiguration();
      assertThat(configuration.isMapUnderscoreToCamelCase()).isTrue();
      assertThat(configuration.getJdbcTypeForNull()).isEqualTo(JdbcType.NULL);
      assertThat(configuration.isCacheEnabled()).isFalse();
      assertThat(configuration.getInterceptors()).hasSize(1);
      assertThat(configuration.getInterceptors().get(0)).isInstanceOf(MybatisPlusInterceptor.class);
      var interceptor = (MybatisPlusInterceptor) configuration.getInterceptors().get(0);
      assertThat(interceptor.getInterceptors()).hasSize(1);
      assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(PaginationInnerInterceptor.class);
    });
  }

  @Test
  void disablingSharedBusinessDatabaseExcludesAllFourBeanFamilies() {
    context().withPropertyValues("yak.database.enabled=false").run(ctx -> {
      assertThat(ctx).hasNotFailed();
      for (String bean : new String[] {
          "yakBusinessDataSource", "yakBusinessTransactionManager",
          "yakBusinessSqlSessionFactory", "yakBusinessSqlSessionTemplate"}) {
        assertThat(ctx.containsBean(bean)).as(bean).isFalse();
      }
    });
  }
}
