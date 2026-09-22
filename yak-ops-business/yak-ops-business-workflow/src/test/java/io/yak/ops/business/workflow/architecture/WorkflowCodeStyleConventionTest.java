package io.yak.ops.business.workflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Enforces low-ambiguity repository-wide CODE_STYLE rules for Workflow production code. */
class WorkflowCodeStyleConventionTest {

  private static final Pattern WILDCARD_IMPORT =
      Pattern.compile("(?m)^import\\s+(?:static\\s+)?[^;]+\\*;");
  private static final Pattern FIELD_INJECTION = Pattern.compile(
      "(?m)@(Autowired|Resource|Inject)\\s*\\R\\s*"
          + "(?:private|protected|public)\\s+[^\\n(]+;");
  private static final Pattern SERVICE_CLASS = Pattern.compile(
      "(?s)@Service\\s*(?:\\R|@[^\\R]+\\R)*public\\s+class\\s+([A-Za-z0-9_]+)");
  private static final Set<String> STABLE_SERVICE_FACADES = Set.of(
      "WorkflowDefinitionManager",
      "WorkflowLauncher",
      "WorkflowExecutionManager",
      "WorkflowExecutionReactivator",
      "WorkflowRuntime",
      "WorkflowBackfillManager");

  @Test
  void productionSourceFollowsRepositoryCodeStyle() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = root.relativize(file).toString();
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(WILDCARD_IMPORT.matcher(source).find())
            .as("%s must not use wildcard imports", relative)
            .isFalse();
        assertThat(FIELD_INJECTION.matcher(source).find())
            .as("%s must use constructor injection", relative)
            .isFalse();
        assertThat(source)
            .as("%s must not use low-signal utility shortcuts", relative)
            .doesNotContain("System.out.", "System.err.", "BeanUtils.copyProperties");
      }
    }
  }

  @Test
  void serviceStereotypeIsReservedForStableFacades() throws IOException {
    Path root = productionRoot();
    Set<String> found = new HashSet<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Matcher matcher = SERVICE_CLASS.matcher(source);
        if (!matcher.find()) continue;
        String className = matcher.group(1);
        found.add(className);
        assertThat(STABLE_SERVICE_FACADES)
            .as("@Service must represent a stable Workflow facade: %s", file)
            .contains(className);
      }
    }
    assertThat(found).containsExactlyInAnyOrderElementsOf(STABLE_SERVICE_FACADES);
  }

  @Test
  void coreDomainRemainsFrameworkFree() throws IOException {
    Path domain = productionRoot().resolve("domain");
    try (Stream<Path> files = Files.walk(domain)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(source)
            .as("Workflow Domain must remain framework-free: %s", file)
            .doesNotContain(
                "org.springframework.",
                "com.baomidou.mybatisplus.",
                "io.yak.ops.common.bean.dto.",
                "io.yak.ops.common.bean.vo.",
                "io.yak.ops.common.bean.po.",
                "lombok.Data",
                "@Data");
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    Path root = productionRoot();
    for (String forbidden :
        List.of("service", "common", "helper", "utils", "util", "base", "persistence")) {
      assertThat(Files.exists(root.resolve(forbidden)))
          .as("Broad Workflow business bucket '%s' must not exist", forbidden)
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
        .as("Workflow must not maintain a module-local CODE_STYLE.md")
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
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/workflow");
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/workflow"))) {
      return local;
    }
    Path repositoryRelative = Path.of("yak-ops-business", "yak-ops-business-workflow")
        .toAbsolutePath()
        .normalize();
    assertThat(Files.isDirectory(
            repositoryRelative.resolve("src/main/java/io/yak/ops/business/workflow")))
        .as("Unable to locate Workflow module root from %s", local)
        .isTrue();
    return repositoryRelative;
  }
}
