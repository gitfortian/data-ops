package io.yak.ops.business.agent.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Agent 架构语义护栏：稳定 Facade、Core Domain 纯度、工具薄壳标记。
 * 这些规则是 ARCHITECTURE.md / CODE_STYLE.md 的可执行形式，不因迁移完成而删除。
 */
class AgentArchitectureTest {

  private static final Path SOURCE_ROOT =
      Path.of("src", "main", "java", "io", "yak", "ops", "business", "agent");

  private static final List<String> STABLE_FACADES =
      List.of(
          "conversation/AgentChatService.java",
          "conversation/query/AgentSessionQueryService.java",
          "conversation/AgentConfigManageService.java",
          "conversation/AgentSkillManageService.java",
          "report/AgentReportService.java");

  private static final Pattern DOMAIN_FORBIDDEN_IMPORT =
      Pattern.compile(
          "^import\\s+(org\\.springframework|io\\.agentscope|io\\.yak\\.ops\\.business\\.dataset"
              + "|com\\.baomidou|org\\.apache\\.ibatis|jakarta\\.annotation|reactor\\.core)",
          Pattern.MULTILINE);

  @Test
  void serviceAnnotationOnlyOnStableFacades() throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                String content = read(path);
                String relative = SOURCE_ROOT.relativize(path).toString().replace('\\', '/');
                boolean annotated =
                    content.contains("@Service") || content.contains("@org.springframework.stereotype.Service");
                if (annotated && !STABLE_FACADES.contains(relative)) {
                  offenders.add(relative);
                }
              });
    }
    assertEquals(
        List.of(),
        offenders,
        "@Service 只允许用于稳定 Application Facade：" + STABLE_FACADES);
  }

  @Test
  void coreDomainStaysFrameworkFree() throws IOException {
    Path domainRoot = SOURCE_ROOT.resolve("domain");
    assertTrue(Files.isDirectory(domainRoot), "domain 包必须存在");
    try (Stream<Path> paths = Files.walk(domainRoot)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                String content = read(path);
                var matcher = DOMAIN_FORBIDDEN_IMPORT.matcher(content);
                assertFalse(
                    matcher.find(),
                    "Core Domain 必须保持 framework-free："
                        + path
                        + " 非法 import "
                        + (matcher.hitEnd() ? "" : content.substring(matcher.start())));
              });
    }
  }

  @Test
  void toolsImplementMarkerInterface() throws IOException {
    Path toolsetRoot = SOURCE_ROOT.resolve("toolset");
    try (Stream<Path> paths = Files.walk(toolsetRoot)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> !path.getFileName().toString().equals("AgentToolBox.java"))
          .forEach(
              path -> {
                String content = read(path);
                if (!content.contains("@Tool(") && !content.contains("@Tool ")) {
                  return;
                }
                assertTrue(
                    content.contains("implements AgentToolBox")
                        || content.contains("@interface"),
                    "工具类必须实现 AgentToolBox 标记接口：" + path);
              });
    }
  }

  private static void assertTrue(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void assertFalse(boolean condition, String message) {
    if (condition) {
      throw new AssertionError(message);
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取失败：" + path, e);
    }
  }
}
