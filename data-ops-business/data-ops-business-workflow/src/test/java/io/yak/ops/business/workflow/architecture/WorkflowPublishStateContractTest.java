package io.yak.ops.business.workflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 锁定工作流定义发布态收敛（多版本契约 C1）：定义状态只允许经
 * {@code PublishState} 读写，禁止历史字面量（ONLINE 等）回潮成第二套口径。
 *
 * <p>注意：schedule.status 的 ONLINE/OFFLINE 是“调度开关”，与发布态正交，不在本约束内。
 */
class WorkflowPublishStateContractTest {

  private static final Pattern DEFINITION_STATUS_LITERAL_ASSIGNMENT =
      Pattern.compile("state\\.status\\s*=\\s*\"");

  private static final Pattern DEFINITION_STATUS_ONLINE_COMPARISON =
      Pattern.compile("\"(?:ONLINE|OFFLINE|DRAFT|PUBLISHED)\"\\.equals\\((?:state|before|current|workflow)\\.");

  @Test
  void definitionStatusWritesGoThroughPublishStateOnly() throws IOException {
    List<String> violations = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      if (DEFINITION_STATUS_LITERAL_ASSIGNMENT.matcher(file.source()).find()) {
        violations.add(file.relativePath());
      }
      if (DEFINITION_STATUS_ONLINE_COMPARISON.matcher(file.source()).find()) {
        violations.add(file.relativePath());
      }
    }

    assertThat(violations)
        .as("Workflow definition status must be read/written via PublishState, not literals")
        .isEmpty();
  }

  @Test
  void publishStateMigrationIsRegistered() throws IOException {
    Path root = migrationRoot();
    List<String> files;
    try (Stream<Path> paths = Files.list(root)) {
      files = paths.map(path -> path.getFileName().toString()).sorted().toList();
    }

    assertThat(files)
        .containsExactlyInAnyOrder(
            "V1__baseline_workflow.sql",
            "V2__add_execution_audit_carrier.sql",
            "V3__workflow_publish_state.sql");
    assertThat(Files.readString(root.resolve("V3__workflow_publish_state.sql")))
        .contains("UPDATE yak_workflow_definition SET status = 'PUBLISHED' WHERE status = 'ONLINE'");
  }

  private List<SourceFile> productionSources() throws IOException {
    Path root = productionRoot();
    List<SourceFile> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        result.add(
            new SourceFile(
                root.relativize(file).toString().replace('\\', '/'),
                Files.readString(file)));
      }
    }
    return result;
  }

  private Path moduleRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/workflow");
    if (Files.isDirectory(local)) return Path.of(".");
    return Path.of("data-ops-business", "data-ops-business-workflow");
  }

  private Path productionRoot() {
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/workflow");
  }

  private Path migrationRoot() {
    return moduleRoot().resolve("src/main/resources/db/migration/yak-workflow");
  }

  private record SourceFile(String relativePath, String source) {}
}
