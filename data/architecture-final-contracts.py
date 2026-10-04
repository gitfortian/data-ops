from pathlib import Path
p=Path('data-ops-boot/src/test/java/io/yak/ops/boot/architecture/DatabaseMigrationSmokeTest.java');s=p.read_text(encoding='utf-8').replace('emptyDatabaseThenCurrentBaselineRestartAndDatasourceDisabledAssembly','emptyDatabaseThenCurrentBaselineRestart').replace('    startAndValidate(false);','    startAndValidate(true);');p.write_text(s,encoding='utf-8')
p=Path('data-ops-boot/src/test/java/io/yak/ops/boot/config/persistence/BusinessDatabaseConfigurationTest.java');s=p.read_text(encoding='utf-8').replace('      SqlSessionFactory sessions = context.getBean(SqlSessionFactory.class);','''      SqlSessionFactory sessions = context.getBean(SqlSessionFactory.class);
      for (String name : new String[] {"opsDataSourceSqlSessionFactory", "opsResourceSqlSessionFactory", "offlineSyncSqlSessionFactory"})
        assertThat(context.getBean(name)).isSameAs(sessions);
      for (String name : new String[] {"opsDataSourceTransactionManager", "opsResourceTransactionManager", "offlineSyncTransactionManager"})
        assertThat(context.getBean(name)).isSameAs(context.getBean(PlatformTransactionManager.class));
      Object template = context.getBean("yakBusinessSqlSessionTemplate");
      for (String name : new String[] {"opsDataSourceSqlSessionTemplate", "opsResourceSqlSessionTemplate", "offlineSyncSqlSessionTemplate"})
        assertThat(context.getBean(name)).isSameAs(template);''');p.write_text(s,encoding='utf-8')
p=Path('data-ops-business/data-ops-business-job/src/main/java/io/yak/ops/business/job/runtime/AbstractTaskExecutorAdapter.java');s=p.read_text(encoding='utf-8').replace('    requireSnapshot(snapshot);\n\n    TaskExecutionTrigger','    requireSnapshot(snapshot);\n    contextFactory.projectIdentity(); // Fail closed before allocating plugin resources.\n\n    TaskExecutionTrigger');p.write_text(s,encoding='utf-8')
