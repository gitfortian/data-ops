package io.yak.ops.business.agent.telemetry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** yak_agent_step 单写入口守护：production 中仅允许白名单文件引用 StepMapper（写入权唯一）。 */
class StepSingleWriterGuardTest {

  private static final Set<String> ALLOWED = Set.of(
      "dao/mapper/AgentStepMapper.java",
      "telemetry/AgentStepRecorder.java",
      "telemetry/AgentStepMetricsJob.java",
      "repository/AgentStepRepositoryAdapter.java");

  @Test
  void stepMapperHasSingleWriteEntry() throws IOException {
    Path root = Path.of("src", "main", "java", "io", "yak", "ops", "business", "agent");
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(root)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
        try {
          String text = Files.readString(p);
          if (text.contains("AgentStepMapper")) {
            String rel = root.relativize(p).toString()
                .replace(java.io.File.separatorChar, '/');
            if (!ALLOWED.contains(rel)) {
              offenders.add(rel);
            }
          }
        } catch (IOException ignored) {
          // 单个文件读取失败不判定违规
        }
      });
    }
    assertTrue(offenders.isEmpty(), "yak_agent_step 出现非法读写方：" + offenders);
  }
}
