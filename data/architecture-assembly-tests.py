from pathlib import Path
p=Path('data-ops-boot/src/test/java/io/yak/ops/boot/architecture/PluginContractClasspathTest.java')
p.write_text('''package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Plugin signatures must resolve without Boot, business, Spring or ORM on the classpath. */
class PluginContractClasspathTest {
  @Test
  void publicPluginContractsLoadOnTheirMinimalClasspath() throws Exception {
    List<URL> urls = new ArrayList<>();
    List<Path> contractRoots = new ArrayList<>();
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    for (String entry : classpath.split(Pattern.quote(System.getProperty("path.separator")))) {
      Path path = Path.of(entry);
      String normalized = entry.replace('\\\\', '/');
      boolean contract = normalized.endsWith("/target/classes") &&
          (normalized.contains("/data-ops-spi/") || normalized.matches(".*/data-ops-plugin-[^/]+-api/target/classes"));
      boolean support = normalized.endsWith("/target/classes") &&
          (normalized.contains("/data-ops-common/") || normalized.contains("/data-common/") || normalized.contains("/data-schedule-api/"));
      boolean external = normalized.endsWith(".jar") && (normalized.contains("/com/fasterxml/jackson/") ||
          normalized.contains("/org/slf4j/") || normalized.contains("/jakarta/validation/"));
      if (contract || support || external) urls.add(path.toUri().toURL());
      if (contract) contractRoots.add(path);
    }
    assertThat(contractRoots).hasSize(5);
    try (URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
      assertThatThrownBy(() -> loader.loadClass("org.springframework.transaction.PlatformTransactionManager"))
          .isInstanceOf(ClassNotFoundException.class);
      assertThatThrownBy(() -> loader.loadClass("com.baomidou.mybatisplus.core.mapper.BaseMapper"))
          .isInstanceOf(ClassNotFoundException.class);
      for (Path root : contractRoots) {
        try (var classes = Files.walk(root)) {
          for (Path file : classes.filter(path -> path.toString().endsWith(".class")).toList()) {
            String name = root.relativize(file).toString().replace('\\\\', '.').replace('/', '.').replaceAll("\\\\.class$", "");
            Class<?> type = Class.forName(name, false, loader);
            // Resolving generic types catches leaks that simply loading the class would miss.
            for (var method : type.getDeclaredMethods()) {
              method.getGenericReturnType().getTypeName();
              for (var parameter : method.getGenericParameterTypes()) parameter.getTypeName();
            }
            for (var field : type.getDeclaredFields()) field.getGenericType().getTypeName();
            for (var constructor : type.getDeclaredConstructors()) constructor.getGenericParameterTypes();
          }
        }
      }
    }
  }
}
''',encoding='utf-8')
p=Path('data-ops-boot/src/test/java/io/yak/ops/boot/architecture/DatabaseMigrationSmokeTest.java')
p.write_text('''package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.boot.YakOpsApplication;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;

/** CI owns an isolated MySQL schema; never run this against a user's existing database. */
@EnabledIfEnvironmentVariable(named = "ARCHITECTURE_MYSQL_URL", matches = ".+")
class DatabaseMigrationSmokeTest {
  @Test
  void emptyDatabaseThenCurrentBaselineRestartAndDatasourceDisabledAssembly() {
    startAndValidate(true);
    startAndValidate(false);
  }

  private void startAndValidate(boolean datasourceEnabled) {
    Map<String, Object> properties = Map.ofEntries(
        Map.entry("server.port", "0"),
        Map.entry("yak.database.enabled", "true"),
        Map.entry("yak.database.url", System.getenv("ARCHITECTURE_MYSQL_URL")),
        Map.entry("yak.database.username", System.getenv().getOrDefault("ARCHITECTURE_MYSQL_USERNAME", "root")),
        Map.entry("yak.database.password", System.getenv().getOrDefault("ARCHITECTURE_MYSQL_PASSWORD", "")),
        Map.entry("yak.datasource.enabled", String.valueOf(datasourceEnabled)),
        Map.entry("yak.security.database-enabled", "true"),
        Map.entry("yak.security.permission-registration.enabled", "false"),
        Map.entry("yak.security.bootstrap.enabled", "false"),
        Map.entry("spring.quartz.auto-startup", "false"));
    try (var context = new SpringApplicationBuilder(YakOpsApplication.class)
        .initializers(application -> application.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("isolated-migration-smoke", properties))).run()) {
      assertThat(context.getBean("yakBusinessDataSource", DataSource.class)).isNotNull();
      assertThat(context.getBean("opsDataSource")).isSameAs(context.getBean("yakBusinessDataSource"));
      Map<String, Flyway> migrations = context.getBeansOfType(Flyway.class);
      assertThat(migrations).isNotEmpty();
      migrations.values().forEach(Flyway::validate);
    }
  }
}
''',encoding='utf-8')
p=Path('.github/workflows/architecture-checks.yml');s=p.read_text(encoding='utf-8');s=s.replace('    timeout-minutes: 60\n    steps:', '''    timeout-minutes: 60
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: architecture-ci
          MYSQL_DATABASE: architecture_ci
        ports: ['3306:3306']
        options: >-
          --health-cmd="mysqladmin ping -h 127.0.0.1 -parchitecture-ci"
          --health-interval=10s --health-timeout=5s --health-retries=12
    env:
      ARCHITECTURE_MYSQL_URL: jdbc:mysql://127.0.0.1:3306/architecture_ci?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
      ARCHITECTURE_MYSQL_USERNAME: root
      ARCHITECTURE_MYSQL_PASSWORD: architecture-ci
    steps:''',1);p.write_text(s,encoding='utf-8')
