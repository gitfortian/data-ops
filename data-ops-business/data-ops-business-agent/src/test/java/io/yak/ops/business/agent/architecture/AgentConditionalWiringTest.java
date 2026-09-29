package io.yak.ops.business.agent.architecture;

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
 * 装配开关护栏：agent 模块所有 Spring Bean 必须声明 @ConditionalOnAgentEnabled，
 * 保证 yak.agent.enabled=false 时整个模块零装配、应用启动不受影响。
 * （AnalyzeWithPythonTool 额外带 python 条件，本测试只要求具备 agent 开关。）
 */
class AgentConditionalWiringTest {

  private static final Path SOURCE_ROOT =
      Path.of("src", "main", "java", "io", "yak", "ops", "business", "agent");

  private static final Pattern STEREOTYPE =
      Pattern.compile("@(Component|Service|Repository|Configuration)\\b");

  private static final String CONDITION =
      "@ConditionalOnAgentEnabled";

  @Test
  void everySpringBeanIsGatedByModuleSwitch() throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                String content = read(path);
                if (!STEREOTYPE.matcher(content).find()) {
                  return;
                }
                // 条件注解可能写在类上或元注解组合处，统一按文本包含判断
                if (!content.contains(CONDITION)) {
                  offenders.add(SOURCE_ROOT.relativize(path).toString());
                }
              });
    }
    assertTrue(
        offenders.isEmpty(),
        "以下 Spring Bean 缺少 @ConditionalOnAgentEnabled，会在模块关闭时仍被装配：" + offenders);
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取失败：" + path, e);
    }
  }
}
