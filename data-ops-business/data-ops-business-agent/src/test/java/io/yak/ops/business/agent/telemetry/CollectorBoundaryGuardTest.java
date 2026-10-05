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

/**
 * 采集面边界守护（可观测性体系设计稿 §八）：业务工具与非 runtime 层禁止直调
 * 采集统一入口——观测是横切关注点，工具实现必须保持零感知。
 * 落库端点（Recorder）允许 runtime 与 conversation（executor 生命周期事件）触达。
 */
class CollectorBoundaryGuardTest {

  /** 允许引用采集入口/记录器的白名单（相对 root）。 */
  private static final Set<String> ALLOWED = Set.of(
      "runtime/AgentObservationCollector.java",
      "runtime/AgentRuntime.java",
      "runtime/LlmResilienceMiddleware.java",
      "runtime/ToolAuditMiddleware.java",
      "runtime/LongTermMemoryPromptMiddleware.java",
      "runtime/GovernanceContextMiddleware.java",
      "runtime/EffectiveConfigMiddleware.java",
      "conversation/AgentTurnExecutor.java",
      "telemetry/AgentObservationCollector.java",
      "telemetry/AgentStepRecorder.java");

  private static final String MARKER = "AgentObservationCollector";

  @Test
  void collectorIsOnlyTouchedByDeclaredSubsystems() throws IOException {
    Path root = Path.of("src", "main", "java", "io", "yak", "ops", "business", "agent");
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(root)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
        try {
          String text = Files.readString(p);
          if (!text.contains(MARKER)) {
            return;
          }
          String rel = root.relativize(p).toString().replace(java.io.File.separatorChar, '/');
          if (!ALLOWED.contains(rel) && text.contains("AgentObservationCollector")) {
            offenders.add(rel);
          }
        } catch (IOException ignored) {
          // 单个文件读取失败不判定违规
        }
      });
    }
    assertTrue(offenders.isEmpty(), "采集入口出现非法触达方：" + offenders);
  }

  @Test
  void businessToolsNeverImportTelemetryRecorder() throws IOException {
    Path toolset = Path.of(
        "src", "main", "java", "io", "yak", "ops", "business", "agent", "toolset");
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(toolset)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
        try {
          String text = Files.readString(p);
          if (text.contains("AgentStepRecorder") || text.contains("AgentObservationCollector")
              || text.contains("AgentStepMapper")) {
            offenders.add(toolset.relativize(p).toString());
          }
        } catch (IOException ignored) {
          // 单个文件读取失败不判定违规
        }
      });
    }
    assertTrue(offenders.isEmpty(), "业务工具必须零感知记账：" + offenders);
  }
}
