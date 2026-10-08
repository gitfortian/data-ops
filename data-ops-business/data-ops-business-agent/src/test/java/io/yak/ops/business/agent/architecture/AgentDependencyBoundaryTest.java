package io.yak.ops.business.agent.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Agent 包依赖边界护栏：扫描 production 源码 import，保护 DEPENDENCIES.md 契约。
 * 修改白名单前必须先证明真实架构需求，不能为了让测试通过直接扩大 corridor。
 */
class AgentDependencyBoundaryTest {

  private static final Path SOURCE_ROOT = Path.of("src", "main", "java", "io", "yak", "ops", "business", "agent");

  private static final Pattern PACKAGE_PATTERN =
      Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
  private static final String ROOT_PACKAGE = "io.yak.ops.business.agent";
  private static final String MODULE_PREFIX = ROOT_PACKAGE + ".";
  private static final Pattern IMPORT_PATTERN =
      Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);

  private static final Map<String, List<String>> ALLOWED_TARGETS =
      Map.ofEntries(
          Map.entry("controller", List.of("conversation", "config", "report", "domain")),
          // conversation -> config：轮次调度与 SSE 尾随的执行参数（poll/pool）属于该子系统自身行为配置
          Map.entry("conversation", List.of("runtime", "repository", "domain", "telemetry", "config", "memory")),
          Map.entry("runtime", List.of("toolset", "config", "domain", "telemetry", "memory", "repository")),
          // toolset -> report 为报告落库 corridor（见 DEPENDENCIES.md 第 4 节）
          Map.entry("toolset", List.of("catalog", "gateway", "report", "domain", "telemetry")),
          Map.entry("catalog", List.of("gateway", "domain")),
          // gateway -> repository 为证据留痕安全 corridor（见 DEPENDENCIES.md 第 6/7 节）；
          // gateway -> catalog 为白名单校验下沉 corridor（校验与 REJECTED 留痕同执行边界）
          Map.entry("gateway", List.of("config", "domain", "repository", "catalog")),
          Map.entry("report", List.of("repository", "domain")),
          Map.entry("repository", List.of("dao", "domain")),
          Map.entry("dao", List.of("config")),
          Map.entry("telemetry", List.of("dao")),
          // memory（记忆线 M1）：只进 dao/telemetry（写端点+观测）/config/domain；
          // 模型调用经 MemoryCompletionPort 端口反转，不直依赖 runtime
          Map.entry("memory", List.of("dao", "config", "repository")),
          Map.entry("config", List.of("domain")),
          Map.entry("domain", List.of()));

  private static final List<String> FORBIDDEN_BUCKETS =
      List.of("service", "common", "helper", "utils", "base");

  /** 根包公共类型（权限码）与模块装配开关注解属基础设施，允许所有子系统引用。 */
  private static final List<String> INFRASTRUCTURE_TYPES =
      List.of(
          "io.yak.ops.business.agent.AgentPermissionCode",
          "io.yak.ops.business.agent.config.ConditionalOnAgentEnabled");

  @Test
  void dependencyMatrixIsRespected() throws IOException {
    for (SourceFile file : productionSources()) {
      if (!file.packageName().startsWith(MODULE_PREFIX)) {
        continue;
      }
      String topLevel = topLevelPackage(file.packageName());
      if (topLevel.isEmpty()) {
        continue;
      }
      List<String> targets = ALLOWED_TARGETS.get(topLevel);
      assertTrue(targets != null, "未登记的顶层包：" + file.packageName());

      for (String imported : file.imports()) {
        if (!imported.startsWith(MODULE_PREFIX)) {
          continue;
        }
        if (INFRASTRUCTURE_TYPES.contains(imported)) {
          continue;
        }
        String targetTopLevel = topLevelOf(imported);
        if (targetTopLevel.equals(topLevel) || targetTopLevel.isEmpty()) {
          continue;
        }
        assertTrue(
            targets.contains(targetTopLevel),
            file.path + " 非法依赖 " + imported + "（" + topLevel + " -> " + targetTopLevel + "）");
      }
    }
  }

  /**
   * SDK 归属（前缀最具体者优先）：import 命中多个前缀时取最长匹配项判定。
   * 具体化豁免（比通用 {@code io.agentscope.} 更具体，属真实跨层集成面）：
   * <ul>
   *   <li>{@code io.agentscope.core.skill.}：技能在线管理三处真实依赖——runtime 边界（AgentRuntime）、
   *       管理 Facade（AgentSkillManageService）、持久化适配器（AgentSkillRepositoryAdapter 实现官方 SPI）；</li>
   *   <li>{@code io.agentscope.core.agui.}：AG-UI 官方化 v2 协议面——runtime（ChatTurnToAguiMapper）
   *       与 conversation（AgentStreamCoordinator SSE 编码）、config 装配面（AgentAguiConfiguration
   *       注册 AguiEventEncoder Bean，对齐 tracing/studio 的 config 装配先例）。</li>
   * </ul>
   */
  private static final List<Map.Entry<String, List<String>>> SDK_OWNERS =
      List.of(
          Map.entry("io.agentscope.core.skill.", List.of("runtime", "conversation", "repository")),
          Map.entry("io.agentscope.core.agui.", List.of("runtime", "conversation", "config")),
          // 官方可观测性：TracerRegistry/TelemetryTracer 与 StudioManager 在 runtime 边界（AgentRuntime）
          // 与 config 装配面（AgentObservabilityOtel/StudioConfiguration）两处真实依赖
          Map.entry("io.agentscope.core.tracing.", List.of("runtime", "config")),
          Map.entry("io.agentscope.core.studio.", List.of("runtime", "config")),
          Map.entry("io.agentscope.", List.of("runtime")),
          // reactor 豁免：工具层以 Mono/Schedulers 卸载阻塞执行，是框架工具契约的声明式机制
          Map.entry("reactor.core.", List.of("runtime", "toolset")),
          Map.entry("io.yak.ops.business.dataset.", List.of("gateway")),
          Map.entry("io.yak.ops.business.asset.api.", List.of("gateway")),
          Map.entry("io.yak.ops.business.quality.api.", List.of("gateway")),
          Map.entry("io.yak.ops.business.semantic.api.", List.of("gateway")),
          Map.entry("io.yak.ops.business.modeling.api.", List.of("gateway")),
          Map.entry("io.yak.ops.business.metric.api.", List.of("gateway")),
          Map.entry("io.yak.ops.business.metric.", List.of()),
          Map.entry("io.yak.ops.business.semantic.", List.of()),
          Map.entry("io.yak.ops.business.modeling.", List.of()),
          Map.entry("io.yak.ops.business.asset.", List.of()),
          Map.entry("io.yak.ops.business.quality.", List.of()));

  @Test
  void sdkWhitelistsAreConfinedToDeclaredSubsystems() throws IOException {
    for (SourceFile file : productionSources()) {
      String owner = topLevelPackage(file.packageName());
      for (String imported : file.imports()) {
        Map.Entry<String, List<String>> rule = mostSpecificSdkRule(imported);
        if (rule == null) {
          continue;
        }
        boolean allowed =
            rule.getValue().contains(owner) || isWhitelistedException(owner, imported);
        assertFalse(
            !allowed,
            file.path + " 违反 SDK 白名单：" + imported + " 只允许出现在 "
                + rule.getValue());
      }
    }
  }

  /** 返回 imported 命中的最长 SDK 前缀规则（最具体优先）；无命中返回 null。 */
  private static Map.Entry<String, List<String>> mostSpecificSdkRule(String imported) {
    Map.Entry<String, List<String>> best = null;
    for (Map.Entry<String, List<String>> rule : SDK_OWNERS) {
      if (imported.startsWith(rule.getKey())
          && (best == null || rule.getKey().length() > best.getKey().length())) {
        best = rule;
      }
    }
    return best;
  }

  /**
   * 白名单精确豁免（均为框架在 toolset 边界的声明式机制）：
   * 工具注解 @Tool/@ToolParam 与会话上下文注入参数 RuntimeContext。
   */
  private static boolean isWhitelistedException(String owner, String imported) {
    return "toolset".equals(owner)
        && (imported.equals("io.agentscope.core.tool.Tool")
            || imported.equals("io.agentscope.core.tool.ToolParam")
            || imported.equals("io.agentscope.core.agent.RuntimeContext"));
  }

  @Test
  void noForbiddenBusinessBuckets() throws IOException {
    try (Stream<Path> packages = Files.walk(SOURCE_ROOT)) {
      packages
          .filter(Files::isDirectory)
          .map(path -> SOURCE_ROOT.relativize(path).toString())
          .forEach(
              relative -> {
                String firstSegment = relative.split("\\\\")[0].split("/")[0];
                assertFalse(
                    FORBIDDEN_BUCKETS.contains(firstSegment),
                    "禁止的业务大桶目录：" + relative);
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

  private static String topLevelOf(String fqcn) {
    String rest = fqcn.substring(MODULE_PREFIX.length());
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }

  private static String topLevelPackage(String packageName) {
    if (packageName.equals(ROOT_PACKAGE)) {
      return "";
    }
    String rest = packageName.substring(MODULE_PREFIX.length());
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }

  private List<SourceFile> productionSources() throws IOException {
    List<SourceFile> files = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
      paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> files.add(parse(path)));
    }
    assertTrue(!files.isEmpty(), "未扫描到源码文件，检查 SOURCE_ROOT");
    return files;
  }

  private SourceFile parse(Path path) {
    try {
      String content = Files.readString(path, StandardCharsets.UTF_8);
      Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
      assertTrue(packageMatcher.find(), "缺少 package 声明：" + path);
      String packageName = packageMatcher.group(1);

      List<String> imports = new ArrayList<>();
      Matcher importMatcher = IMPORT_PATTERN.matcher(content);
      while (importMatcher.find()) {
        imports.add(importMatcher.group(1));
      }
      return new SourceFile(path.toString(), packageName, imports);
    } catch (IOException e) {
      throw new IllegalStateException("读取失败：" + path, e);
    }
  }

  private record SourceFile(String path, String packageName, List<String> imports) {}
}
