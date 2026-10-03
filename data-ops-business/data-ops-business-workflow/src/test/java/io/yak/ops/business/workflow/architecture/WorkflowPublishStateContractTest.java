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
      files = paths
          .map(path -> path.getFileName().toString())
          .filter(name -> name.startsWith("V"))
          .sorted()
          .toList();
    }

    assertThat(files)
        .containsExactlyInAnyOrder("V1__workflow_baseline.sql");
    assertThat(
            section(
                Files.readString(root.resolve("V1__workflow_baseline.sql")),
                "V3__workflow_publish_state.sql"))
        .contains("UPDATE yak_workflow_definition SET status = 'PUBLISHED' WHERE status = 'ONLINE'");
  }

  /**
   * 合并后的单文件按 {@code -- Source: <原路径>} 分段;按文件名取回原迁移的正文,
   * 使原先针对单个文件的断言继续有效。
   */
  private static String section(String sql, String sourceFileName) {
    String[] lines = sql.split("\n");
    int start = -1;
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:") && lines[i].trim().endsWith(sourceFileName)) {
        start = i + 1;
        break;
      }
    }
    if (start < 0) throw new IllegalStateException("missing source section: " + sourceFileName);
    StringBuilder body = new StringBuilder();
    for (int i = start; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:")) break;
      body.append(lines[i]).append('\n');
    }
    return body.toString();
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
