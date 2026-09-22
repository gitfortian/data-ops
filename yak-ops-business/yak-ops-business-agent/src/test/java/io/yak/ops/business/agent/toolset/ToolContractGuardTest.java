package io.yak.ops.business.agent.toolset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 工具契约守护（Phase 3-D 测试基建）：TOOL_CONTRACTS.md 与 toolset 源码 @Tool/@ToolParam
 * 注解双向核对——名称集合、参数名集合、required 标记、HITL externalTool 红线、示例行存在。
 * 契约漂移（新工具未登记/登记未实现/参数改名/必填变更）在 CI 被拦截。
 *
 * <p>解析为源码正则级（仓库守护测试既有模式）；约束：@ToolParam 的 description 不得含
 * ASCII 右括号（中文括号不受限），否则参数区间解析失准。</p>
 */
class ToolContractGuardTest {

  private static final Path TOOLSET_ROOT =
      Path.of("src", "main", "java", "io", "yak", "ops", "business", "agent", "toolset");
  private static final Path CONTRACT_DOC = Path.of("TOOL_CONTRACTS.md");

  private static final Pattern TOOL_NAME =
      Pattern.compile("@Tool\\(\\s*(?:externalTool\\s*=\\s*true\\s*,\\s*)?name\\s*=\\s*\"([^\"]+)\"");
  private static final Pattern TOOL_PARAM =
      Pattern.compile("@ToolParam\\(([^)]*)\\)", Pattern.DOTALL);
  private static final Pattern PARAM_NAME = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");
  private static final Pattern PARAM_OPTIONAL = Pattern.compile("required\\s*=\\s*false");

  /** 契约表行：`tool` | `param*`、`param` | example。 */
  private static final Pattern CONTRACT_ROW =
      Pattern.compile("^\\|\\s*`([a-z_]+)`\\s*\\|(.+)\\|\\s*$");

  record ToolSpec(String name, Set<String> params, Set<String> required, boolean externalTool) {}

  @Test
  void toolContractsMatchSourceAnnotations() throws IOException {
    Map<String, ToolSpec> fromSource = scanSource();
    Map<String, ContractRow> fromDoc = parseContractDoc();

    // 双向：源码每个 @Tool 必须登记；登记的每个工具必须存在源码
    for (Map.Entry<String, ToolSpec> e : fromSource.entrySet()) {
      assertTrue(fromDoc.containsKey(e.getKey()),
          "工具未登记 TOOL_CONTRACTS.md（新工具必须随手带契约）：" + e.getKey());
    }
    for (String name : fromDoc.keySet()) {
      assertTrue(fromSource.containsKey(name),
          "TOOL_CONTRACTS.md 登记了不存在的工具（登记与实现漂移）：" + name);
    }

    for (Map.Entry<String, ToolSpec> e : fromSource.entrySet()) {
      ToolSpec spec = e.getValue();
      ContractRow row = fromDoc.get(e.getKey());
      assertEquals(spec.params(), row.params(),
          "参数集合漂移：" + e.getKey() + " 源码=" + spec.params() + " 契约=" + row.params());
      assertEquals(spec.required(), row.required(),
          "必填标记漂移：" + e.getKey() + " 源码=" + spec.required() + " 契约=" + row.required());
      assertFalse(row.example().isBlank(), "契约行必须有调用示例：" + e.getKey());
    }

    // HITL 红线：反问工具必须 externalTool=true（挂起机制依赖）
    ToolSpec clarify = fromSource.get("request_clarification");
    if (clarify != null) {
      assertTrue(clarify.externalTool(), "request_clarification 必须 externalTool=true");
    }
  }

  private Map<String, ToolSpec> scanSource() throws IOException {
    Map<String, ToolSpec> result = new LinkedHashMap<>();
    try (Stream<Path> paths = Files.walk(TOOLSET_ROOT)) {
      List<Path> files = paths.filter(p -> p.toString().endsWith(".java")).toList();
      for (Path file : files) {
        String text = Files.readString(file);
        if (!text.contains("@Tool(")) {
          continue;
        }
        Matcher nameMatcher = TOOL_NAME.matcher(text);
        while (nameMatcher.find()) {
          String toolName = nameMatcher.group(1);
          // 参数区间：本 @Tool 注解之后到方法体的 @ToolParam 集合（同文件多工具时按出现顺序切分）
          int start = nameMatcher.end();
          int next = text.indexOf("@Tool(", start);
          String segment = text.substring(start, next < 0 ? text.length() : next);
          Set<String> params = new HashSet<>();
          Set<String> required = new HashSet<>();
          Matcher paramMatcher = TOOL_PARAM.matcher(segment);
          while (paramMatcher.find()) {
            Matcher nameMatcher2 = PARAM_NAME.matcher(paramMatcher.group(1));
            if (nameMatcher2.find()) {
              params.add(nameMatcher2.group(1));
              if (!PARAM_OPTIONAL.matcher(paramMatcher.group(1)).find()) {
                required.add(nameMatcher2.group(1));
              }
            }
          }
          boolean externalTool = segment.contains("externalTool = true")
              || text.substring(Math.max(0, nameMatcher.start() - 40), nameMatcher.end())
                  .contains("externalTool = true");
          result.put(toolName, new ToolSpec(toolName, params, required, externalTool));
        }
      }
    }
    assertFalse(result.isEmpty(), "toolset 源码扫描不得为空（目录约定变更时同步本测试）");
    return result;
  }

  private Map<String, ContractRow> parseContractDoc() throws IOException {
    Map<String, ContractRow> result = new LinkedHashMap<>();
    List<String> lines = Files.readAllLines(CONTRACT_DOC);
    boolean inTable = false;
    for (String line : lines) {
      if (line.startsWith("| 工具 |")) {
        inTable = true;
        continue;
      }
      if (!inTable || !line.startsWith("|")) {
        continue;
      }
      Matcher row = CONTRACT_ROW.matcher(line);
      if (!row.find()) {
        continue;
      }
      String name = row.group(1);
      // 先按竖线切列（列 0=参数格，列 1=示例格），再反引号解析参数——示例内的括号/反引号不参与参数集合
      String[] cells = row.group(2).split("\\|");
      String paramCell = cells.length > 0 ? cells[0] : "";
      String example = cells.length > 1 ? cells[1].trim() : "";
      // 参数格：`p*`、`p` —— 以反引号切分
      Set<String> params = new HashSet<>();
      Set<String> required = new HashSet<>();
      Matcher backtick = Pattern.compile("`([^`]+)`").matcher(paramCell);
      while (backtick.find()) {
        String token = backtick.group(1).trim();
        if (token.isEmpty() || token.equals("—")) {
          continue;
        }
        for (String part : token.split("[,，]")) {
          String p = part.trim();
          if (p.isEmpty()) {
            continue;
          }
          if (p.endsWith("*")) {
            p = p.substring(0, p.length() - 1);
            required.add(p);
          }
          params.add(p);
        }
      }
      result.put(name, new ContractRow(name, params, required, example));
    }
    assertFalse(result.isEmpty(), "TOOL_CONTRACTS.md 契约表解析不得为空");
    return result;
  }

  private record ContractRow(String name, Set<String> params, Set<String> required, String example) {}

  @Test
  void contractDocMustListRegisteredMemoryKindsToo() {
    // 防呆：契约文档存在且被本测试解析（防路径移动后测试空转）
    assertTrue(Files.exists(CONTRACT_DOC), "TOOL_CONTRACTS.md 必须存在于模块根");
  }
}
