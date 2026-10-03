package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.jar.JarFile;
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
      String normalized = entry.replace('\\', '/');
      boolean contractJar = normalized.matches(".*/(?:data-ops-spi|data-ops-plugin-[^/]+-api)-[0-9][^/]*\\.jar");
      boolean supportJar = normalized.matches(".*/(?:data-ops-common|data-common|data-schedule-api)-[0-9][^/]*\\.jar");
      boolean contract = contractJar || normalized.endsWith("/target/classes") &&
          (normalized.contains("/data-ops-spi/") || normalized.matches(".*/data-ops-plugin-[^/]+-api/target/classes"));
      boolean support = supportJar || normalized.endsWith("/target/classes") &&
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
        List<String> names;
        if (Files.isDirectory(root)) {
          try (var classes = Files.walk(root)) {
            names = classes.filter(path -> path.toString().endsWith(".class"))
                .map(path -> root.relativize(path).toString().replace('\\', '/')).toList();
          }
        } else {
          try (JarFile jar = new JarFile(root.toFile())) {
            names = jar.stream().map(entry -> entry.getName()).filter(name -> name.endsWith(".class")).toList();
          }
        }
        for (String entry : names) {
          String name = entry.replace('/', '.').replaceAll("\\.class$", "");
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
