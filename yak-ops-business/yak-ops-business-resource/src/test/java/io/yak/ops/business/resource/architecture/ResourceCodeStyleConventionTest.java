package io.yak.ops.business.resource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Enforces repository-wide code-style rules that are architecture-significant for Resource. */
class ResourceCodeStyleConventionTest {

  private static final Pattern WILDCARD_IMPORT =
      Pattern.compile("(?m)^import\\s+(?:static\\s+)?[^;]+\\*;");
  private static final Pattern FIELD_INJECTION = Pattern.compile(
      "(?m)@(Autowired|Resource|Inject)\\s*\\R\\s*"
          + "(?:private|protected|public)\\s+[^\\n(]+;");
  private static final Pattern SERVICE_STEREOTYPE =
      Pattern.compile("(?m)^import\\s+org\\.springframework\\.stereotype\\.Service;|@Service\\b");

  @Test
  void productionSourceFollowsRepositoryCodeStyle() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = normalize(root.relativize(file));
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(WILDCARD_IMPORT.matcher(source).find())
            .as("%s must not use wildcard imports", relative)
            .isFalse();
        assertThat(FIELD_INJECTION.matcher(source).find())
            .as("%s must use constructor injection", relative)
            .isFalse();
        assertThat(source)
            .as("%s must not use low-signal implementation shortcuts", relative)
            .doesNotContain("System.out.", "System.err.", "BeanUtils.copyProperties");
      }
    }
  }

  @Test
  void resourceDoesNotUseGenericServiceStereotype() throws IOException {
    try (Stream<Path> files = Files.walk(productionRoot())) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(SERVICE_STEREOTYPE.matcher(source).find())
            .as("Resource roles use explicit package/class vocabulary instead of @Service: %s", file)
            .isFalse();
      }
    }
  }

  @Test
  void coreDomainRemainsFrameworkAndTransportFree() throws IOException {
    Path domain = productionRoot().resolve("domain");
    try (Stream<Path> files = Files.walk(domain)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(source)
            .as("Resource Domain must remain transport/framework free: %s", file)
            .doesNotContain(
                "org.springframework.",
                "com.baomidou.mybatisplus.",
                "jakarta.servlet.",
                "io.yak.ops.common.bean.dto.",
                "io.yak.ops.common.bean.vo.",
                "io.yak.ops.common.bean.po.",
                "io.yak.ops.spi.storage.StorageOperator",
                "lombok.Data",
                "@Data");
      }
    }
  }

  @Test
  void storageOperatorOnlyAppearsInsideStorageBoundary() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() > 0 && relative.getName(0).toString().equals("storage")) {
          continue;
        }
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(source)
            .as("StorageOperator SPI may only be translated in Resource storage boundary: %s", relative)
            .doesNotContain(
                "io.yak.ops.spi.storage.StorageOperator",
                "io.yak.ops.spi.storage.StoragePluginException");
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    Path root = productionRoot();
    for (String forbidden :
        List.of("service", "common", "helper", "utils", "util", "base", "persistence")) {
      assertThat(Files.exists(root.resolve(forbidden)))
          .as("Broad Resource business bucket '%s' must not exist", forbidden)
          .isFalse();
    }
  }

  @Test
  void repositoryUsesSingleRootCodeStyleDocument() throws IOException {
    Path module = moduleRoot();
    Path repository = module.getParent().getParent();
    assertThat(Files.isRegularFile(repository.resolve("CODE_STYLE.md")))
        .as("Yak Ops root CODE_STYLE.md must exist")
        .isTrue();
    assertThat(Files.exists(module.resolve("CODE_STYLE.md")))
        .as("Resource must not maintain a module-local CODE_STYLE.md")
        .isFalse();

    long count;
    try (Stream<Path> paths = Files.walk(repository, 6)) {
      count = paths.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().equals("CODE_STYLE.md"))
          .count();
    }
    assertThat(count).as("Yak Ops must keep one repository-wide CODE_STYLE.md").isEqualTo(1L);
  }

  private Path productionRoot() {
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/resource");
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/resource"))) {
      return local;
    }
    Path repositoryRelative = Path.of("yak-ops-business", "yak-ops-business-resource")
        .toAbsolutePath()
        .normalize();
    assertThat(Files.isDirectory(
            repositoryRelative.resolve("src/main/java/io/yak/ops/business/resource")))
        .as("Unable to locate Resource module root from %s", local)
        .isTrue();
    return repositoryRelative;
  }

  private String normalize(Path path) {
    return path.toString().replace(java.io.File.separatorChar, '/');
  }
}
